package dev.eternalz.forge.data

import android.util.Base64
import dev.eternalz.forge.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

class ApiFailure(val status: Int, message: String) : IOException(message)
data class Reply(val value: Any, val scopes: String, val remaining: String)
fun obj(vararg fields: Pair<String, Any?>): JSONObject = JSONObject().also { o -> fields.forEach { (k,v) -> o.put(k, v ?: JSONObject.NULL) } }
fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key)
fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

/** Only fixed GitHub hosts, system TLS, no redirects carrying Authorization, bounded responses. */
fun interface ApiTransport { suspend fun execute(method: String, path: String, body: JSONObject?): Reply }
class GitHubApi(private val token: String = "", private val transport: ApiTransport? = null) {
    suspend fun request(method: String, path: String, body: JSONObject? = null): Reply {
        require(path.startsWith('/') && !path.startsWith("//"))
        return transport?.execute(method, path, body) ?: exchange(method, "https://api.github.com$path", body, true)
    }

    suspend fun oauth(path: String, body: JSONObject): JSONObject =
        (transport?.execute("POST", "/login/$path", body) ?: exchange("POST", "https://github.com/login/$path", body, false)).value as JSONObject

    private suspend fun exchange(method: String, address: String, body: JSONObject?, authenticated: Boolean): Reply = withContext(Dispatchers.IO) {
        val url = URL(address)
        require(url.protocol == "https" && url.host in setOf("api.github.com", "github.com") && url.userInfo == null)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("Accept", if(authenticated) "application/vnd.github+json" else "application/json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "EternalzForge/0.1")
            if (authenticated && token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { readBounded(it, 4_000_000) } ?: byteArrayOf()
            coroutineContext.ensureActive()
            if (bytes.size > 4_000_000) throw ApiFailure(code, "Response exceeds the safe viewer limit. Narrow your query or use GitHub for this artifact.")
            val raw = bytes.toString(Charsets.UTF_8)
            if (code !in 200..299) {
                val message = when {
                    code == 401 -> "GitHub rejected the credential saved on this device (HTTP 401). Reconnect this account; the OAuth app may still appear authorized on GitHub."
                    code == 403 && connection.getHeaderField("X-RateLimit-Remaining") == "0" -> "GitHub rate limit reached. Resets at epoch ${connection.getHeaderField("X-RateLimit-Reset")}. Wait before retrying."
                    code == 429 -> "GitHub asked us to slow down. Retry after ${connection.getHeaderField("Retry-After") ?: "a short wait"} seconds."
                    code == 403 -> "GitHub denied this action. Check selected repositories, token permissions, organization SSO and branch rules."
                    code == 404 -> "Not found, or this credential cannot access it. Refresh and check repository permissions."
                    code == 409 || code == 422 -> "GitHub rejected the change: ${runCatching { JSONObject(raw).text("message") }.getOrDefault("conflict or invalid input")}. Refresh before retrying; nothing was force-overwritten."
                    code in 300..399 -> "GitHub redirected this resource. Refresh its repository name; credentials were not forwarded."
                    else -> "GitHub returned HTTP $code. ${runCatching { JSONObject(raw).text("message") }.getOrDefault("Try again later.")}"
                }
                throw ApiFailure(code, message)
            }
            Reply(when { raw.startsWith('[') -> JSONArray(raw); raw.startsWith('{') -> JSONObject(raw); else -> JSONObject() },
                connection.getHeaderField("X-OAuth-Scopes") ?: "Fine-grained / GitHub App permissions: review on GitHub",
                connection.getHeaderField("X-RateLimit-Remaining") ?: "")
        } catch (error: java.net.UnknownHostException) {
            throw IOException("You're offline or GitHub is unreachable. Your staged changes are safe on this device.")
        } catch (error: java.net.SocketTimeoutException) {
            throw IOException("The request timed out. For writes, refresh to check whether GitHub applied it before retrying.")
        } finally { connection.disconnect() }
    }

    suspend fun json(path: String) = request("GET", path).value as JSONObject
    suspend fun list(path: String): List<JSONObject> = (request("GET", path).value as JSONArray).objects()
    suspend fun mutate(method: String, path: String, body: JSONObject? = null): JSONObject = request(method, path, body).value as JSONObject
    fun repoPath(repo: String): String = "/repos/" + repo.split('/').joinToString("/") { segment(it) }
    suspend fun repositories(page: Int = 1): List<Repository> = list("/user/repos?sort=pushed&per_page=50&page=$page&affiliation=owner,collaborator,organization_member").map(::repository)
    suspend fun head(repo: String, branch: String) = json("${repoPath(repo)}/git/ref/heads/${filePath(branch)}").getJSONObject("object").getString("sha")
    suspend fun contents(repo: String, branch: String, path: String): Any = request("GET", "${repoPath(repo)}/contents/${filePath(path)}?ref=${segment(branch)}").value
    suspend fun file(repo: String, branch: String, path: String): Pair<String, String> {
        val value = contents(repo, branch, path) as JSONObject
        require(value.optInt("size") <= 500_000 && value.text("encoding") == "base64") { "This file is too large or cannot be edited as text (500 KB limit)." }
        val bytes = Base64.decode(value.text("content"), Base64.DEFAULT)
        require(!bytes.contains(0.toByte())) { "Binary files cannot be edited." }
        val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        return text to value.text("sha")
    }

    /** Atomic multi-file commit. Never force updates. A concurrent remote push fails the final ref update. */
    suspend fun commit(change: ChangeSet, message: String): String {
        require(message.isNotBlank() && change.files.isNotEmpty())
        require(change.files.all { validPath(it.path) && it.mode in listOf("100644", "100755") })
        require(change.files.map { it.path }.distinct().size == change.files.size)
        val base = "${repoPath(change.repository)}/git"
        if (head(change.repository, change.branch) != change.base) throw ApiFailure(409, "This branch moved remotely. Keep your draft, compare with the latest files and restage on the new head. Nothing was pushed.")
        val parent = json("$base/commits/${change.base}")
        val tree = JSONArray()
        change.files.forEach { draft ->
            val entry = obj("path" to draft.path, "mode" to draft.mode, "type" to "blob")
            if (draft.content == null) entry.put("sha", JSONObject.NULL)
            else {
                val blob = mutate("POST", "$base/blobs", obj("content" to draft.content, "encoding" to "utf-8"))
                entry.put("sha", blob.getString("sha"))
            }
            tree.put(entry)
        }
        val newTree = mutate("POST", "$base/trees", obj("base_tree" to parent.getJSONObject("tree").getString("sha"), "tree" to tree))
        val commit = mutate("POST", "$base/commits", obj("message" to message, "tree" to newTree.getString("sha"), "parents" to JSONArray().put(change.base)))
        val sha = commit.getString("sha")
        mutate("PATCH", "$base/refs/heads/${filePath(change.branch)}", obj("sha" to sha, "force" to false))
        return sha
    }

    suspend fun fileMode(repo: String, ref: String, path: String): String {
        var tree = json("${repoPath(repo)}/git/commits/$ref").getJSONObject("tree").text("sha")
        val pieces = path.split('/')
        for ((index, name) in pieces.withIndex()) {
            val node = json("${repoPath(repo)}/git/trees/$tree").getJSONArray("tree").objects().firstOrNull { it.text("path") == name }
                ?: throw ApiFailure(404, "File disappeared. Refresh before editing.")
            if(index == pieces.lastIndex) {
                require(node.text("mode") in listOf("100644", "100755")) { "Symlinks and submodules cannot be edited safely here." }
                return node.text("mode")
            }
            require(node.text("type") == "tree") { "Path traverses a non-directory." }
            tree = node.text("sha")
        }
        return "100644"
    }

    suspend fun revertHead(repo: String, branch: String, target: String) {
        val base = "${repoPath(repo)}/git"
        require(head(repo, branch) == target) { "Only the current branch-head commit can be reverted safely here. Refresh first." }
        val original = json("$base/commits/$target")
        val parents = original.getJSONArray("parents")
        require(parents.length() == 1) { "Root and merge commits require a manual revert." }
        val parent = json("$base/commits/${parents.getJSONObject(0).text("sha")}")
        val reverted = mutate("POST", "$base/commits", obj("message" to "Revert ${target.take(7)}\n\nReverts $target", "tree" to parent.getJSONObject("tree").text("sha"), "parents" to JSONArray().put(target)))
        mutate("PATCH", "$base/refs/heads/${filePath(branch)}", obj("sha" to reverted.text("sha"), "force" to false))
    }

    suspend fun jobLog(repo: String, id: String): String = withContext(Dispatchers.IO) {
        require(id.all(Char::isDigit))
        val first = (URL("https://api.github.com${repoPath(repo)}/actions/jobs/$id/logs").openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false; connectTimeout = 15000; readTimeout = 25000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("User-Agent", "EternalzForge/0.1")
        }
        val location = try {
            require(first.responseCode in listOf(301,302,303,307)) { "Job logs unavailable. They may have expired, or Actions read permission is missing." }
            first.getHeaderField("Location") ?: error("Missing log download address.")
        } finally { first.disconnect() }
        val url = URL(location)
        require(url.protocol == "https" && url.userInfo == null && (url.host.endsWith(".blob.core.windows.net") || url.host.endsWith(".actions.githubusercontent.com") || url.host.endsWith(".githubusercontent.com"))) { "Unexpected log download host; download was blocked." }
        // Signed artifact URL gets NO Authorization header; never cache its query or log it.
        val download = (url.openConnection() as HttpURLConnection).apply { instanceFollowRedirects = false; connectTimeout = 15000; readTimeout = 25000 }
        try {
            require(download.responseCode == 200) { "Log download expired. Try again." }
            val bytes = download.inputStream.use { readBounded(it, 1_000_000) }
            coroutineContext.ensureActive()
            bytes.toString(Charsets.UTF_8).replace(Regex("\\u001B\\[[;\\d]*m"), "").take(1_000_000) + if(bytes.size > 1_000_000) "\n… log truncated at 1 MB" else ""
        } finally { download.disconnect() }
    }

    companion object {
        private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while(output.size() <= limit) {
                val count = input.read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
                if(count < 0) break
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }

        fun repository(o: JSONObject) = Repository(o.text("name"), o.text("full_name"), o.text("description"), o.text("language"),
            o.optInt("stargazers_count"), o.optInt("forks_count"), o.optBoolean("private"), o.text("default_branch").ifBlank { "main" }, o.text("pushed_at"), o.optBoolean("archived"), o.optBoolean("fork"))
    }
}
