package dev.eternalz.forge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eternalz.forge.data.*
import dev.eternalz.forge.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class Screen { HOME, REPOS, SEARCH, INBOX, ACCOUNT, REPOSITORY, DETAIL, FILE, DRAFTS }
data class Field(val key: String, val label: String, val value: String = "", val multiline: Boolean = false)
data class Form(val kind: String, val title: String, val fields: List<Field> = emptyList(), val warning: String = "Applied to GitHub immediately after confirmation.", val confirmation: String = "", val target: String = "")
data class DeviceLogin(val code: String, val uri: String, val expires: Int)
data class ForgeState(
    val screen: Screen = Screen.HOME, val account: Account? = null, val accounts: List<Account> = emptyList(),
    val updateMessage: String? = null, val updateUrl: String? = null, val checkingUpdate: Boolean = false, val reconnectRequired: Boolean = false,
    val busy: Boolean = false, val authorizing: Boolean = false, val error: String? = null, val notice: String? = null,
    val repositories: List<Repository> = emptyList(), val entries: List<Entry> = emptyList(),
    val repo: Repository? = null, val branch: String = "", val path: String = "", val tab: String = "Code", val page: Int = 1,
    val query: String = "", val searchType: String = "repositories", val listFilter: String = "All",
    val detailReturn: Screen = Screen.REPOSITORY, val title: String = "", val detail: String = "", val detailKind: String = "", val detailId: String = "",
    val text: String = "", val original: String = "", val fileSha: String = "", val fileHead: String = "",
    val fileMode: String = "100644", val draft: ChangeSet? = null, val form: Form? = null, val login: DeviceLogin? = null,
    val theme: String = "Light", val recent: List<String> = emptyList(), val pins: List<String> = emptyList(), val compactHome: Boolean = false,
)

