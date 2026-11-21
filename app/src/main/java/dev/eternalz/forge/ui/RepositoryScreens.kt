package dev.eternalz.forge.ui

import android.widget.TextView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.eternalz.forge.domain.*
import io.noties.markwon.Markwon

@Composable fun RepositoryScreen(s: ForgeState, vm: ForgeViewModel) {
    val repo = s.repo ?: return
    var localFilter by rememberSaveable(repo.fullName, s.tab) { mutableStateOf("") }
    LazyColumn {
        item {
            Column(Modifier.padding(20.dp, 16.dp)) {
                Eyebrow(repo.fullName.substringBefore('/'))
                Text(repo.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 6.dp))
                if(repo.description.isNotBlank()) Text(repo.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Pill(if(repo.privateRepo) "Private" else "Public"); Pill("★ ${repo.stars}"); Pill("${repo.forks} forks"); if(repo.archived) Pill("Archived") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction(s.branch, Icons.Outlined.AccountTree, !s.busy) { vm.tab("Branches") }
                SmallAction(if(repo.fullName in s.pins) "Unpin" else "Pin", Icons.Outlined.PushPin, !s.busy, vm::pin)
                SmallAction("Settings", Icons.Outlined.Settings, !s.busy) { vm.tab("Settings") }
            }
            Chips(listOf("Code", "Issues", "Pull requests", "Actions", "Commits", "Branches", "Releases", "Tags"), s.tab, vm::tab)
            HorizontalDivider(Modifier.padding(top = 10.dp))
        }
        if(s.tab == "Settings") {
            item { Section("Repository controls"); Text("Every remote change requires confirmation. GitHub permissions and branch protection rules are enforced by the server.", Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
            item { EntryRow(Entry("Edit repository", "Name, description, visibility, archive status"), { vm.form(Form("settings", "Repository settings", listOf(Field("name", "Repository name", repo.name), Field("description", "Description", repo.description), Field("visibility", "private or public", if(repo.privateRepo) "private" else "public"), Field("archived", "Archived: true or false", repo.archived.toString())), "Changing visibility can expose repository content. Archiving makes the repository read-only.", repo.fullName)) }) }
            item { EntryRow(Entry("Edit topics", "Replace comma-separated repository topics"), { vm.prepareTopics() }) }
            item { EntryRow(Entry("Fork repository", "Creates a fork in your GitHub account"), { vm.form(Form("fork", "Fork ${repo.name}?", warning = "GitHub creates the fork asynchronously. Organization policies may restrict forking.")) }) }
            item { EntryRow(Entry("Star repository", "Save it to your GitHub starred list"), { vm.form(Form("star", "Star ${repo.name}?")) }) }
            item { EntryRow(Entry("Unstar repository", "Remove it from your GitHub starred list"), { vm.form(Form("unstar", "Unstar ${repo.name}?")) }) }
            item { EntryRow(Entry("Delete repository", "Permanent. Includes issues, settings and repository data."), { vm.form(Form("deleteRepo", "Delete ${repo.fullName}?", warning = "This permanently deletes the remote repository. This cannot be undone in Eternalz Forge. You need repository administration/delete permission.", confirmation = repo.fullName)) }) }
        } else {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when(s.tab) {
                        "Code" -> { SmallAction("New file", enabled = !s.busy, onClick = vm::newFile); SmallAction("README", Icons.Outlined.Description, !s.busy) { vm.openFile("README.md") }; SmallAction("Search files", Icons.Outlined.Search, !s.busy) { vm.quickSearch("repo:${repo.fullName} ", "code") } }
                        "Issues" -> SmallAction("New issue", enabled = !s.busy) { vm.form(Form("issue", "New issue", listOf(Field("title", "Title"), Field("body", "Description (Markdown)", multiline = true)))) }
                        "Pull requests" -> SmallAction("New pull request", enabled = !s.busy) { vm.form(Form("pull", "New pull request", listOf(Field("title", "Title"), Field("head", "Head branch or owner:branch", s.branch), Field("base", "Base branch", repo.branch), Field("draft", "Draft: true or false", "true"), Field("body", "Description", multiline = true)))) }
                        "Branches" -> {
                            SmallAction("New branch", enabled = !s.busy) { vm.form(Form("branch", "Create branch", listOf(Field("name", "Branch name")), "Creates a branch from the latest remote head of ${s.branch}.")) }
                            SmallAction("Rename current", Icons.Outlined.Edit, !s.busy) { vm.form(Form("renameBranch", "Rename ${s.branch}", listOf(Field("name", "New name")), confirmation = s.branch)) }
                            SmallAction("Compare", Icons.Outlined.Difference, !s.busy) { vm.form(Form("compare", "Compare branches", listOf(Field("base", "Base", repo.branch), Field("head", "Head", s.branch)), "Read-only comparison. No changes are applied.")) }
                            SmallAction("Delete current", Icons.Outlined.DeleteOutline, !s.busy) { vm.form(Form("deleteBranch", "Delete ${s.branch}?", warning = "Deletes this branch on GitHub. Unmerged commits may become unreachable. Default branches cannot be deleted here.", confirmation = s.branch)) }
                        }
                        "Releases" -> SmallAction("New release", enabled = !s.busy) { vm.form(Form("release", "Create release", listOf(Field("tag", "Tag"), Field("name", "Release name"), Field("draft", "Draft: true or false", "true"), Field("body", "Release notes (Markdown)", multiline = true)), "Creates a release and, if needed, a tag at ${s.branch}. Drafts are not public until published on GitHub.")) }
                        "Tags" -> SmallAction("Create tag", enabled = !s.busy) { vm.form(Form("tag", "Create lightweight tag", listOf(Field("name", "Tag name")), "Creates a remote tag at the latest ${s.branch} commit.")) }
                    }
                }
                if(s.tab == "Code") Text("${repo.name} / ${s.path.ifBlank { "root" }}", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SearchInput(localFilter, { localFilter = it }, if(s.tab == "Code") "Find in this directory" else "Filter loaded ${s.tab.lowercase()}", {})
            }
            if(s.path.isNotBlank() && s.tab == "Code") item { EntryRow(Entry("..", "Parent directory", kind = "dir"), vm::back) }
            items(s.entries.filter { (it.title + it.subtitle + it.badge).contains(localFilter, true) }) { entry -> EntryRow(entry) { vm.open(entry) } }
            if(s.entries.isEmpty() && !s.busy) item { EmptyState("No ${s.tab.lowercase()} loaded", "This repository may be empty, or this credential may not have access. Check any error above and refresh.") }
            if(s.tab != "Code") item { TextButton(onClick = vm::more, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text("Load next page") } }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Native Markdown rendering. No WebView, HTML plugin, images, JavaScript or automatic link navigation. */
@Composable fun Markdown(text: String, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    AndroidView(modifier = modifier.fillMaxWidth(), factory = { context -> TextView(context).apply { textSize = 14f; setTextIsSelectable(true); setLineSpacing(5f, 1f) } }, update = { view ->
        view.setTextColor(color)
        val safe = text.replace(Regex("<[^>]*>"), "").take(200_000)
        Markwon.create(view.context).setMarkdown(view, safe)
        view.movementMethod = null // Markdown links never launch an external intent silently.
    })
}

@Composable fun DetailScreen(s: ForgeState, vm: ForgeViewModel, openLink: (String) -> Unit) {
    LazyColumn {
        item { Heading(s.title, s.repo?.fullName) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if(s.detailKind in listOf("issue", "pull")) {
                    SmallAction("Comment", Icons.Outlined.ChatBubbleOutline, !s.busy) { vm.form(Form("comment", "Add comment", listOf(Field("body", "Comment (Markdown)", multiline = true)))) }
                    if(s.detailKind == "issue") SmallAction("Edit", Icons.Outlined.Edit, !s.busy) { vm.prepareEdit() }
                    SmallAction("Close", Icons.Outlined.CheckCircleOutline, !s.busy) { vm.form(Form("close", "Close #${s.detailId}?")) }
                    SmallAction("Reopen", Icons.Outlined.Refresh, !s.busy) { vm.form(Form("reopen", "Reopen #${s.detailId}?")) }
                    if(s.detailKind == "pull") {
                        SmallAction("Review", Icons.Outlined.RateReview, !s.busy) { vm.preparePullAction("review") }
                        SmallAction("Merge", Icons.Outlined.Merge, !s.busy) { vm.preparePullAction("merge") }
                    }
                }
                if(s.detailKind == "run") {
                    SmallAction("Rerun failed", Icons.Outlined.Refresh, !s.busy) { vm.form(Form("rerun", "Rerun failed jobs?", warning = "Starts GitHub-hosted or self-hosted jobs and may consume Actions minutes.", confirmation = "RERUN")) }
                    SmallAction("Cancel run", Icons.Outlined.StopCircle, !s.busy) { vm.form(Form("cancelRun", "Cancel workflow run?", confirmation = "CANCEL")) }
                }
                if(s.detailKind == "commit") SmallAction("Revert head", Icons.Outlined.Undo, !s.busy) { vm.form(Form("revert", "Revert this commit?", warning = "Only a single-parent commit at the current branch head can be reverted here. Creates and pushes a new inverse commit; history is not rewritten.", confirmation = s.detailId.take(7))) }
                if(s.detailKind == "release") SmallAction("Edit notes", Icons.Outlined.Edit, !s.busy) { vm.prepareEdit() }
                if(s.detailKind in listOf("issue", "pull", "commit", "run", "release", "user")) SmallAction("View on GitHub", Icons.Outlined.OpenInNew) {
                    val repo = s.repo?.fullName.orEmpty()
                    val path = when(s.detailKind) { "issue" -> "$repo/issues/${s.detailId}"; "pull" -> "$repo/pull/${s.detailId}"; "commit" -> "$repo/commit/${s.detailId}"; "run" -> "$repo/actions/runs/${s.detailId}"; "user" -> s.detailId; else -> "$repo/releases" }
                    openLink("https://github.com/$path")
                }
            }
        }
        item { Markdown(s.detail, Modifier.padding(20.dp)); if(s.detailKind in listOf("pull", "issue")) Text("Conversation/checks/files show up to 100 items per section. Large threads may be truncated. Links in Markdown are intentionally inert.", Modifier.padding(horizontal = 20.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if(s.detailKind == "contributions") item { ContributionCalendar(s.entries) }
        items(s.entries) { entry ->
            when(entry.kind) {
                "patch" -> { Section(entry.title); Pill(entry.badge); CodeBlock(entry.subtitle) }
                "job" -> { Section(entry.title); Text(entry.badge, Modifier.padding(horizontal = 20.dp), fontFamily = Mono, color = MaterialTheme.colorScheme.primary); CodeBlock(entry.subtitle); TextButton(onClick = { vm.jobLogs(entry.id) }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Inspect job log") } }
                "asset" -> EntryRow(entry, { openLink(entry.url) })
                "gist" -> EntryRow(entry, { vm.gist(entry.id) })
                "contribution" -> Unit
                else -> EntryRow(entry) { vm.open(entry) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable fun DraftScreen(s: ForgeState, vm: ForgeViewModel) {
    val draft = s.draft
    val clipboard = LocalClipboardManager.current
    LazyColumn {
        item { Heading("Staging area", "Local first. Deliberate by design.") }
        if(draft == null) item { EmptyState("Nothing staged", "Open a file, make changes and stage them. Your repository isn't modified until you confirm a commit and push.") }
        else {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Eyebrow(draft.repository); Text(draft.branch, fontFamily = Mono, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)); Text("Base ${draft.base.take(7)} · ${draft.files.size} files\nEncrypted on this device. Not pushed.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    Button(onClick = { vm.form(Form("commit", "Commit & push", listOf(Field("message", "Commit message")), "Atomically commits ${draft.files.size} files to ${draft.repository} → ${draft.branch}. The branch must still match ${draft.base.take(7)}. There is no force push. This immediately updates GitHub.", "PUSH")) }, enabled = !s.busy, shape = androidx.compose.foundation.shape.RoundedCornerShape(9.dp), modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) { Text("Reviewed · Commit & push") }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallAction("Check latest base", Icons.Outlined.Refresh, !s.busy, vm::refreshDraftBase)
                        SmallAction("Copy full draft", Icons.Outlined.ContentCopy) { clipboard.setText(AnnotatedString(draft.files.joinToString("\n\n") { "### ${it.path}${if(it.content == null) " (deleted)" else ""}\n${it.content ?: ""}" })) }
                    }
                    TextButton(onClick = { vm.form(Form("discard", "Discard all staged changes?", warning = "Permanently removes this local draft. GitHub is not changed.", confirmation = "DISCARD")) }, enabled = !s.busy) { Text("Discard local draft", color = MaterialTheme.colorScheme.error) }
                }
            }
            items(draft.files) { file -> Section(file.path); CodeBlock(reviewDiff(file.original, file.content)) }
        }
    }
}

@Composable fun CodeBlock(text: String) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxWidth().padding(12.dp).background(MaterialTheme.colorScheme.surfaceContainer).horizontalScroll(scroll).padding(12.dp)) {
        SelectionContainer { Column { text.lineSequence().take(1500).forEach { line -> Text(line.ifEmpty { " " }, fontFamily = Mono, fontSize = 11.sp, lineHeight = 18.sp, color = when { line.startsWith('+') -> MaterialTheme.colorScheme.primary; line.startsWith('-') -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant }) } } }
    }
}

@Composable private fun ContributionCalendar(entries: List<Entry>) {
    Column(Modifier.padding(20.dp)) {
        Eyebrow("Past year / scroll to explore")
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            entries.chunked(7).forEach { week -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    val count = day.id.toIntOrNull() ?: 0
                    Box(Modifier.size(14.dp).background(if(count == 0) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primary.copy(alpha = when { count < 3 -> 0.35f; count < 8 -> 0.6f; else -> 1f }), androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).semantics { contentDescription = "${day.title}: $count contributions" })
                }
            } }
        }
        Text("Less  ░ ▒ ▓ █  More", fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
