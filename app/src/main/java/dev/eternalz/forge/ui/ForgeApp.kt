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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import dev.eternalz.forge.R
import dev.eternalz.forge.BuildConfig
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
    var reconnect by remember { mutableStateOf(false) }
    var reconnectStarted by remember { mutableStateOf(false) }
    LaunchedEffect(s.authorizing, s.reconnectRequired, s.account?.id) {
        if (reconnect && s.authorizing) reconnectStarted = true
        if (reconnectStarted && !s.authorizing && !s.reconnectRequired && s.error == null) {
            reconnect = false; reconnectStarted = false
        }
    }
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
                    TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background), title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(painterResource(R.drawable.ic_forge), null, Modifier.size(30.dp), tint = Color.Unspecified)
                            Text("Eternalz Forge", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = (-0.5).sp)
                        }
                    },
                        navigationIcon = { if (s.screen !in destinations.map { it.screen }) IconButton(onClick = { back() }, enabled = !s.busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                        actions = {
                            if (s.draft != null) IconButton(onClick = { if(s.screen == Screen.FILE && s.text != s.original) leaveEditor = true else vm.draftScreen() }, enabled = !s.busy) { BadgedBox(badge = { Badge { Text(s.draft.files.size.toString()) } }) { Icon(Icons.Outlined.Difference, "Staged changes") } }
                            IconButton(onClick = vm::refresh, enabled = !s.busy) { Icon(Icons.Outlined.Refresh, "Refresh") }
                            Surface(onClick = { if(s.screen == Screen.FILE && s.text != s.original) leaveEditor = true else vm.navigate(Screen.ACCOUNT) }, modifier = Modifier.padding(end = 16.dp).size(48.dp), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Box(contentAlignment = Alignment.Center) { Text(s.account.login.take(2).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            }
                        })
                },
                bottomBar = {
                    if (!wide) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                        destinations.forEach { dest -> NavigationBarItem(selected = s.screen == dest.screen || (dest.screen == Screen.REPOS && s.screen in listOf(Screen.REPOSITORY, Screen.DETAIL, Screen.FILE, Screen.DRAFTS)),
                            onClick = { if (s.screen == Screen.FILE && s.text != s.original) leaveEditor = true else vm.navigate(dest.screen) },
                            icon = { Icon(dest.icon, dest.label, Modifier.size(22.dp)) }, label = { Text(dest.label, fontSize = 11.sp, fontWeight = FontWeight.Medium) }, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer, selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary), enabled = !s.busy) }
                    }
                }) { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (!online) Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) { Text("Offline · staged drafts are safe on this device", Modifier.fillMaxWidth().padding(12.dp), fontFamily = Mono, fontSize = 11.sp) }
                    if (s.reconnectRequired) Surface(color = MaterialTheme.colorScheme.errorContainer) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Reconnect @${s.account.login}", fontWeight = FontWeight.SemiBold)
                            Text("GitHub rejected this device's saved credential. Your drafts are preserved; an app listing on GitHub does not validate this credential.", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { if (s.screen == Screen.FILE && s.text != s.original) leaveEditor = true else reconnect = true }) { Text("Reconnect GitHub") }
                        }
                    }
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
    if (reconnect && s.account != null) ModalBottomSheet(onDismissRequest = { reconnect = false }) {
        ConnectScreen(s, vm, openLink = { external = it }, embedded = true)
    }
    s.updateMessage?.let { message ->
        AlertDialog(onDismissRequest = vm::dismissUpdate, title = { Text("App update") },
            text = { Text(message) },
            confirmButton = { s.updateUrl?.let { url -> TextButton(onClick = { vm.dismissUpdate(); external = url }) { Text("Download APK") } }
                ?: TextButton(onClick = vm::dismissUpdate) { Text("OK") } },
            dismissButton = { if (s.updateUrl != null) TextButton(onClick = vm::dismissUpdate) { Text("Later") } })
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
    Icon(painterResource(R.drawable.ic_forge), "Eternalz Forge", Modifier.size(42.dp), tint = Color.Unspecified)
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
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp),
        shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Book, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary) }
                }
                Column(Modifier.weight(1f)) {
                    Text(repo.fullName.substringBefore('/'), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(repo.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(if (repo.privateRepo) Icons.Outlined.Lock else Icons.AutoMirrored.Outlined.ArrowForward, if(repo.privateRepo) "Private repository" else null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(repo.description.isNotBlank()) Text(repo.description, fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if(repo.language.isNotBlank()) {
                    val dot = when(repo.language.lowercase()) { "kotlin" -> Color(0xFF8A67B0); "typescript" -> Color(0xFF397DB5); "javascript" -> Color(0xFFB68A19); "python" -> Color(0xFF468777); else -> MaterialTheme.colorScheme.primary }
                    Box(Modifier.size(6.dp).background(dot, RoundedCornerShape(3.dp)))
                    Text(repo.language, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(5.dp))
                }
                Icon(Icons.Outlined.StarBorder, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(repo.stars.toString(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if(repo.archived) Pill("Archived")
                Spacer(Modifier.weight(1f))
                Text(repo.updated.take(10), fontFamily = Mono, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

fun createRepoForm() = Form("createRepo", "New repository", listOf(Field("name", "Repository name"), Field("description", "Description"), Field("visibility", "private or public", "private")), "Creates a repository in your personal account with an initial README.")

@Composable fun HomeScreen(s: ForgeState, vm: ForgeViewModel) {
    WorkspaceContent(s, onRepo = vm::openRepository, onCreate = { vm.form(createRepoForm()) },
        onSearch = { vm.navigate(Screen.SEARCH) }, onAll = { vm.navigate(Screen.REPOS) },
        onIssues = { vm.quickSearch("is:issue is:open assignee:${s.account?.login}") },
        onReviews = { vm.quickSearch("is:pr is:open review-requested:${s.account?.login}") },
        onDraft = vm::draftScreen, onCustomize = vm::customize, onActivity = vm::open)
}

@Composable fun WorkspaceContent(s: ForgeState, onRepo: (String) -> Unit, onCreate: () -> Unit,
    onSearch: () -> Unit, onAll: () -> Unit, onIssues: () -> Unit, onReviews: () -> Unit,
    onDraft: () -> Unit, onCustomize: () -> Unit, onActivity: (Entry) -> Unit) {
    var showActivity by rememberSaveable { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp)) {
                Text("Workspace", style = MaterialTheme.typography.headlineMedium)
                Surface(onClick = onSearch, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Search your GitHub workspace", Modifier.weight(1f).padding(start = 10.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onIssues) { Text("My issues") }
                TextButton(onClick = onReviews) { Text("Reviews") }
            }
        }
        if(s.draft != null) item {
            Surface(onClick = onDraft, color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(horizontal = 20.dp).padding(top = 16.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Difference, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Your changes are waiting", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("${s.draft.files.size} staged files · saved locally", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if(showActivity && s.pins.isNotEmpty()) {
            item { Section("Pinned", "Manage") { onAll() } }
            items(s.pins) { name -> EntryRow(Entry(name, "Pinned workspace", kind = "repository"), { onRepo(name) }) }
        }
        item { Section("Repositories", "View all", onAll) }
        items(s.repositories.take(5), key = { it.fullName }) { repo -> EntryRow(Entry(repo.fullName, repo.description, if (repo.privateRepo) "Private" else "", kind = "repository"), { onRepo(repo.fullName) }) }
        if(s.repositories.isEmpty() && !s.busy && !s.reconnectRequired && s.error == null) item { EmptyState("Start something worth building", "For private repositories, reconnect with Include private repositories enabled. Organization SSO or OAuth-app approval may also be required.") }
        item {
            TextButton(onClick = onCreate, modifier = Modifier.padding(horizontal = 12.dp)) {
                Icon(Icons.Outlined.Add, null, Modifier.size(17.dp)); Text("New repository", Modifier.padding(start = 8.dp), fontSize = 12.sp)
            }
        }
        item { TextButton(onClick = { showActivity = !showActivity }, modifier = Modifier.padding(horizontal = 12.dp)) { Text(if (showActivity) "Hide activity & recents" else "Show activity & recents") } }
        if(showActivity) {
            if(s.recent.isNotEmpty()) { item { Section("Recently opened") }; items(s.recent.take(2)) { name -> EntryRow(Entry(name, kind = "repository"), { onRepo(name) }) } }
            item { Section("Activity") }
            items(s.entries) { entry -> EntryRow(entry) { onActivity(entry) } }
        }
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
        if (repos.isEmpty() && !s.busy && !s.reconnectRequired && s.error == null) item { EmptyState("No matching repositories", "Check filters and loaded pages. For private repositories, reconnect with Include private repositories enabled. Organization SSO policies may also restrict access.") }
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
    var wasAuthorizing by remember { mutableStateOf(false) }
    LaunchedEffect(s.authorizing, s.login, s.error) {
        if (s.authorizing) wasAuthorizing = true
        else if (wasAuthorizing && s.login == null && s.error == null) { addAccount = false; wasAuthorizing = false }
    }
    if (addAccount) { ModalBottomSheet(onDismissRequest = { addAccount = false }) { ConnectScreen(s, vm, openLink, embedded = true) }; return }
    LazyColumn {
        item { Heading(s.account!!.name.ifBlank { s.account.login }, "@${s.account.login}"); Section("Connected accounts", "Add account") { addAccount = true } }
        items(s.accounts) { account -> EntryRow(Entry(account.login, if(account.id == s.account?.id) "Active account · encrypted on this device" else "Tap to switch workspace", if(account.id == s.account?.id) "Active" else "", kind = "user"), { vm.switchAccount(account.id) }) }
        item { TextButton(onClick = { addAccount = true }) { Text("Reconnect / change permissions") } }
        item { Section("Appearance"); Chips(listOf("System", "Dark", "Light"), s.theme, vm::theme) }
        item { Section("Connected permissions"); Text(s.account!!.scopes, Modifier.padding(horizontal = 20.dp), fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("Fine-grained tokens: review exact repository selection and permissions on GitHub. A denied action does not automatically request broader access.", Modifier.padding(20.dp, 12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { SmallAction("Manage access", Icons.Outlined.Security) { openLink("https://github.com/settings/applications") }; SmallAction("Tokens", Icons.Outlined.Key) { openLink("https://github.com/settings/personal-access-tokens") } } }
        item { Section("Profile & organizations") }
        items(s.entries) { entry -> EntryRow(entry) { vm.open(entry) } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { SmallAction("Gists", Icons.Outlined.Code) { vm.accountList("Gists") }; SmallAction("Contributions", Icons.Outlined.CalendarMonth) { vm.contributions() }; SmallAction("Followers", Icons.Outlined.PeopleOutline, !s.busy) { vm.accountList("Followers") }; SmallAction("Following", Icons.Outlined.PeopleOutline, !s.busy) { vm.accountList("Following") } } }
        item { TextButton(onClick = { vm.form(Form("disconnect", "Disconnect @${s.account!!.login}?", warning = "Removes this account's credential, staged files, pins and recents from this device. Other accounts are untouched. This does NOT revoke access on GitHub; use Manage access or Tokens to revoke it there.", confirmation = s.account.login)) }, modifier = Modifier.padding(12.dp)) { Text("Disconnect this account", color = MaterialTheme.colorScheme.error) } }
        item { UpdateButton(s, vm) }
        item { Text("ETERNALZ FORGE / 0.3.4\nNative Android. No tracking. No WebViews.", Modifier.padding(20.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable fun ConnectScreen(s: ForgeState, vm: ForgeViewModel, openLink: (String) -> Unit, embedded: Boolean = false, oauthClientId: String = BuildConfig.GITHUB_OAUTH_CLIENT_ID) {
    var token by remember { mutableStateOf("") } // Intentionally never saveable / persisted before validation.
    var tokenMode by rememberSaveable { mutableStateOf(false) }
    var privateRepositories by rememberSaveable { mutableStateOf(s.account?.scopes?.split(',')?.any { it.trim() == "repo" } == true) }
    var workflowFiles by rememberSaveable { mutableStateOf(s.account?.scopes?.split(',')?.any { it.trim() == "workflow" } == true) }
    var deleteRepositories by rememberSaveable { mutableStateOf(false) }
    var showPermissions by rememberSaveable { mutableStateOf(false) }
    var setupRequired by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().then(if(embedded) Modifier.heightIn(max = 650.dp) else Modifier.fillMaxHeight().systemBarsPadding()).verticalScroll(rememberScrollState()).padding(28.dp)) {
        if (!embedded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(); Spacer(Modifier.width(12.dp))
                Text("Eternalz Forge", fontWeight = FontWeight.SemiBold, fontSize = 19.sp, letterSpacing = (-0.5).sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.theme(if(s.theme == "Dark") "Light" else "Dark") }) {
                    Icon(if(s.theme == "Dark") Icons.Outlined.LightMode else Icons.Outlined.DarkMode, "Switch light or dark theme", Modifier.size(21.dp))
                }
            }
            Spacer(Modifier.height(38.dp))
        }
        Text("Sign in to GitHub", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
        s.error?.let { MessageStrip(it, true, vm::clearError) }
        if (s.busy || s.authorizing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (s.login != null) {
            Eyebrow("1. Copy this one-time sign-in code")
            Text(s.login.code, fontFamily = Mono, fontSize = 30.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 16.dp))
            TextButton(onClick = { clipboard.setText(AnnotatedString(s.login.code)) }) {
                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp)); Text("Copy sign-in code", Modifier.padding(start = 8.dp))
            }
            Button(onClick = { openLink(s.login.uri) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Text("2. Approve on GitHub") }
            Text(s.notice ?: "Waiting for authorization… expires in ${s.login.expires / 60} minutes. Return here after approving.", fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = vm::resumeLogin, enabled = !s.authorizing) { Text("Check authorization again") }
            TextButton(onClick = vm::cancelLogin) { Text("Cancel sign-in") }
        } else if (tokenMode) {
            Text("Manual token connection", style = MaterialTheme.typography.titleLarge)
            Text("Use a fine-grained token to choose exactly which repositories Forge can access.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
            OutlinedTextField(token, { token = it }, label = { Text("Personal access token") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.connect(token); token = "" }, enabled = token.isNotBlank() && !s.busy, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(10.dp)) { Icon(Icons.Outlined.Lock, null, Modifier.size(17.dp)); Text("Connect securely", Modifier.padding(start = 8.dp)) }
            TextButton(onClick = { openLink("https://github.com/settings/personal-access-tokens/new") }) { Text("Create a fine-grained token on GitHub") }
            TextButton(onClick = { tokenMode = false; token = "" }) { Text("Back to GitHub sign-in") }
        } else {
            Text("Sign in on GitHub and approve Forge. No password or personal token to paste here.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
            Button(onClick = {
                if (oauthClientId.isBlank()) setupRequired = true
                else vm.deviceLogin(oauthClientId, privateRepositories, workflowFiles, deleteRepositories)
            }, enabled = !s.busy && !s.authorizing, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Outlined.OpenInNew, null, Modifier.size(18.dp))
                Text(if (s.authorizing) "Getting sign-in code…" else "Continue with GitHub", Modifier.padding(start = 8.dp))
            }
            Text("You'll approve a short-lived code in your browser, then return here. Forge never sees your GitHub password.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = { showPermissions = !showPermissions }, enabled = !s.authorizing) {
                Text(if (showPermissions) "Hide permissions" else "Choose permissions")
            }
            if (showPermissions) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(privateRepositories, { privateRepositories = it }, enabled = !s.authorizing)
                    Text("Include private repositories", fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(workflowFiles, { workflowFiles = it }, enabled = !s.authorizing)
                    Text("Edit Actions workflow files", fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(deleteRepositories, { deleteRepositories = it }, enabled = !s.authorizing)
                    Text("Repository deletion", fontSize = 12.sp)
                }
                Text("Deletion adds GitHub's delete_repo permission. It does not delete anything now. Every deletion still needs the full owner/repository confirmation and administrator rights.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (privateRepositories || workflowFiles || deleteRepositories) Text(
                "Selected: " + listOfNotNull(if (privateRepositories) "private repositories" else null, if (workflowFiles) "workflow editing" else null, if (deleteRepositories) "repository deletion" else null).joinToString(", "),
                fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            if (s.authorizing) TextButton(onClick = vm::cancelLogin) { Text("Cancel sign-in") }
            TextButton(onClick = { tokenMode = true }, enabled = !s.busy && !s.authorizing) { Text("Advanced: connect an existing token") }
        }
        } }
        UpdateButton(s, vm)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Outlined.VerifiedUser, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary); Text("Encrypted with Android Keystore.\nCredentials stay on your device. No analytics.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(24.dp))
    }
}
    if (setupRequired) AlertDialog(onDismissRequest = { setupRequired = false },
        title = { Text("App owner setup needed") },
        text = { Text("This development build has not been linked to Eternalz Forge's GitHub OAuth registration yet. The app owner must register it once, enable Device Flow, and configure its public client ID in the build. Regular users won't need to create tokens, register apps, or enter a client ID. No client secret belongs in the Android app.") },
        confirmButton = { TextButton(onClick = { setupRequired = false; openLink("https://github.com/settings/applications/new") }) { Text("Owner: register app") } },
        dismissButton = { TextButton(onClick = { setupRequired = false }) { Text("Close") } })
}

@Composable fun ActionSheet(form: Form, busy: Boolean, onDismiss: () -> Unit, onSubmit: (Map<String, String>) -> Unit) {
    val fields = remember(form) { mutableStateMapOf<String, String>().apply { form.fields.forEach { put(it.key, it.value) } } }
    var confirmation by remember(form) { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    ModalBottomSheet(onDismissRequest = { if(!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(form.title, style = MaterialTheme.typography.headlineMedium)
            Text(form.warning, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp))
            form.fields.forEach { field -> OutlinedTextField(fields[field.key].orEmpty(), { fields[field.key] = it }, label = { Text(field.label) }, minLines = if(field.multiline) 3 else 1, maxLines = if(field.multiline) 10 else 1, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), enabled = !busy) }
            if (form.confirmation.isNotBlank()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (form.kind == "deleteRepo") "Repository to delete" else "Confirmation text", style = MaterialTheme.typography.labelSmall)
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(form.confirmation, fontFamily = Mono, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(form.confirmation)) }) {
                            Icon(Icons.Outlined.ContentCopy, if (form.kind == "deleteRepo") "Copy repository name" else "Copy confirmation text")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if(form.confirmation.isNotBlank()) OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Type ${form.confirmation} to confirm") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), enabled = !busy)
            Button(onClick = { onSubmit(fields.toMap() + ("__confirmation" to confirmation)) }, enabled = !busy && (form.confirmation.isBlank() || confirmation == form.confirmation), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(9.dp)) { Text(if(busy) "Working…" else if (form.kind == "deleteRepo") "Delete repository permanently" else "Confirm") }
            TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel") }
        }
    }
}


@Composable private fun UpdateButton(s: ForgeState, vm: ForgeViewModel) {
    val context = LocalContext.current
    TextButton(enabled = !s.checkingUpdate, onClick = {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNATURES)
        @Suppress("DEPRECATION")
        val certificate = info.signatures?.firstOrNull()?.toByteArray()?.let { bytes ->
            java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        }.orEmpty()
        vm.checkForUpdate(BuildConfig.VERSION_CODE, certificate)
    }) { Text(if (s.checkingUpdate) "Checking for updates…" else "Check for app updates · ${BuildConfig.VERSION_NAME}") }
}
