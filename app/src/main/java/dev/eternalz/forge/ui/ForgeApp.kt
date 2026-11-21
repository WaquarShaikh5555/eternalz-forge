@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.eternalz.forge.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.eternalz.forge.domain.*

private data class Destination(val screen: Screen, val label: String, val icon: ImageVector)
private val destinations = listOf(
    Destination(Screen.HOME, "Workspace", Icons.Outlined.SpaceDashboard), Destination(Screen.REPOS, "Repos", Icons.Outlined.FolderOpen),
    Destination(Screen.SEARCH, "Search", Icons.Outlined.Search), Destination(Screen.INBOX, "Inbox", Icons.Outlined.Inbox), Destination(Screen.ACCOUNT, "You", Icons.Outlined.PersonOutline),
)

@Composable fun ForgeApp(s: ForgeState, vm: ForgeViewModel) {
    val context = LocalContext.current
    val online = rememberOnline()
    var external by remember { mutableStateOf<String?>(null) }
    var leaveEditor by remember { mutableStateOf(false) }
    fun back() { if (s.screen == Screen.FILE && s.text != s.original) leaveEditor = true else vm.back() }
    BackHandler(enabled = s.account != null && s.screen !in destinations.map { it.screen }) { back() }
    if (s.account == null) {
        ConnectScreen(s, vm, openLink = { external = it })
    } else BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        Row {
            if (wide) NavigationRail(Modifier.fillMaxHeight().width(96.dp).systemBarsPadding(), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Spacer(Modifier.height(16.dp)); BrandMark(); Spacer(Modifier.height(32.dp))
                destinations.forEach { dest -> NavigationRailItem(selected = s.screen == dest.screen, onClick = { if (s.screen == Screen.FILE && s.text != s.original) leaveEditor = true else vm.navigate(dest.screen) }, icon = { Icon(dest.icon, dest.label) }, label = { Text(dest.label) }, enabled = !s.busy) }
            }
            Scaffold(modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(title = { Column { Text("ETERNALZ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("forge", fontWeight = FontWeight.SemiBold, fontSize = 23.sp) } },
                        navigationIcon = { if (s.screen !in destinations.map { it.screen }) IconButton(onClick = { back() }, enabled = !s.busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                        actions = {
                            if (s.draft != null) IconButton(onClick = { if(s.screen == Screen.FILE && s.text != s.original) leaveEditor = true else vm.draftScreen() }, enabled = !s.busy) { BadgedBox(badge = { Badge { Text(s.draft.files.size.toString()) } }) { Icon(Icons.Outlined.Difference, "Staged changes") } }
                            IconButton(onClick = vm::refresh, enabled = !s.busy) { Icon(Icons.Outlined.Refresh, "Refresh") }
                            Surface(onClick = { if(s.screen == Screen.FILE && s.text != s.original) leaveEditor = true else vm.navigate(Screen.ACCOUNT) }, modifier = Modifier.padding(end = 16.dp).size(36.dp), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Box(contentAlignment = Alignment.Center) { Text(s.account.login.take(2).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            }
                        })
                },
                bottomBar = {
                    if (!wide) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
                        destinations.forEach { dest -> NavigationBarItem(selected = s.screen == dest.screen || (dest.screen == Screen.REPOS && s.screen in listOf(Screen.REPOSITORY, Screen.DETAIL, Screen.FILE, Screen.DRAFTS)),
                            onClick = { if (s.screen == Screen.FILE && s.text != s.original) leaveEditor = true else vm.navigate(dest.screen) },
                            icon = { Icon(dest.icon, dest.label, Modifier.size(22.dp)) }, label = { Text(dest.label, fontSize = 10.sp) }, enabled = !s.busy) }
                    }
                }) { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (!online) Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) { Text("Offline · staged drafts are safe on this device", Modifier.fillMaxWidth().padding(12.dp), fontFamily = Mono, fontSize = 11.sp) }
                    s.error?.let { MessageStrip(it, true, vm::clearError) }
                    s.notice?.let { MessageStrip(it, false, vm::clearError) }
                    when (s.screen) {
                        Screen.HOME -> HomeScreen(s, vm)
                        Screen.REPOS -> RepositoriesScreen(s, vm)
                        Screen.SEARCH -> SearchScreen(s, vm)
                        Screen.INBOX -> InboxScreen(s, vm)
                        Screen.ACCOUNT -> AccountScreen(s, vm) { external = it }
                        Screen.REPOSITORY -> RepositoryScreen(s, vm)
                        Screen.DETAIL -> DetailScreen(s, vm) { external = it }
                        Screen.FILE -> EditorScreen(s, vm) { external = it }
                        Screen.DRAFTS -> DraftScreen(s, vm)
                    }
                }
            }
        }
    }
    s.form?.let { form -> ActionSheet(form, s.busy, onDismiss = { vm.form(null) }) { vm.apply(form, it) } }
    external?.let { url ->
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        val safe = uri?.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
        AlertDialog(onDismissRequest = { external = null }, title = { Text(if (safe) "Open external link?" else "Link blocked") }, text = { Text(if (safe) "$url\n\nThis leaves Eternalz Forge. GitHub credentials are never attached to browser links. Downloads are handled by your browser." else "Only HTTPS links without embedded credentials are allowed.") },
            confirmButton = { if (safe) TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)) }; external = null }) { Text("Open browser") } }, dismissButton = { TextButton(onClick = { external = null }) { Text("Cancel") } })
    }
    if (leaveEditor) AlertDialog(onDismissRequest = { leaveEditor = false }, title = { Text("Leave unstaged edits?") }, text = { Text("These edits haven't been staged. Leaving discards this editor buffer. Stage them first to save an encrypted local draft.") }, confirmButton = { TextButton(onClick = { leaveEditor = false; vm.back() }) { Text("Discard buffer") } }, dismissButton = { TextButton(onClick = { leaveEditor = false }) { Text("Keep editing") } })
}