class ForgeViewModel(private val vault: SecureVault, private val sessionApi: (String) -> GitHubApi = { GitHubApi(it) }, private val authApi: (String) -> GitHubApi = { GitHubApi(it) }) : ViewModel() {
    private val mutable = MutableStateFlow(ForgeState(theme = vault.preference("theme", "Light"), compactHome = vault.preference("compact") == "true"))
    val state = mutable.asStateFlow()
    private var api = GitHubApi()
    private var job: Job? = null
    private var authJob: Job? = null
    private var generation = 0
    private var authAttempt = 0
    private fun update(block: (ForgeState) -> ForgeState) { mutable.value = block(mutable.value) }
    init { restore(); resumeLogin() }
    private fun restore() {
        val accounts = vault.accounts(); val account = accounts.firstOrNull { it.id == vault.active() }
        api = sessionApi(account?.let { vault.token(it.id) }.orEmpty())
        update { ForgeState(account = account, accounts = accounts, theme = it.theme, compactHome = it.compactHome,
            draft = account?.let { a -> vault.draft(a.id) }, recent = account?.let { a -> vault.recent(a.id) }.orEmpty(), pins = account?.let { a -> vault.pins(a.id) }.orEmpty()) }
        if (account != null) work {
            api.request("GET", "/user")
            load()
        }
    }
    private fun work(checkSession: Boolean = true, block: suspend () -> Unit) {
        job?.cancel(); val epoch = ++generation
        job = viewModelScope.launch {
            update { it.copy(busy = true, error = null, notice = null) }
            try { block(); ensureActive() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (epoch == generation) update { it.copy(
                    reconnectRequired = it.reconnectRequired || (checkSession && e is ApiFailure && e.status == 401 && it.account != null),
                    error = e.message ?: "Something went wrong. Please refresh and try again.") }
            }
            finally { if (epoch == generation) update { it.copy(busy = false) } }
        }
    }
    fun dismissUpdate() { update { it.copy(updateMessage = null, updateUrl = null) } }
    fun checkForUpdate(installedCode: Int, certificate: String) {
        if (state.value.checkingUpdate) return
        update { it.copy(checkingUpdate = true) }
        viewModelScope.launch {
            try {
                // Deliberately unauthenticated: never send the user's credential for updates.
                val release = AppUpdate.parse(GitHubApi().json("/repos/WaquarShaikh5555/eternalz-forge/releases/latest"))
                val newer = release.versionCode > installedCode
                val compatible = release.certificate.equals(certificate, ignoreCase = true)
                update { it.copy(updateMessage = when {
                    !newer -> "You are on the latest published version."
                    !compatible -> "${release.version} is available, but its signing certificate differs from this installation. Android will not allow an in-place update. Do not uninstall without saving local drafts."
                    else -> "${release.version} is available with the same signing identity. Download the APK and choose Update in Android's installer. Your accounts and drafts stay on this device. Android verifies the APK signature before updating."
                }, updateUrl = if (newer && compatible) release.url else null) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { update { it.copy(updateMessage = "Unable to find a compatible published APK. Check your connection and try again. Unverified builds and ZIP artifacts are not offered as updates.", updateUrl = null) } }
            finally { update { it.copy(checkingUpdate = false) } }
        }
    }
    fun clearError() = update { it.copy(error = null, notice = null) }
    fun form(value: Form?) { if (!state.value.busy) update { it.copy(form = value) } }
    fun theme(value: String) { vault.preference("theme", value, true); update { it.copy(theme = value) } }
    fun customize() { val next = !state.value.compactHome; vault.preference("compact", next.toString(), true); update { it.copy(compactHome = next) } }
    fun connect(token: String) {
        if (token.isBlank()) return
        cancelLogin()
        work(checkSession = false) {
            val candidate = sessionApi(token.trim()); val reply = candidate.request("GET", "/user"); val user = reply.value as JSONObject
            val account = Account(user.getLong("id").toString(), user.text("login"), user.text("name"), reply.scopes)
            vault.connect(account, token.trim()); api = candidate
            update { ForgeState(account = account, accounts = vault.accounts(), busy = true, theme = it.theme, compactHome = it.compactHome, draft = vault.draft(account.id), recent = vault.recent(account.id), pins = vault.pins(account.id)) }
            load()
        }
    }
    fun switchAccount(id: String) { if (state.value.busy) return; cancelLogin(); job?.cancel(); generation++; vault.switch(id); restore() }
    fun disconnect() {
        val id = state.value.account?.id ?: return
        cancelLogin(); job?.cancel(); generation++; vault.disconnect(id); restore()
    }
    fun deviceLogin(clientId: String, privateRepositories: Boolean = false, workflowFiles: Boolean = false, deleteRepositories: Boolean = false) {
        if (state.value.busy || state.value.authorizing) return
        if (!DeviceAuthorization.validClientId(clientId)) {
            update { it.copy(error = "This build needs the app owner's GitHub sign-in configuration.") }
            return
        }
        authJob?.cancel()
        val attempt = ++authAttempt
        update { it.copy(authorizing = true, error = null, login = null) }
        authJob = viewModelScope.launch {
            try {
                val data = authApi("").oauth("device/code", obj("client_id" to clientId, "scope" to DeviceAuthorization.scopes(privateRepositories, workflowFiles, deleteRepositories)))
                if (data.has("error")) error(DeviceAuthorization.errorMessage(data.text("error")))
                val seconds = data.optInt("expires_in", 900).coerceIn(1, 3600)
                require(data.getString("device_code").isNotBlank() && data.getString("user_code").isNotBlank())
                data.put("client_id", clientId).put("deadline", System.currentTimeMillis() + seconds * 1000L)
                vault.pendingLogin(data)
                pollLogin(data, attempt)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (attempt == authAttempt) update { it.copy(error = "Unable to finish GitHub sign-in. Check your connection and retry. Your pending approval is retained until it expires.") } }
            finally { if (attempt == authAttempt) update { it.copy(authorizing = false) } }
        }
    }
    /** Called at startup and on browser return; never creates a second polling loop. */
    fun resumeLogin() {
        if (authJob?.isActive == true || state.value.busy) return
        val pending = vault.pendingLogin() ?: return
        val attempt = ++authAttempt
        update { it.copy(authorizing = true, error = null) }
        authJob = viewModelScope.launch {
            try { pollLogin(pending, attempt) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (attempt == authAttempt) update { it.copy(error = "Sign-in could not finish. Your approval is saved; return online and tap Check authorization again.") } }
            finally { if (attempt == authAttempt) update { it.copy(authorizing = false) } }
        }
    }
    private suspend fun pollLogin(pending: JSONObject, attempt: Int) {
        val deadline = pending.getLong("deadline")
        var interval = pending.optInt("interval", 5).coerceIn(5, 60)
        val guest by lazy { authApi("") }
        while (currentCoroutineContext().isActive && attempt == authAttempt) {
            val remaining = ((deadline - System.currentTimeMillis()) / 1000L).toInt()
            val savedToken = pending.text("access_token")
            if (savedToken.isBlank() && remaining <= 0) {
                vault.pendingLogin(null)
                update { it.copy(login = null, error = "Sign-in code expired. Start again for a new code.") }
                return
            }
            update { it.copy(login = DeviceLogin(pending.getString("user_code"), "https://github.com/login/device", remaining.coerceAtLeast(0))) }
            try {
                if (savedToken.isNotBlank()) {
                    update { it.copy(notice = "GitHub approved. Connecting your account…") }
                    // Complete in the SAME coroutine. Do not fire-and-forget connect().
                    val candidate = authApi(savedToken)
                    val reply = candidate.request("GET", "/user")
                    val user = reply.value as JSONObject
                    currentCoroutineContext().ensureActive()
                    val account = Account(user.getLong("id").toString(), user.text("login"), user.text("name"), reply.scopes)
                    vault.connect(account, savedToken)
                    vault.pendingLogin(null)
                    api = candidate
                    update { ForgeState(account = account, accounts = vault.accounts(), theme = it.theme, compactHome = it.compactHome,
                        draft = vault.draft(account.id), recent = vault.recent(account.id), pins = vault.pins(account.id)) }
                    refresh()
                    return
                }
                // Persist the next allowed poll, including across process recreation/browser return.
                val wait = (pending.optLong("next_poll") - System.currentTimeMillis()).coerceAtLeast(0)
                delay(wait)
                val nextPoll = System.currentTimeMillis() + interval * 1000L
                pending.put("next_poll", nextPoll)
                vault.pendingLogin(pending)
                val reply = guest.oauth("oauth/access_token", obj("client_id" to pending.getString("client_id"),
                    "device_code" to pending.getString("device_code"), "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"))
                when (reply.text("error")) {
                    "authorization_pending" -> update { it.copy(notice = "Waiting for GitHub approval. After approving, return here.") }
                    "slow_down" -> {
                        interval = DeviceAuthorization.nextInterval(interval, reply.optInt("interval"))
                        pending.put("interval", interval).put("next_poll", System.currentTimeMillis() + interval * 1000L)
                        vault.pendingLogin(pending)
                    }
                    "" -> {
                        val token = reply.getString("access_token")
                        require(token.isNotBlank())
                        // Save immediately: the device grant may only be exchanged once.
                        pending.put("access_token", token)
                        vault.pendingLogin(pending)
                    }
                    else -> {
                        vault.pendingLogin(null)
                        update { it.copy(login = null, error = DeviceAuthorization.errorMessage(reply.text("error"))) }
                        return
                    }
                }
            } catch (e: ApiFailure) {
                if (e.status == 401 && savedToken.isNotBlank()) {
                    vault.pendingLogin(null)
                    update { it.copy(login = null, error = "GitHub rejected the approved credential. Start sign-in again.") }
                    return
                }
                if (e.status !in listOf(408, 429) && e.status < 500) throw e
                update { it.copy(notice = "GitHub is temporarily unavailable. Retrying automatically…") }
                delay(interval * 1000L)
            } catch (e: java.io.IOException) {
                update { it.copy(notice = "Connection interrupted. Keeping your approval and retrying automatically…") }
                delay(interval * 1000L)
            }
        }
    }
    fun cancelLogin() {
        ++authAttempt; authJob?.cancel(); vault.pendingLogin(null)
        update { it.copy(login = null, authorizing = false, notice = null) }
    }
    fun navigate(screen: Screen) {
        if (state.value.busy) return
        update { it.copy(screen = screen, entries = emptyList(), page = 1, error = null, form = null) }
        refresh()
    }
    fun refresh() { if (state.value.account != null) work { load() } }
    fun more() { if (!state.value.busy) { update { it.copy(page = it.page + 1) }; work { load(append = true) } } }
    private fun entries(items: List<Entry>, append: Boolean = false) = update { it.copy(entries = if (append) it.entries + items else items) }
    fun query(value: String) { update { it.copy(query = value) } }
    fun searchType(value: String) { update { it.copy(searchType = value, page = 1) } }
    fun filter(value: String) { update { it.copy(listFilter = value, page = 1) }; refresh() }
    fun search() { update { it.copy(page = 1, entries = emptyList()) }; refresh() }
    suspend fun load(append: Boolean = false) {
        val s = state.value
        when (s.screen) {
            Screen.HOME, Screen.REPOS -> {
                val values = if (s.listFilter == "Starred" && s.screen == Screen.REPOS) api.list("/user/starred?per_page=50&page=${s.page}").map(GitHubApi::repository) else api.repositories(s.page)
                update { it.copy(repositories = if (append) (it.repositories + values).distinctBy { r -> r.fullName } else values) }
                if (s.screen == Screen.HOME) entries(api.list("/users/${segment(s.account!!.login)}/events?per_page=12").map {
                    Entry(it.text("type").removeSuffix("Event"), it.optJSONObject("repo")?.text("name").orEmpty(), it.text("created_at").take(10), kind = "activity", path = it.optJSONObject("repo")?.text("name").orEmpty())
                })
            }
            Screen.SEARCH -> {
                if (s.query.isBlank()) { entries(emptyList()); return }
                val result = api.json("/search/${s.searchType}?q=${segment(s.query)}&per_page=30&page=${s.page}")
                entries(result.getJSONArray("items").objects().map { o -> when (s.searchType) {
                    "repositories" -> Entry(o.text("full_name"), o.text("description"), "★ ${o.optInt("stargazers_count")}", kind = "repository", path = o.text("full_name"))
                    "users" -> Entry(o.text("login"), o.text("type"), kind = "user", path = o.text("login"))
                    "code" -> Entry(o.text("path"), o.optJSONObject("repository")?.text("full_name").orEmpty(), kind = "searchfile", path = o.text("path"), id = o.optJSONObject("repository")?.text("full_name").orEmpty())
                    "commits" -> Entry(o.optJSONObject("commit")?.text("message").orEmpty(), o.optJSONObject("repository")?.text("full_name").orEmpty(), o.text("sha").take(7), kind = "searchcommit", id = o.text("sha"), path = o.optJSONObject("repository")?.text("full_name").orEmpty())
                    else -> Entry(o.text("title"), o.text("repository_url").substringAfter("/repos/") + " #${o.optInt("number")}", o.text("state"), o.optInt("number").toString(), o.text("repository_url").substringAfter("/repos/"), if (o.has("pull_request")) "searchpull" else "searchissue")
                } }, append)
            }
            Screen.INBOX -> entries(api.list("/notifications?all=${s.listFilter == "All activity"}&per_page=50&page=${s.page}").map {
                val subject = it.getJSONObject("subject"); val repo = it.getJSONObject("repository").text("full_name")
                Entry(subject.text("title"), "$repo · ${it.text("reason")}", if (it.optBoolean("unread")) "Unread" else "Read", it.text("id"), repo, subject.text("type"), subject.text("url"))
            }, append)
            Screen.ACCOUNT -> {
                val profile = api.json("/user")
                val groups = api.list("/users/${segment(s.account!!.login)}/orgs?per_page=100").map { Entry(it.text("login"), "Organization", kind = "user", path = it.text("login")) }
                entries(listOf(Entry(profile.text("name").ifBlank { profile.text("login") }, profile.text("bio"), "${profile.optInt("public_repos")} repos · ${profile.optInt("followers")} followers · ${profile.optInt("following")} following", kind = "user", path = profile.text("login"))) + groups)
            }
            Screen.REPOSITORY -> loadRepository(append)
            else -> Unit
        }
    }
    fun openRepository(name: String) {
        if (state.value.busy) return
        work {
            val repo = GitHubApi.repository(api.json(api.repoPath(name)))
            vault.visit(state.value.account!!.id, repo.fullName)
            update { it.copy(screen = Screen.REPOSITORY, repo = repo, branch = repo.branch, path = "", tab = "Code", entries = emptyList(), page = 1, recent = vault.recent(it.account!!.id)) }
            loadRepository()
        }
    }
    fun tab(tab: String) { if (state.value.busy) return; update { it.copy(tab = tab, page = 1, entries = emptyList(), path = "") }; refresh() }
    fun branch(name: String) { if (state.value.busy) return; update { it.copy(branch = name, tab = "Code", path = "", page = 1) }; refresh() }
    private suspend fun loadRepository(append: Boolean = false) {
        val s = state.value; val repo = s.repo ?: return; val base = api.repoPath(repo.fullName)
        val p = "per_page=50&page=${s.page}"
        val values = when (s.tab) {
            "Code" -> {
                val content = api.contents(repo.fullName, s.branch, s.path)
                if (content is JSONArray) content.objects().sortedWith(compareBy<JSONObject> { it.text("type") != "dir" }.thenBy { it.text("name") }).map {
                    Entry(it.text("name"), if (it.text("type") == "dir") "Directory" else "${it.optInt("size")} bytes", id = it.text("sha"), path = it.text("path"), kind = it.text("type"))
                } else emptyList()
            }
            "Issues" -> api.list("$base/issues?state=all&$p").filterNot { it.has("pull_request") }.map { Entry(it.text("title"), "#${it.optInt("number")} · ${it.getJSONObject("user").text("login")}", it.text("state"), it.optInt("number").toString(), kind = "issue") }
            "Pull requests" -> api.list("$base/pulls?state=all&$p").map { Entry(it.text("title"), "#${it.optInt("number")} · ${it.getJSONObject("user").text("login")}", if (!it.isNull("merged_at")) "merged" else it.text("state"), it.optInt("number").toString(), kind = "pull") }
            "Actions" -> api.json("$base/actions/runs?$p").getJSONArray("workflow_runs").objects().map { Entry(it.text("display_title"), "${it.text("head_branch")} · run #${it.optInt("run_number")}", it.text("conclusion").ifBlank { it.text("status") }, it.getLong("id").toString(), kind = "run") }
            "Commits" -> api.list("$base/commits?sha=${segment(s.branch)}&$p").map { Entry(it.getJSONObject("commit").text("message"), it.getJSONObject("commit").getJSONObject("author").text("name"), it.text("sha").take(7), it.text("sha"), kind = "commit") }
            "Branches" -> api.list("$base/branches?$p").map { Entry(it.text("name"), it.getJSONObject("commit").text("sha").take(7), if (it.optBoolean("protected")) "Protected" else "", it.text("name"), kind = "branch") }
            "Releases" -> api.list("$base/releases?$p").map { Entry(it.text("name").ifBlank { it.text("tag_name") }, it.text("tag_name"), if (it.optBoolean("draft")) "Draft" else if (it.optBoolean("prerelease")) "Pre-release" else "Release", it.getLong("id").toString(), kind = "release") }
            "Tags" -> api.list("$base/tags?$p").map { Entry(it.text("name"), it.getJSONObject("commit").text("sha").take(7), kind = "tag", id = it.text("name")) }
            else -> emptyList()
        }
        entries(values, append)
    }
    fun open(entry: Entry) {
        if (state.value.busy) return
        when {
            entry.kind == "repository" || entry.kind == "activity" -> openRepository(entry.path)
            entry.kind == "branch" -> branch(entry.title)
            entry.kind == "tag" -> work {
                val base = api.repoPath(state.value.repo!!.fullName)
                var target = api.json("$base/git/ref/tags/${filePath(entry.title)}").getJSONObject("object")
                var depth = 0
                while(target.text("type") == "tag" && depth++ < 5) target = api.json("$base/git/tags/${target.text("sha")}").getJSONObject("object")
                require(target.text("type") == "commit") { "This tag does not resolve to a commit." }
                loadDetail(Entry(entry.title, id = target.text("sha"), kind = "commit"))
            }
            entry.kind == "dir" -> { update { it.copy(path = entry.path) }; refresh() }
            entry.kind == "file" -> openFile(entry.path)
            entry.kind.startsWith("search") -> work {
                val repoName = if (entry.kind == "searchfile") entry.id else entry.path
                val repo = GitHubApi.repository(api.json(api.repoPath(repoName)))
                update { it.copy(repo = repo, branch = repo.branch) }
                if (entry.kind == "searchfile") loadFile(entry.path)
                else loadDetail(entry.copy(kind = entry.kind.removePrefix("search")))
            }
            state.value.screen == Screen.INBOX -> work {
                val repo = GitHubApi.repository(api.json(api.repoPath(entry.path)))
                update { it.copy(repo = repo, branch = repo.branch) }
                when (entry.kind) {
                    "Issue", "PullRequest", "Commit" -> loadDetail(entry.copy(kind = when(entry.kind) { "Issue" -> "issue"; "PullRequest" -> "pull"; else -> "commit" }, id = entry.url.substringAfterLast('/')))
                    else -> error("This notification type is not available in the native detail viewer. Open its repository instead.")
                }
            }
            entry.kind == "user" -> work {
                val user = api.json("/users/${segment(entry.path)}")
                update { it.copy(screen = Screen.DETAIL, title = user.text("login"), detail = listOf(user.text("name"), user.text("bio"), user.text("location"), "${user.optInt("followers")} followers · ${user.optInt("following")} following").filter(String::isNotBlank).joinToString("\n\n"), detailReturn = if(it.screen == Screen.DETAIL) it.detailReturn else it.screen, detailKind = "user", detailId = entry.path, entries = emptyList()) }
            }
            entry.kind in listOf("issue", "pull", "commit", "run", "release") -> work { loadDetail(entry) }
        }
    }
    private suspend fun loadDetail(entry: Entry) {
        val s = state.value; val base = api.repoPath(s.repo!!.fullName)
        val endpoint = when(entry.kind) { "issue" -> "issues"; "pull" -> "pulls"; "commit" -> "commits"; "run" -> "actions/runs"; else -> "releases" }
        val item = api.json("$base/$endpoint/${segment(entry.id)}")
        var text = item.text("body"); var children = emptyList<Entry>()
        when (entry.kind) {
            "issue", "pull" -> {
                text = "${item.text("state").uppercase()} · #${entry.id}\n\n$text"
                if (entry.kind == "pull") {
                    text += "\n\n${item.optJSONObject("head")?.text("label")} → ${item.optJSONObject("base")?.text("label")}\nMergeable: ${item.opt("mergeable")} · ${item.text("mergeable_state")}"
                    val sha = item.getJSONObject("head").text("sha")
                    val checks = try { api.json("$base/commits/$sha/check-runs?per_page=100").getJSONArray("check_runs").objects() }
                    catch (error: ApiFailure) { if(error.status == 403 || error.status == 404) { text += "\n\nChecks unavailable with current permissions."; emptyList() } else throw error }
                    text += "\n\n### Checks\n" + checks.joinToString("\n") { "• ${it.text("name")}: ${it.text("conclusion").ifBlank { it.text("status") }}" }
                    val files = api.list("$base/pulls/${entry.id}/files?per_page=100")
                    children = files.map { Entry(it.text("filename"), it.text("patch").ifBlank { "Binary or unavailable patch" }, it.text("status"), kind = "patch") }
                }
                val comments = api.list("$base/issues/${entry.id}/comments?per_page=100")
                text += "\n\n### Conversation\n" + comments.joinToString("\n\n---\n\n") { "**${it.getJSONObject("user").text("login")}**\n\n${it.text("body")}" }
            }
            "commit" -> {
                text = "${item.getJSONObject("commit").text("message")}\n\n${item.text("sha")}"
                children = item.getJSONArray("files").objects().map { Entry(it.text("filename"), it.text("patch").ifBlank { "Binary or unavailable patch" }, it.text("status"), kind = "patch") }
            }
            "run" -> {
                text = "${item.text("status")} · ${item.text("conclusion")}\n\n${item.text("head_branch")} · ${item.text("head_sha")}"
                children = api.json("$base/actions/runs/${entry.id}/jobs?per_page=100").getJSONArray("jobs").objects().map { job ->
                    Entry(job.text("name"), job.optJSONArray("steps")?.objects()?.joinToString("\n") { "${it.text("name")}: ${it.text("conclusion").ifBlank { it.text("status") }}" }.orEmpty(), job.text("conclusion").ifBlank { job.text("status") }, job.getLong("id").toString(), kind = "job", url = job.text("html_url"))
                }
            }
            "release" -> children = item.getJSONArray("assets").objects().map { Entry(it.text("name"), "${it.optLong("size")} bytes · download opens the authenticated browser", id = it.getLong("id").toString(), kind = "asset", url = it.text("browser_download_url")) }
        }
        update { it.copy(screen = Screen.DETAIL, title = item.text("title").ifBlank { item.text("name").ifBlank { entry.title } }, detail = text, detailReturn = if(s.screen == Screen.DETAIL) s.detailReturn else s.screen, detailId = entry.id, detailKind = entry.kind, entries = children) }
    }
    fun openFile(path: String) { work { loadFile(path) } }
    private suspend fun loadFile(path: String) {
        val s = state.value; val head = api.head(s.repo!!.fullName, s.branch)
        val mode = api.fileMode(s.repo.fullName, head, path)
        val file = api.file(s.repo.fullName, head, path)
        val staged = s.draft?.takeIf { it.repository == s.repo.fullName && it.branch == s.branch }?.files?.firstOrNull { it.path == path }
        update { it.copy(screen = Screen.FILE, path = path, original = file.first, text = staged?.content ?: file.first, fileSha = file.second, fileHead = head, fileMode = mode, title = path.substringAfterLast('/')) }
    }
    fun newFile() { work {
        val s = state.value; val head = api.head(s.repo!!.fullName, s.branch)
        update { it.copy(screen = Screen.FILE, path = "", text = "", original = "", fileSha = "", fileHead = head, fileMode = "100644", title = "New file") }
    } }
    fun edit(value: String) = update { it.copy(text = value) }
    fun stage(path: String, delete: Boolean = false, renameFrom: String? = null) {
        work {
            val s = state.value; require(validPath(path)) { "Use a relative file path without empty, . or .. segments." }
            require(s.text.toByteArray().size <= 500_000) { "Files must be smaller than 500 KB." }
            val repo = s.repo!!; val old = s.draft
            require(old == null || (old.repository == repo.fullName && old.branch == s.branch && old.base == s.fileHead)) { "Your existing draft belongs to a different repository, branch or remote head. Commit or discard it first." }
            if (s.fileSha.isBlank() || (renameFrom != null && renameFrom != path)) {
                try { api.contents(repo.fullName, s.fileHead, path); error("A file already exists at this path. Open and edit it instead.") }
                catch (e: ApiFailure) { if (e.status != 404) throw e }
            }
            val changes = (old?.files.orEmpty().filterNot { it.path == path || it.path == renameFrom } +
                listOfNotNull(renameFrom?.takeIf { it != path }?.let { Draft(it, s.original, null, s.fileSha, s.fileMode) }) +
                Draft(path, s.original, if (delete) null else s.text, s.fileSha, s.fileMode)).distinctBy { it.path }
            require(changes.size <= 30 && changes.sumOf { (it.content?.length ?: 0) + it.original.length } < 2_000_000) { "Draft limit: 30 files / 2 MB. Commit this batch before continuing." }
            val draft = ChangeSet(repo.fullName, s.branch, s.fileHead, changes)
            vault.draft(s.account!!.id, draft)
            update { it.copy(draft = draft, original = it.text, notice = "Staged locally. Nothing has been pushed to GitHub.", screen = Screen.DRAFTS) }
        }
    }
    fun draftScreen() { if (!state.value.busy) update { it.copy(screen = Screen.DRAFTS) } }
    fun discard() { vault.draft(state.value.account!!.id, null); update { it.copy(draft = null, form = null, notice = "Local draft discarded.") } }
    fun pin() { val s = state.value; val name = s.repo!!.fullName; vault.pin(s.account!!.id, name); update { it.copy(pins = vault.pins(s.account.id)) } }
    fun back() {
        if (state.value.busy) return
        val s = state.value
        when {
            s.screen == Screen.REPOSITORY && s.path.isNotBlank() -> { update { it.copy(path = it.path.substringBeforeLast('/', "")) }; refresh() }
            s.screen in listOf(Screen.FILE, Screen.DETAIL, Screen.DRAFTS) -> {
                update { it.copy(screen = if (s.screen == Screen.DETAIL) s.detailReturn else if (it.repo != null) Screen.REPOSITORY else Screen.HOME, path = if (s.screen == Screen.FILE) s.path.substringBeforeLast('/', "") else "", entries = emptyList()) }; refresh()
            }
            else -> navigate(Screen.HOME)
        }
    }
    fun apply(form: Form, values: Map<String, String>) {
        if (state.value.busy) return
        if (form.confirmation.isNotBlank() && values["__confirmation"] != form.confirmation) {
            update { it.copy(error = "Enter the exact confirmation text before continuing.") }; return
        }
        if (form.kind == "disconnect") { disconnect(); return }
        if (form.kind == "discard") { discard(); return }
        if (state.value.reconnectRequired) {
            update { it.copy(error = "Reconnect this account before changing GitHub. Your local drafts are safe.") }; return
        }
        work {
            val s = state.value; val repo = s.repo; val base = repo?.let { api.repoPath(it.fullName) }.orEmpty()
            fun v(key: String) = values[key].orEmpty().trim()
            fun required(key: String): String = v(key).also { require(it.isNotBlank()) { "$key cannot be empty." } }
            when (form.kind) {
                "createRepo" -> {
                    val name = required("name"); require(name.matches(Regex("[A-Za-z0-9_.-]{1,100}"))) { "Invalid repository name." }
                    api.mutate("POST", "/user/repos", obj("name" to name, "description" to v("description"), "private" to (v("visibility") != "public"), "auto_init" to true))
                }
                "fork" -> api.mutate("POST", "$base/forks", obj())
                "deleteRepo" -> {
                    require(repo != null && form.target == repo.fullName && form.confirmation == repo.fullName) {
                        "The selected repository changed. Reopen deletion confirmation."
                    }
                    val scopes = s.account?.scopes.orEmpty().split(',').map(String::trim).toSet()
                    require("delete_repo" in scopes || ("public_repo" !in scopes && "repo" !in scopes)) {
                        "Your GitHub sign-in does not allow deletion. Open You → Reconnect / change permissions, enable Repository deletion, and approve on GitHub. Nothing was deleted."
                    }
                    val permissions = api.json(base).optJSONObject("permissions")
                    require(permissions?.optBoolean("admin") != false) {
                        "Only a repository administrator can delete this repository. Organization policies may also prevent deletion."
                    }
                    try { api.mutate("DELETE", base) }
                    catch (e: ApiFailure) {
                        if (e.status == 403) error("GitHub denied deletion. Reconnect with Repository deletion enabled (delete_repo), or check fine-grained token Administration: write, organization SSO and repository admin rights. Nothing was deleted.")
                        throw e
                    }
                }
                "settings" -> { require(v("visibility") in listOf("private", "public") && v("archived") in listOf("true", "false")) { "Visibility must be private/public and archived must be true/false." }; api.mutate("PATCH", base, obj("name" to required("name"), "description" to v("description"), "private" to (v("visibility") != "public"), "archived" to (v("archived") == "true"))) }
                "star" -> api.mutate("PUT", "/user/starred/${repo!!.fullName}")
                "unstar" -> api.mutate("DELETE", "/user/starred/${repo!!.fullName}")
                "branch" -> {
                    val name = required("name"); require(validBranch(name)) { "Invalid Git branch name." }
                    api.mutate("POST", "$base/git/refs", obj("ref" to "refs/heads/$name", "sha" to api.head(repo!!.fullName, s.branch)))
                }
                "renameBranch" -> { val name = required("name"); require(validBranch(name)); api.mutate("POST", "$base/branches/${segment(s.branch)}/rename", obj("new_name" to name)); update { it.copy(branch = name) } }
                "deleteBranch" -> { require(s.branch != repo!!.branch) { "Switch away from the default branch before deleting a branch." }; api.mutate("DELETE", "$base/git/refs/heads/${filePath(s.branch)}"); update { it.copy(branch = repo.branch) } }
                "compare" -> {
                    val result = api.json("$base/compare/${segment(required("base"))}...${segment(required("head"))}")
                    update { it.copy(screen = Screen.DETAIL, detailKind = "compare", title = "Branch comparison", detail = "${result.text("status")} · ${result.optInt("ahead_by")} ahead · ${result.optInt("behind_by")} behind", entries = result.getJSONArray("files").objects().map { f -> Entry(f.text("filename"), f.text("patch"), f.text("status"), kind = "patch") }) }
                }
                "issue" -> api.mutate("POST", "$base/issues", obj("title" to required("title"), "body" to v("body")))
                "editIssue" -> { require(v("milestone").isBlank() || v("milestone").toIntOrNull() != null) { "Milestone must be a number, or blank to clear." }; require(api.json("$base/issues/${s.detailId}").text("updated_at") == form.target) { "This issue changed remotely. Reopen Edit to avoid replacing newer fields." }; api.mutate("PATCH", "$base/issues/${s.detailId}", obj("title" to required("title"), "body" to v("body"), "assignees" to JSONArray(v("assignees").split(',').map(String::trim).filter(String::isNotEmpty)), "labels" to JSONArray(v("labels").split(',').map(String::trim).filter(String::isNotEmpty)), "milestone" to v("milestone").toIntOrNull())) }
                "close", "reopen" -> api.mutate("PATCH", "$base/${if(s.detailKind == "pull") "pulls" else "issues"}/${s.detailId}", obj("state" to if (form.kind == "close") "closed" else "open"))
                "comment" -> api.mutate("POST", "$base/issues/${s.detailId}/comments", obj("body" to required("body")))
                "pull" -> api.mutate("POST", "$base/pulls", obj("title" to required("title"), "body" to v("body"), "head" to required("head"), "base" to required("base"), "draft" to (v("draft") == "true")))
                "review" -> { val event = required("event"); require(event in listOf("APPROVE", "REQUEST_CHANGES", "COMMENT")); api.mutate("POST", "$base/pulls/${s.detailId}/reviews", obj("event" to event, "body" to v("body"), "commit_id" to form.target)) }
                "merge" -> { require(v("method") in listOf("merge", "squash", "rebase")); val result = api.mutate("PUT", "$base/pulls/${s.detailId}/merge", obj("merge_method" to v("method"), "sha" to form.target)); require(result.optBoolean("merged")) { "GitHub did not merge this pull request. Check branch rules and required checks." } }
                "revert" -> api.revertHead(repo!!.fullName, s.branch, s.detailId)
                "cancelRun" -> api.mutate("POST", "$base/actions/runs/${s.detailId}/cancel", obj())
                "rerun" -> api.mutate("POST", "$base/actions/runs/${s.detailId}/rerun-failed-jobs", obj())
                "release" -> api.mutate("POST", "$base/releases", obj("tag_name" to required("tag"), "target_commitish" to s.branch, "name" to required("name"), "body" to v("body"), "draft" to (v("draft") != "false")))
                "editRelease" -> api.mutate("PATCH", "$base/releases/${s.detailId}", obj("name" to required("name"), "body" to v("body")))
                "tag" -> { val name = required("name"); require(validBranch(name)); api.mutate("POST", "$base/git/refs", obj("ref" to "refs/tags/$name", "sha" to api.head(repo!!.fullName, s.branch))) }
                "topics" -> api.mutate("PUT", "$base/topics", obj("names" to JSONArray(v("topics").split(',').map { it.trim().lowercase() }.filter(String::isNotEmpty))))
                "read" -> api.mutate("PATCH", "/notifications/threads/${form.target}", obj())
                "readAll" -> api.mutate("PUT", "/notifications", obj("read" to true))
                "commit" -> {
                    val draft = s.draft ?: error("No staged files")
                    val sha = api.commit(draft, required("message")); vault.draft(s.account!!.id, null)
                    update { it.copy(draft = null, notice = "Committed and pushed ${sha.take(7)}", form = null) }; return@work
                }
                "history" -> {
                    entries(api.list("$base/commits?path=${segment(s.path)}&sha=${segment(s.branch)}&per_page=100").map { Entry(it.getJSONObject("commit").text("message"), it.text("sha"), it.text("sha").take(7), it.text("sha"), kind = "commit") })
                    update { it.copy(screen = Screen.REPOSITORY, tab = "Commits", form = null) }; return@work
                }
                else -> error("Unknown operation")
            }
            update { it.copy(form = null, notice = "GitHub accepted the operation. Refresh to see asynchronous changes.") }
            if (form.kind == "deleteRepo" || form.kind == "settings") {
                update { it.copy(screen = Screen.REPOS, repo = null, entries = emptyList()) }; load()
            } else if (s.screen == Screen.DETAIL && s.detailKind in listOf("issue", "pull", "run", "release")) loadDetail(Entry(s.title, id = s.detailId, kind = s.detailKind))
            else if (form.kind != "compare") load()
        }
    }
    fun preparePullAction(kind: String) { work {
        val s = state.value; val pull = api.json("${api.repoPath(s.repo!!.fullName)}/pulls/${s.detailId}")
        val head = pull.getJSONObject("head").text("sha")
        update { it.copy(form = if (kind == "merge") Form("merge", "Merge pull request", listOf(Field("method", "Method: merge, squash or rebase", "squash")), "Immediately merges #${s.detailId}. GitHub branch protections still apply. Pinned to head ${head.take(7)}.", "MERGE", head)
            else Form("review", "Submit review", listOf(Field("event", "APPROVE, REQUEST_CHANGES or COMMENT", "COMMENT"), Field("body", "Review", multiline = true)), "Review is submitted immediately against ${head.take(7)}.", target = head)) }
    } }
    fun quickSearch(query: String, type: String = "issues") {
        update { it.copy(query = query, searchType = type, screen = Screen.SEARCH, page = 1, entries = emptyList()) }; refresh()
    }
    fun prepareEdit() { work {
        val s = state.value; val base = api.repoPath(s.repo!!.fullName)
        val item = api.json("$base/${if(s.detailKind == "release") "releases" else "issues"}/${s.detailId}")
        val form = if(s.detailKind == "release") Form("editRelease", "Edit release notes", listOf(Field("name", "Release name", item.text("name")), Field("body", "Release notes", item.text("body"), true)))
        else Form("editIssue", "Edit issue", listOf(Field("title", "Title", item.text("title")), Field("body", "Description", item.text("body"), true),
            Field("assignees", "Assignees, comma-separated", item.getJSONArray("assignees").objects().joinToString(",") { it.text("login") }),
            Field("labels", "Labels, comma-separated", item.getJSONArray("labels").objects().joinToString(",") { it.text("name") }),
            Field("milestone", "Milestone number (empty clears)", item.optJSONObject("milestone")?.optInt("number")?.toString().orEmpty())), "Replaces the issue fields shown below. Blank labels/assignees clear them. Refresh first if someone else is editing.", target = item.text("updated_at"))
        update { it.copy(form = form) }
    } }
    fun jobLogs(id: String) { work {
        val s = state.value; val log = api.jobLog(s.repo!!.fullName, id)
        update { it.copy(screen = Screen.DETAIL, title = "Job log", detail = "Downloaded securely. Display capped at 1 MB / 1500 lines.", detailKind = "log", entries = listOf(Entry("Log output", log, kind = "patch"))) }
    } }

    fun accountList(kind: String) { work {
        val s = state.value; val login = segment(s.account!!.login)
        val list = when(kind) {
            "Gists" -> api.list("/gists?per_page=100").map { Entry(it.text("description").ifBlank { "Untitled gist" }, if(it.optBoolean("public")) "Public gist" else "Secret gist", id = it.text("id"), kind = "gist") }
            "Followers" -> api.list("/users/$login/followers?per_page=100").map { Entry(it.text("login"), kind = "user", path = it.text("login")) }
            else -> api.list("/users/$login/following?per_page=100").map { Entry(it.text("login"), kind = "user", path = it.text("login")) }
        }
        update { it.copy(screen = Screen.DETAIL, title = kind, detail = "First 100 results. Read-only account view.", detailReturn = Screen.ACCOUNT, detailKind = "accountList", entries = list) }
    } }
    fun gist(id: String) { work {
        val gist = api.json("/gists/${segment(id)}")
        val files = gist.getJSONObject("files")
        update { it.copy(screen = Screen.DETAIL, title = gist.text("description").ifBlank { "Gist" }, detailReturn = Screen.ACCOUNT, detailKind = "gist", detail = "${gist.text("id")} · native read-only view", entries = files.keys().asSequence().map { name -> val file = files.getJSONObject(name); Entry(name, file.text("content") + if(file.optBoolean("truncated")) "\n… file truncated by GitHub" else "", kind = "patch") }.toList()) }
    } }
    fun contributions() { work {
        val login = state.value.account!!.login
        val query = "query(\$login:String!) { user(login:\$login) { contributionsCollection { contributionCalendar { totalContributions weeks { contributionDays { date contributionCount } } } } } }"
        val response = api.mutate("POST", "/graphql", obj("query" to query, "variables" to obj("login" to login)))
        require(!response.has("errors")) { "Contribution graph unavailable with this credential. GitHub GraphQL access is required." }
        val calendar = response.getJSONObject("data").getJSONObject("user").getJSONObject("contributionsCollection").getJSONObject("contributionCalendar")
        val days = calendar.getJSONArray("weeks").objects().flatMap { week -> week.getJSONArray("contributionDays").objects().map { it.text("date") to it.optInt("contributionCount") } }
        update { it.copy(screen = Screen.DETAIL, title = "Contributions", detailReturn = Screen.ACCOUNT, detailKind = "contributions", detail = "${calendar.optInt("totalContributions")} contributions in the last year. Counts follow GitHub visibility settings.", entries = days.map { day -> Entry(day.first, "${day.second} contributions", id = day.second.toString(), kind = "contribution") }) }
    } }

    fun prepareTopics() { work {
        val names = api.json("${api.repoPath(state.value.repo!!.fullName)}/topics").getJSONArray("names")
        update { it.copy(form = Form("topics", "Edit repository topics", listOf(Field("topics", "Topics, comma-separated", (0 until names.length()).joinToString(",") { index -> names.getString(index) })), "Replaces all repository topics. Use lowercase letters, digits and hyphens.")) }
    } }

    fun refreshDraftBase() { work {
        val s = state.value; val draft = s.draft ?: error("No draft")
        val head = api.head(draft.repository, draft.branch)
        val files = mutableListOf<Draft>(); val conflicts = mutableListOf<String>()
        for (file in draft.files) {
            val remote = try { api.file(draft.repository, head, file.path) } catch(e: ApiFailure) { if(e.status == 404) null else throw e }
            when {
                remote == null && file.sha.isBlank() -> files += file
                remote == null && file.content == null -> Unit // Deletion already applied remotely.
                remote != null && remote.first == file.content -> Unit // Write already applied, e.g. lost response.
                remote != null && remote.second == file.sha -> files += file
                else -> conflicts += file.path
            }
        }
        require(conflicts.isEmpty()) { "Manual resolution required: ${conflicts.joinToString()}. Local draft kept intact. Copy its diff, open the latest remote file and compare before discarding/restaging. No changes were pushed." }
        val next = if(files.isEmpty()) null else draft.copy(base = head, files = files)
        vault.draft(s.account!!.id, next)
        update { it.copy(draft = next, notice = if(next == null) "All staged changes are already present remotely." else "Draft base refreshed. No staged file changed remotely. Review before pushing.") }
    } }

}
