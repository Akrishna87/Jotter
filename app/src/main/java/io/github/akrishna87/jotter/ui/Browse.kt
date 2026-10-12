package io.github.akrishna87.jotter.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.jotter.Backup
import io.github.akrishna87.jotter.Store
import io.github.akrishna87.jotter.model.Data
import io.github.akrishna87.jotter.model.Dates
import io.github.akrishna87.jotter.model.Markdown
import io.github.akrishna87.jotter.model.ThemeMode
import java.time.LocalDate

/** Searches note titles, text and tags. */
@Composable
fun SearchPage(data: Data) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    var query by remember { mutableStateOf(TextFieldValue("")) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val q = query.text.trim()
    val notes = if (q.isEmpty()) emptyList() else sortNotes(
        data.notes.filter { n ->
            !n.trashed && (n.title.contains(q, true) || n.body.contains(q, true) || n.tags.any { it.contains(q.removePrefix("#"), true) })
        },
        data.settings.noteSort,
    )
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        run {
            PageBar(onBack = { nav.pop() }, leading = {
                Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                PlainField(
                    value = query,
                    onChange = { query = it },
                    placeholder = "Search notes",
                    description = "Search",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(10f).padding(start = 12.dp).focusRequester(focus),
                    singleLine = true,
                )
            }) {
                if (query.text.isNotEmpty()) {
                    IconButton(onClick = { query = TextFieldValue("") }, Modifier.semantics { contentDescription = "Clear search" }) { Icon(Icons.Rounded.Close, null) }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (q.isEmpty()) {
                item { EmptyState(Icons.Rounded.Search, "Search your notes", "Find notes by title, text or #tag.") }
            } else if (notes.isEmpty()) {
                item { EmptyState(Icons.Rounded.Search, "No results", "Nothing matches “$q”.") }
            }
            if (notes.isNotEmpty()) {
                item { SectionHeader("Notes · ${notes.size}") }
                items(notes, key = { "n-" + it.id }) { n -> NoteRow(n, data, showNotebook = true, today = today) { nav.push(Page.NotePage(n.id)) } }
            }
        }
    }
}

/** Notes moved to the trash: restore them, delete them for good, or empty the trash. */
@Composable
fun TrashPage(data: Data) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    var emptying by remember { mutableStateOf(false) }
    val trashed = data.notes.filter { it.trashed }.sortedByDescending { it.trashedAt }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageBar("Trash", onBack = { nav.pop() }) {
            if (trashed.isNotEmpty()) TextButton(onClick = { emptying = true }) { Text("Empty") }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (trashed.isEmpty()) item { EmptyState(Icons.Rounded.Delete, "Trash is empty", "Notes you delete stay here until you empty the trash.") }
            items(trashed, key = { it.id }) { n ->
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(n.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                Markdown.plain(n.body, 120).replace('\n', ' '),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text("Deleted ${Dates.shortDate(n.trashedAt, today)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { Store.restoreNote(n.id) }, Modifier.semantics { contentDescription = "Restore ${n.title}" }) {
                            Icon(Icons.Rounded.RestoreFromTrash, null)
                        }
                        IconButton(onClick = { Store.deleteNoteForever(n.id) }, Modifier.semantics { contentDescription = "Delete ${n.title} forever" }) {
                            Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
    if (emptying) {
        ConfirmDialog("Empty the trash?", "${trashed.size} notes are deleted for good.", "Empty trash", onConfirm = { Store.emptyTrash() }, onDismiss = { emptying = false })
    }
}

/** Theme, backups and the version. */
@Composable
fun SettingsPage(data: Data) {
    val nav = LocalNav.current
    val actions = LocalActions.current
    val context = LocalContext.current
    val s = data.settings
    var restoring by remember { mutableStateOf<Data?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) actions.message(if (Backup.export(context, uri)) "Backup saved" else "Couldn't save the backup")
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val d = Backup.read(context, uri)
            if (d == null) actions.message("That file isn't a Jotter backup") else restoring = d
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageBar("Settings", onBack = { nav.pop() })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { SectionHeader("Theme") }
            items(ThemeMode.entries) { mode ->
                ListRow(
                    mode.label,
                    leading = { RadioButton(selected = s.theme == mode, onClick = null) },
                    onClick = { Store.updateSettings { it.copy(theme = mode) } },
                    modifier = Modifier.semantics { contentDescription = "Theme: ${mode.label}" },
                )
            }
            item { SectionHeader("Backup") }
            item {
                ListRow(
                    "Export backup",
                    detail = "Save every note and notebook to a file",
                    onClick = { exporter.launch("jotter-backup-${LocalDate.now()}.json") },
                )
            }
            item {
                ListRow(
                    "Restore from backup",
                    detail = "Replace everything with a backup file",
                    onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                )
            }
            item { SectionHeader("About") }
            item {
                val version = remember {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
                }
                ListRow("Jotter", detail = "Version $version · Your notes, on your phone only")
            }
        }
    }
    restoring?.let { backup ->
        ConfirmDialog(
            "Restore this backup?",
            "It has ${backup.notes.size} notes in ${backup.notebooks.size} notebooks. Everything in Jotter now is replaced.",
            "Restore",
            onConfirm = {
                Store.replaceAll(backup)
                actions.message("Backup restored")
            },
            onDismiss = { restoring = null },
        )
    }
}
