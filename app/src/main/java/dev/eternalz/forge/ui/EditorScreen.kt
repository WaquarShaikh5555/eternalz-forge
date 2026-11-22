package dev.eternalz.forge.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.eternalz.forge.domain.validPath

private class CodeColors(private val keyword: androidx.compose.ui.graphics.Color, private val string: androidx.compose.ui.graphics.Color, private val comment: androidx.compose.ui.graphics.Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = buildAnnotatedString {
            append(text)
            Regex("\\b(fun|val|var|class|object|interface|return|if|else|when|import|package|suspend|private|public|const|let|function|export|from|def|for|while|true|false|null)\\b").findAll(text.take(60_000)).forEach { addStyle(SpanStyle(color = keyword), it.range.first, it.range.last + 1) }
            Regex("\"(?:[^\"\\\\]|\\\\.)*\"|'[^'\\n]*'").findAll(text.take(60_000)).forEach { addStyle(SpanStyle(color = string), it.range.first, it.range.last + 1) }
            Regex("//[^\\n]*|#[^\\n]*").findAll(text.take(60_000)).forEach { addStyle(SpanStyle(color = comment), it.range.first, it.range.last + 1) }
        }
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

@Composable fun EditorScreen(s: ForgeState, vm: ForgeViewModel, openLink: (String) -> Unit) {
    var path by rememberSaveable(s.repo?.fullName, s.path) { mutableStateOf(s.path) }
    var markdown by rememberSaveable(s.path) { mutableStateOf(s.path.endsWith(".md", true)) }
    var search by rememberSaveable(s.path) { mutableStateOf("") }
    var delete by remember { mutableStateOf(false) }
    var stageReview by remember { mutableStateOf(false) }
    var field by remember(s.path) { mutableStateOf(TextFieldValue(s.text)) }
    LaunchedEffect(s.text) { if(field.text != s.text) field = TextFieldValue(s.text, TextRange(s.text.length)) }
    val clipboard = LocalClipboardManager.current
    val colors = MaterialTheme.colorScheme
    val transform = remember(colors) { CodeColors(colors.primary, colors.tertiary, colors.onSurfaceVariant) }
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Code, null, Modifier.size(18.dp), tint = colors.primary)
            Text(s.branch, Modifier.weight(1f).padding(start = 8.dp), fontFamily = Mono, fontSize = 11.sp, color = colors.onSurfaceVariant)
            Pill(if(s.original != s.text || s.path != path) "Unstaged" else "Unchanged")
        }
        OutlinedTextField(path, { path = it }, label = { Text(if(s.path.isEmpty()) "New file path" else "Path · edit to rename") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), textStyle = LocalTextStyle.current.copy(fontFamily = Mono, fontSize = 12.sp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction("Stage", Icons.Outlined.Add, !s.busy && validPath(path)) { stageReview = true }
            SmallAction(if(markdown) "Edit code" else "Read Markdown", Icons.Outlined.Description) { markdown = !markdown }
            SmallAction("Copy", Icons.Outlined.ContentCopy) { clipboard.setText(AnnotatedString(s.text)) }
            SmallAction("Copy path", Icons.Outlined.Link) { clipboard.setText(AnnotatedString(path)) }
            if(s.fileSha.isNotBlank()) {
                SmallAction("History", Icons.Outlined.History, !s.busy) { vm.form(Form("history", "View file history", warning = "Read-only. Unstaged editor changes will be discarded if you continue.")) }
                SmallAction("Delete", Icons.Outlined.DeleteOutline, !s.busy) { delete = true }
                SmallAction("Download", Icons.Outlined.Download) { openLink("https://github.com/${s.repo!!.fullName}/raw/refs/heads/${dev.eternalz.forge.domain.filePath(s.branch)}/${dev.eternalz.forge.domain.filePath(s.path)}") }
            }
        }
        OutlinedTextField(search, { search = it }, placeholder = { Text("Find in file", fontSize = 12.sp) }, singleLine = true,
            trailingIcon = { if(search.isNotEmpty()) { val count = Regex(Regex.escape(search), RegexOption.IGNORE_CASE).findAll(s.text).count(); TextButton(onClick = { val index = s.text.indexOf(search, (field.selection.end).coerceAtMost(s.text.length), ignoreCase = true).let { if(it < 0) s.text.indexOf(search, ignoreCase = true) else it }; if(index >= 0) field = field.copy(selection = TextRange(index, index + search.length)) }) { Text("$count · Next") } } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
        HorizontalDivider()
        if(markdown) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) { Markdown(s.text) }
        else Row(Modifier.weight(1f).fillMaxWidth().verticalScroll(vertical).horizontalScroll(horizontal).padding(vertical = 12.dp)) {
            Text((1..(s.text.count { it == '\n' } + 1)).joinToString("\n"), fontFamily = Mono, fontSize = 12.sp, lineHeight = 21.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp, end = 16.dp))
            BasicTextField(value = field, onValueChange = { next ->
                if(next.text.toByteArray().size <= 500_000) {
                    var value = next
                    val cursor = next.selection.start
                    if(next.text.length == field.text.length + 1 && cursor > 0 && next.text[cursor - 1] == '\n') {
                        val previous = next.text.substring(0, cursor - 1).substringAfterLast('\n')
                        val indent = previous.takeWhile { it == ' ' || it == '\t' } + if(previous.trimEnd().endsWith('{')) "    " else ""
                        value = TextFieldValue(next.text.substring(0, cursor) + indent + next.text.substring(cursor), TextRange(cursor + indent.length))
                    }
                    field = value; vm.edit(value.text)
                }
            }, textStyle = TextStyle(color = colors.onSurface, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 21.sp), cursorBrush = SolidColor(colors.primary), visualTransformation = transform,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Ascii), modifier = Modifier.widthIn(min = 500.dp).padding(end = 40.dp))
        }
        Text("UTF-8  ·  ${s.text.lines().size} lines  ·  ${s.text.toByteArray().size} bytes  ·  stage to save locally", fontFamily = Mono, fontSize = 9.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(12.dp))
    }
    if(delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Stage file deletion?") }, text = { Text("${s.path} will be marked for deletion in your encrypted local draft. GitHub changes only after commit & push.") }, confirmButton = { TextButton(onClick = { delete = false; vm.stage(s.path, delete = true) }) { Text("Stage deletion") } }, dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancel") } })
    if(stageReview) AlertDialog(onDismissRequest = { stageReview = false }, title = { Text("Stage local changes?") }, text = { Text(if(path != s.path && s.path.isNotEmpty()) "Rename ${s.path} → $path and stage its edited contents. This only changes your local draft." else "$path will be saved in your encrypted local staging area. Nothing is pushed yet.") }, confirmButton = { TextButton(onClick = { stageReview = false; vm.stage(path, renameFrom = s.path.takeIf { it.isNotBlank() && it != path }) }) { Text("Stage locally") } }, dismissButton = { TextButton(onClick = { stageReview = false }) { Text("Cancel") } })
}
