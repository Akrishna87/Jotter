package io.github.akrishna87.jotter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.jotter.Store
import io.github.akrishna87.jotter.model.Data
import io.github.akrishna87.jotter.model.Dates
import io.github.akrishna87.jotter.model.Markdown
import io.github.akrishna87.jotter.model.Note
import io.github.akrishna87.jotter.model.NoteSort
import io.github.akrishna87.jotter.model.Notebook

/** Notes in a list, filtered by notebook and tag, sorted the way you choose. Pinned notes come first. The menu has the trash and settings. */
@Composable
fun NotesScreen(data: Data, padding: PaddingValues) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    var notebookId by rememberSaveable { mutableStateOf<String?>(null) }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    var notebookMenu by remember { mutableStateOf(false) }
    var newNotebook by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Notebook?>(null) }
    var deleting by remember { mutableStateOf<Notebook?>(null) }
    if (notebookId != null && data.notebooks.none { it.id == notebookId }) notebookId = null

    val live = data.notes.filter { !it.trashed }
    val shown = sortNotes(
        live.filter { (notebookId == null || it.notebookId == notebookId) && (tag == null || it.tags.any { t -> t.equals(tag, true) }) },
        data.settings.noteSort,
    )
    val selectedNotebook = data.notebooks.firstOrNull { it.id == notebookId }

    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        ScreenHeader(
            selectedNotebook?.name ?: "Notes",
            if (shown.size == 1) "1 note" else "${shown.size} notes",
        ) {
            SearchAction { nav.push(Page.Search) }
            Box {
                IconButton(onClick = { sortMenu = true }, Modifier.semantics { contentDescription = "Notes options" }) { Icon(Icons.Rounded.MoreVert, null) }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    for (s in NoteSort.entries) {
                        DropdownMenuItem(
                            text = { Text("Sort by ${s.label.lowercase()}") },
                            onClick = { sortMenu = false; Store.updateSettings { it.copy(noteSort = s) } },
                            trailingIcon = { if (data.settings.noteSort == s) Icon(Icons.Rounded.Check, null) },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Trash") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        trailingIcon = { Count(data.notes.count { it.trashed }) },
                        onClick = { sortMenu = false; nav.push(Page.Trash) },
                    )
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                        onClick = { sortMenu = false; nav.push(Page.SettingsPage) },
                    )
                    if (selectedNotebook != null) {
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Rename notebook") }, onClick = { sortMenu = false; renaming = selectedNotebook })
                        if (data.notebooks.size > 1) {
                            DropdownMenuItem(
                                text = { Text("Delete notebook", color = MaterialTheme.colorScheme.error) },
                                onClick = { sortMenu = false; deleting = selectedNotebook },
                            )
                        }
                    }
                }
            }
        }

        // Notebooks, then tags.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterPill("All notes", notebookId == null) { notebookId = null }
            for (nb in data.notebooks.sortedBy { it.createdAt }) {
                FilterPill(nb.name, notebookId == nb.id, icon = Icons.Rounded.Book) { notebookId = if (notebookId == nb.id) null else nb.id }
            }
            FilterPill("New notebook", false, icon = Icons.Rounded.Add) { newNotebook = true }
        }
        if (data.tags.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (t in data.tags) FilterPill("#$t", tag.equals(t, true), small = true) { tag = if (tag.equals(t, true)) null else t }
            }
        }
        HorizontalDivider(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 88.dp)) {
            if (shown.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.Description,
                        if (live.isEmpty()) "No notes yet" else "No notes here",
                        if (live.isEmpty()) "Tap + to write your first note." else "Try another notebook or tag, or tap + to write a note here.",
                    )
                }
            }
            items(shown, key = { it.id }) { n ->
                NoteRow(n, data, showNotebook = notebookId == null, today = today, modifier = Modifier.animateItem()) {
                    nav.push(Page.NotePage(n.id))
                }
            }
        }
    }

    // The + button starts a note in the notebook (and with the tag) on show.
    SideEffect {
        NotesContext.notebookId = notebookId
        NotesContext.tag = tag
    }

    if (newNotebook) {
        NameDialog("New notebook", confirm = "Create", onSave = { name, _ ->
            val nb = Notebook(name = name)
            Store.saveNotebook(nb)
            notebookId = nb.id
        }, onDismiss = { newNotebook = false })
    }
    renaming?.let { nb ->
        NameDialog("Rename notebook", initial = nb.name, onSave = { name, _ -> Store.saveNotebook(nb.copy(name = name)) }, onDismiss = { renaming = null })
    }
    deleting?.let { nb ->
        ConfirmDialog(
            "Delete notebook?",
            "“${nb.name}” is deleted. Its notes move to another notebook.",
            "Delete",
            onConfirm = {
                Store.deleteNotebook(nb.id)
                notebookId = null
            },
            onDismiss = { deleting = null },
        )
    }
}

/** Where the + button should put a new note: in the notebook (and with the tag) on show. */
object NotesContext {
    var notebookId: String? = null
    var tag: String? = null

    fun newNote(data: Data): Note = Note(
        notebookId = notebookId?.takeIf { id -> data.notebooks.any { it.id == id } } ?: data.notebooks.sortedBy { it.createdAt }.first().id,
        tags = listOfNotNull(tag),
    )
}

fun sortNotes(notes: List<Note>, sort: NoteSort): List<Note> {
    val order = when (sort) {
        NoteSort.UPDATED -> compareByDescending<Note> { it.updatedAt }
        NoteSort.CREATED -> compareByDescending<Note> { it.createdAt }
        NoteSort.TITLE -> compareBy<Note> { it.title.ifBlank { "￿" }.lowercase() }
    }
    return notes.sortedWith(compareByDescending<Note> { it.pinned }.then(order))
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .clip(shape)
            .then(
                if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer, shape)
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = if (small) 10.dp else 12.dp, vertical = if (small) 5.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(16.dp), tint = fg)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

/** A note in a list: title, a few lines of it, and when it changed, its notebook, checklist and tags. */
@Composable
fun NoteRow(note: Note, data: Data, showNotebook: Boolean, today: java.time.LocalDate, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val checklist = Markdown.checklist(note.body)
    Column(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    note.title.ifBlank { "Untitled" },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (note.title.isBlank()) muted else MaterialTheme.colorScheme.onSurface,
                )
                if (note.pinned) Icon(Icons.Rounded.PushPin, "Pinned", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
            val preview = Markdown.plain(note.body, 200).replace('\n', ' ')
            if (preview.isNotBlank()) {
                Text(preview, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(Dates.shortDate(note.updatedAt, today), style = MaterialTheme.typography.labelMedium, color = muted)
                if (showNotebook) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Book, null, Modifier.size(13.dp), tint = muted)
                        Spacer(Modifier.width(3.dp))
                        Text(data.notebook(note.notebookId).name, style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1)
                    }
                }
                if (checklist != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckBox, null, Modifier.size(13.dp), tint = muted)
                        Spacer(Modifier.width(3.dp))
                        Text("${checklist.first}/${checklist.second}", style = MaterialTheme.typography.labelMedium, color = muted)
                    }
                }
                for (t in note.tags.take(3)) Text("#$t", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            }
        }
        HorizontalDivider(Modifier.padding(start = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}