@Composable fun BrandMark() {
    Surface(shape = RoundedCornerShape(9.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp)) { Box(contentAlignment = Alignment.Center) { Text("F", color = MaterialTheme.colorScheme.onPrimary, fontFamily = Mono, fontSize = 27.sp, fontWeight = FontWeight.Black) } }
}
@Composable fun Eyebrow(text: String) { Text(text.uppercase(), fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable fun Heading(title: String, subtitle: String? = null) { Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) { Text(title, style = MaterialTheme.typography.headlineMedium); if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp)) } }
@Composable fun Section(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); if (action != null) TextButton(onClick = onAction) { Text(action, fontSize = 12.sp) } }
}
@Composable fun MessageStrip(text: String, error: Boolean, dismiss: () -> Unit) { Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer) { Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text(text, Modifier.weight(1f).padding(vertical = 10.dp), style = MaterialTheme.typography.bodySmall); IconButton(onClick = dismiss) { Icon(Icons.Outlined.Close, "Dismiss") } } } }
@Composable fun EmptyState(title: String = "Nothing here yet", body: String = "Refresh, adjust your filters or create something new.") {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.Terminal, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(16.dp)); Text(title, style = MaterialTheme.typography.titleMedium); Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
}
@Composable fun Pill(text: String) { Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(5.dp)) { Text(text, Modifier.padding(horizontal = 7.dp, vertical = 4.dp), fontFamily = Mono, fontSize = 10.sp, color = when(text.lowercase()) { "open", "success", "completed" -> MaterialTheme.colorScheme.primary; "failure", "cancelled" -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant }) } }
@Composable fun Chips(options: List<String>, selected: String, onClick: (String) -> Unit) { Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { options.forEach { FilterChip(selected == it, { onClick(it) }, label = { Text(it, fontSize = 12.sp) }) } } }
@Composable fun SmallAction(label: String, icon: ImageVector = Icons.Outlined.Add, enabled: Boolean = true, onClick: () -> Unit) { OutlinedButton(onClick, enabled = enabled, shape = RoundedCornerShape(9.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(7.dp)); Text(label, fontSize = 12.sp) } }
@Composable fun SearchInput(value: String, onChange: (String) -> Unit, hint: String, onSubmit: () -> Unit) { OutlinedTextField(value, onChange, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), singleLine = true, placeholder = { Text(hint, fontSize = 13.sp) }, leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(20.dp)) }, trailingIcon = { IconButton(onSubmit) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Search") } }, shape = RoundedCornerShape(10.dp)) }
@Composable fun EntryRow(entry: Entry, onClick: () -> Unit) {
    EntryRowWithAction(entry, onClick, null)
}
@Composable fun EntryRowWithAction(entry: Entry, onClick: () -> Unit, trailing: (@Composable () -> Unit)?) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(when(entry.kind) { "dir" -> Icons.Outlined.Folder; "file", "searchfile" -> Icons.Outlined.InsertDriveFile; "pull", "searchpull" -> Icons.Outlined.Merge; "issue", "searchissue" -> Icons.Outlined.Adjust; "branch" -> Icons.Outlined.AccountTree; "run", "job" -> Icons.Outlined.PlayCircle; "commit" -> Icons.Outlined.Commit; else -> Icons.Outlined.Code }, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) { Text(entry.title.lineSequence().firstOrNull().orEmpty(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis); if (entry.subtitle.isNotBlank()) Text(entry.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
            if (entry.badge.isNotBlank()) Pill(entry.badge)
            trailing?.invoke()
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}
@Composable fun RepoRow(repo: Repository, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(20.dp, 15.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (repo.privateRepo) Icons.Outlined.Lock else Icons.Outlined.Book, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(9.dp)); Text(repo.fullName.substringBefore('/'), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)); if (repo.archived) Pill("Archived") else Text(if (repo.privateRepo) "PRIVATE" else "PUBLIC", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(repo.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.padding(top = 7.dp))
        if (repo.description.isNotBlank()) Text(repo.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (repo.language.isNotBlank()) { Text("●", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary); Text(repo.language, fontSize = 11.sp) }
            Icon(Icons.Outlined.StarBorder, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text(repo.stars.toString(), fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f)); Text(repo.updated.take(10), fontFamily = Mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

fun createRepoForm() = Form("createRepo", "New repository", listOf(Field("name", "Repository name"), Field("description", "Description"), Field("visibility", "private or public", "private")), "Creates a repository in your personal account with an initial README.")

@Composable fun HomeScreen(s: ForgeState, vm: ForgeViewModel) {
    LazyColumn {
        item {
            Column(Modifier.padding(20.dp, 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))); Spacer(Modifier.width(8.dp)); Eyebrow("Your development workspace") }
                Text("Less friction.\nMore shipping.", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 15.dp))
                Text("Welcome back, ${s.account!!.name.ifBlank { s.account.login }}.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { vm.form(createRepoForm()) }, shape = RoundedCornerShape(9.dp), enabled = !s.busy) { Icon(Icons.Outlined.Add, null, Modifier.size(17.dp)); Text("New repository", Modifier.padding(start = 7.dp), fontSize = 12.sp) }
                    SmallAction("Find code", Icons.Outlined.Search, !s.busy) { vm.navigate(Screen.SEARCH) }
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        }
        if (s.draft != null) item { Surface(onClick = vm::draftScreen, color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.padding(20.dp, 16.dp)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Difference, null); Column(Modifier.weight(1f).padding(start = 12.dp)) { Text("Pick up where you left off", fontWeight = FontWeight.Medium); Text("${s.draft.files.size} staged files · ${s.draft.repository}", fontSize = 12.sp) }; Icon(Icons.AutoMirrored.Outlined.ArrowForward, null) } } }
        if (s.pins.isNotEmpty()) { item { Section("Pinned workspace") }; items(s.pins) { name -> EntryRow(Entry(name, "Pinned on this device", kind = "repository"), { vm.openRepository(name) }) } }
        if (!s.compactHome && s.recent.isNotEmpty()) { item { Section("Jump back in") }; items(s.recent.take(3)) { name -> EntryRow(Entry(name, "Recently opened", kind = "repository"), { vm.openRepository(name) }) } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction("Assigned issues", Icons.Outlined.Adjust, !s.busy) { vm.quickSearch("is:issue is:open assignee:${s.account!!.login}") }
            SmallAction("Review requests", Icons.Outlined.RateReview, !s.busy) { vm.quickSearch("is:pr is:open review-requested:${s.account!!.login}") }
            SmallAction("My pull requests", Icons.Outlined.Merge, !s.busy) { vm.quickSearch("is:pr is:open author:${s.account!!.login}") }
        } }
        item { Section("Repositories", "View all") { vm.navigate(Screen.REPOS) } }
        items(s.repositories.take(if(s.compactHome) 3 else 5), key = { it.fullName }) { repo -> RepoRow(repo) { vm.openRepository(repo.fullName) } }
        if (s.repositories.isEmpty() && !s.busy) item { EmptyState("Your workspace starts here", "Connect a credential with repository access, or create your first repository.") }
        if (!s.compactHome) {
            item { Section("Recent activity", "Customize") { vm.customize() } }
            items(s.entries) { entry -> EntryRow(entry) { vm.open(entry) } }
        } else item { TextButton(onClick = vm::customize, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Show expanded dashboard") } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable fun RepositoriesScreen(s: ForgeState, vm: ForgeViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var grid by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf("Recent") }
    val repos = s.repositories.filter { it.fullName.contains(query, true) && when(s.listFilter) { "Owned" -> it.fullName.substringBefore('/') == s.account?.login; "Private" -> it.privateRepo; "Forks" -> it.fork; "Collaborated" -> it.fullName.substringBefore('/') != s.account?.login; else -> true } }.let { list -> when(sort) { "Name" -> list.sortedBy { it.name.lowercase() }; "Stars" -> list.sortedByDescending { it.stars }; else -> list } }
    LazyColumn {
        item { Heading("Repositories", "Everything you build. One place."); SearchInput(query, { query = it }, "Filter loaded repositories", {}) }
        item { Chips(listOf("All", "Owned", "Collaborated", "Forks", "Private", "Starred"), s.listFilter, vm::filter) }
        item { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Text("${repos.size} loaded", fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)); TextButton(onClick = { sort = when(sort) { "Recent" -> "Name"; "Name" -> "Stars"; else -> "Recent" } }) { Text(sort, fontSize = 12.sp) }; IconButton(onClick = { grid = !grid }) { Icon(if(grid) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView, "Toggle list/grid") }; IconButton(onClick = { vm.form(createRepoForm()) }, enabled = !s.busy) { Icon(Icons.Outlined.Add, "Create repository") } } }
        if (grid) items(repos.chunked(2)) { pair -> Row(Modifier.padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { pair.forEach { repo -> Surface(onClick = { vm.openRepository(repo.fullName) }, modifier = Modifier.weight(1f).padding(vertical = 4.dp), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainer) { Column(Modifier.padding(12.dp).heightIn(min = 120.dp)) { Icon(Icons.Outlined.Book, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(10.dp)); Text(repo.name, fontWeight = FontWeight.Medium, maxLines = 2); Text(repo.language.ifBlank { "Repository" } + " · ★ ${repo.stars}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }; if (pair.size == 1) Spacer(Modifier.weight(1f)) } }
        else items(repos, key = { it.fullName }) { repo -> RepoRow(repo) { vm.openRepository(repo.fullName) } }
        if (repos.isEmpty() && !s.busy) item { EmptyState("No matching repositories", "Filters apply to loaded pages. Load more or search all of GitHub.") }
        item { TextButton(onClick = vm::more, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text("Load next page") } }
    }
}

@Composable fun SearchScreen(s: ForgeState, vm: ForgeViewModel) {
    LazyColumn {
        item { Heading("Find your next move.", "Search GitHub, without the detour."); SearchInput(s.query, vm::query, "Query, repo:owner/name, is:open…", vm::search) }
        item { Chips(listOf("repositories", "code", "issues", "users", "commits"), s.searchType, vm::searchType) }
        item { Text("Pull requests: use Issues with is:pr. File names: use Code with filename:. GitHub code search may require a classic OAuth token.", Modifier.padding(20.dp, 8.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(s.entries) { entry -> EntryRow(entry) { vm.open(entry) } }
        if (s.entries.isEmpty() && !s.busy) item { EmptyState(if(s.query.isBlank()) "A shortcut to everything" else "No results loaded", "Enter a query and tap the arrow. GitHub search qualifiers work here.") }
        if (s.entries.isNotEmpty()) item { TextButton(onClick = vm::more, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text("Load next page") } }
    }
}

@Composable fun InboxScreen(s: ForgeState, vm: ForgeViewModel) {
    var filter by rememberSaveable { mutableStateOf("") }
    LazyColumn {
        item { Heading("Inbox", "A little signal. A lot less noise."); Chips(listOf("Unread", "All activity"), s.listFilter, vm::filter); SearchInput(filter, { filter = it }, "Filter loaded notifications", {}) }
        item { TextButton(onClick = { vm.form(Form("readAll", "Mark all notifications read?", warning = "Applies to all GitHub notifications, not just this page.", confirmation = "READ ALL")) }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Mark all read") } }
        items(s.entries.filter { (it.title + it.path + it.kind).contains(filter, true) }) { entry -> EntryRowWithAction(entry, { vm.open(entry) }) { IconButton(onClick = { vm.form(Form("read", "Mark notification read?", target = entry.id)) }, enabled = !s.busy) { Icon(Icons.Outlined.Done, "Mark as read") } } }
        if (s.entries.isEmpty() && !s.busy) item { EmptyState("You're all caught up", "Your GitHub notifications will appear here. A token needs notification permission.") }
        item { TextButton(onClick = vm::more, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text("Load next page") } }
    }
}

@Composable fun AccountScreen(s: ForgeState, vm: ForgeViewModel, openLink: (String) -> Unit) {
    var addAccount by remember { mutableStateOf(false) }
    if (addAccount) { ModalBottomSheet(onDismissRequest = { addAccount = false }) { ConnectScreen(s.copy(account = null), vm, openLink, embedded = true) }; return }
    LazyColumn {
        item { Heading(s.account!!.name.ifBlank { s.account.login }, "@${s.account.login}"); Section("Connected accounts", "Add account") { addAccount = true } }
        items(s.accounts) { account -> EntryRow(Entry(account.login, if(account.id == s.account?.id) "Active account · encrypted on this device" else "Tap to switch workspace", if(account.id == s.account?.id) "Active" else "", kind = "user"), { vm.switchAccount(account.id) }) }
        item { Section("Appearance"); Chips(listOf("System", "Dark", "Light"), s.theme, vm::theme) }
        item { Section("Connected permissions"); Text(s.account!!.scopes, Modifier.padding(horizontal = 20.dp), fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("Fine-grained tokens: review exact repository selection and permissions on GitHub. A denied action does not automatically request broader access.", Modifier.padding(20.dp, 12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { SmallAction("Manage access", Icons.Outlined.Security) { openLink("https://github.com/settings/applications") }; SmallAction("Tokens", Icons.Outlined.Key) { openLink("https://github.com/settings/personal-access-tokens") } } }
        item { Section("Profile & organizations") }
        items(s.entries) { entry -> EntryRow(entry) { vm.open(entry) } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { SmallAction("Gists", Icons.Outlined.Code) { vm.accountList("Gists") }; SmallAction("Contributions", Icons.Outlined.CalendarMonth) { vm.contributions() }; SmallAction("Followers", Icons.Outlined.PeopleOutline, !s.busy) { vm.accountList("Followers") }; SmallAction("Following", Icons.Outlined.PeopleOutline, !s.busy) { vm.accountList("Following") } } }
        item { TextButton(onClick = { vm.form(Form("disconnect", "Disconnect @${s.account!!.login}?", warning = "Removes this account's credential, staged files, pins and recents from this device. Other accounts are untouched. This does NOT revoke access on GitHub; use Manage access or Tokens to revoke it there.", confirmation = s.account.login)) }, modifier = Modifier.padding(12.dp)) { Text("Disconnect this account", color = MaterialTheme.colorScheme.error) } }
        item { Text("ETERNALZ FORGE / 0.1.0\nNative Android. No tracking. No WebViews.", Modifier.padding(20.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable fun ConnectScreen(s: ForgeState, vm: ForgeViewModel, openLink: (String) -> Unit, embedded: Boolean = false) {
    var token by remember { mutableStateOf("") } // Intentionally never saveable / persisted before validation.
    var client by rememberSaveable { mutableStateOf("") }
    var tokenMode by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().then(if(embedded) Modifier.heightIn(max = 650.dp) else Modifier.fillMaxHeight().systemBarsPadding()).verticalScroll(rememberScrollState()).padding(28.dp)) {
        if (!embedded) { Row(verticalAlignment = Alignment.CenterVertically) { BrandMark(); Spacer(Modifier.width(12.dp)); Text("ETERNALZ / FORGE", fontFamily = Mono, fontSize = 12.sp, letterSpacing = 2.sp) }; Spacer(Modifier.height(56.dp)) }
        Eyebrow("Built for the way you ship")
        Text("Your GitHub.\nPocket-sized.\nFull-powered.", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 20.dp))
        Text("From the first issue to the final merge.\nA proper workspace, wherever you are.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp, bottom = 28.dp))
        s.error?.let { MessageStrip(it, true, vm::clearError) }
        if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (s.login != null) {
            Eyebrow("Enter this code on GitHub")
            Text(s.login.code, fontFamily = Mono, fontSize = 30.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 16.dp))
            Button(onClick = { openLink(s.login.uri) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Text("Continue on GitHub") }
            Text("Waiting for authorization… expires in ${s.login.expires / 60} minutes. Return here after approving.", fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = vm::cancelLogin) { Text("Cancel sign-in") }
        } else if (tokenMode) {
            Text("Connect a personal access token", style = MaterialTheme.typography.titleMedium)
            Text("Prefer a fine-grained token with only the repositories you select. No password is ever needed. Paste the token here, never in chat.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp))
            OutlinedTextField(token, { token = it }, label = { Text("GitHub access token") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.connect(token); token = "" }, enabled = token.isNotBlank() && !s.busy, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(10.dp)) { Text("Connect securely") }
            TextButton(onClick = { openLink("https://github.com/settings/personal-access-tokens/new") }) { Text("Create a fine-grained token on GitHub") }
            TextButton(onClick = { tokenMode = false; token = "" }) { Text("Use device sign-in instead") }
        } else {
            OutlinedTextField(client, { client = it }, label = { Text("GitHub OAuth app client ID") }, supportingText = { Text("Public identifier, not a secret. The registered OAuth app must have device flow enabled.") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = { vm.deviceLogin(client) }, enabled = client.isNotBlank() && !s.busy, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(10.dp)) { Text("Connect with GitHub") }
            Text("Device sign-in requests repo, read:user, notifications and workflow scopes for the full workspace. For narrower repository access, use a fine-grained token instead.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = { tokenMode = true }) { Text("Use a fine-grained token instead") }
        }
        HorizontalDivider(Modifier.padding(vertical = 24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Outlined.VerifiedUser, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary); Text("Encrypted with Android Keystore.\nCredentials stay on your device. No analytics.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable fun ActionSheet(form: Form, busy: Boolean, onDismiss: () -> Unit, onSubmit: (Map<String, String>) -> Unit) {
    val fields = remember(form) { mutableStateMapOf<String, String>().apply { form.fields.forEach { put(it.key, it.value) } } }
    var confirmation by remember(form) { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = { if(!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(form.title, style = MaterialTheme.typography.headlineMedium)
            Text(form.warning, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp))
            form.fields.forEach { field -> OutlinedTextField(fields[field.key].orEmpty(), { fields[field.key] = it }, label = { Text(field.label) }, minLines = if(field.multiline) 3 else 1, maxLines = if(field.multiline) 10 else 1, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), enabled = !busy) }
            if(form.confirmation.isNotBlank()) OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Type ${form.confirmation} to confirm") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), enabled = !busy)
            Button(onClick = { onSubmit(fields.toMap()) }, enabled = !busy && (form.confirmation.isBlank() || confirmation == form.confirmation), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(9.dp)) { Text(if(busy) "Working…" else "Confirm") }
            TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel") }
        }
    }
}
