package dev.eternalz.forge.domain

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun segment(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
fun filePath(value: String): String = value.split('/').joinToString("/") { segment(it) }
fun validPath(path: String): Boolean = path.isNotBlank() && path.length <= 512 &&
    !path.startsWith('/') && path.split('/').all { it.isNotBlank() && it != "." && it != ".." } &&
    path.none { it.isISOControl() || it == '\\' }
fun validBranch(name: String): Boolean = name.isNotBlank() && name.length <= 200 &&
    !name.startsWith('-') && !name.startsWith('/') && !name.endsWith('/') && !name.endsWith('.') &&
    !name.contains("..") && !name.contains("@{") && name != "@" &&
    name.split('/').all { it.isNotEmpty() && !it.startsWith('.') && !it.endsWith(".lock") } &&
    name.none { it.isWhitespace() || it.isISOControl() || it in "~^:?*[\\" }

data class Account(val id: String, val login: String, val name: String, val scopes: String)
data class Repository(
    val name: String, val fullName: String, val description: String, val language: String,
    val stars: Int, val forks: Int, val privateRepo: Boolean, val branch: String,
    val updated: String, val archived: Boolean = false, val fork: Boolean = false,
)
data class Entry(val title: String, val subtitle: String = "", val badge: String = "", val id: String = "", val path: String = "", val kind: String = "", val url: String = "")
data class Draft(val path: String, val original: String, val content: String?, val sha: String = "", val mode: String = "100644")
data class ChangeSet(val repository: String, val branch: String, val base: String, val files: List<Draft>)

/** Bounded review diff, not a patch sent to GitHub. Git trees carry the actual complete contents. */
fun reviewDiff(before: String, after: String?): String {
    if (after == null) return "--- deleted file\n" + before.lineSequence().take(500).joinToString("\n") { "- $it" }
    if (before == after) return "No changes"
    val a = before.lines(); val b = after.lines()
    var prefix = 0
    while (prefix < minOf(a.size, b.size) && a[prefix] == b[prefix]) prefix++
    var suffix = 0
    while (suffix < minOf(a.size, b.size) - prefix && a[a.lastIndex - suffix] == b[b.lastIndex - suffix]) suffix++
    return buildString {
        appendLine("@@ line ${prefix + 1} · ${a.size} → ${b.size} lines @@")
        a.subList(maxOf(0, prefix - 2), prefix).forEach { appendLine("  $it") }
        a.subList(prefix, a.size - suffix).take(500).forEach { appendLine("- $it") }
        b.subList(prefix, b.size - suffix).take(500).forEach { appendLine("+ $it") }
        b.subList(b.size - suffix, minOf(b.size, b.size - suffix + 2)).forEach { appendLine("  $it") }
        if (a.size + b.size > 1000) appendLine("… Review truncated. Full file contents are committed.")
    }
}
