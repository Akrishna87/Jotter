package io.github.akrishna87.jotter.ui

import android.app.Activity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Star
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.jotter.Exports
import io.github.akrishna87.jotter.Store
import io.github.akrishna87.jotter.model.Data
import io.github.akrishna87.jotter.model.Dates
import io.github.akrishna87.jotter.model.ListStyle
import io.github.akrishna87.jotter.model.Markdown
import io.github.akrishna87.jotter.model.Note
import io.github.akrishna87.jotter.model.NoteSort
import io.github.akrishna87.jotter.model.Notebook
import io.github.akrishna87.jotter.model.Search
import io.github.akrishna87.jotter.model.Shortcut
import io.github.akrishna87.jotter.model.ShortcutKind
import java.time.LocalDate

/** The notes a [NoteFilter] shows, not in the trash. */
fun notesFor(filter: NoteFilter, data: Data, today: LocalDate): List<Note> = when (filter) {
    NoteFilter.All -> data.liveNotes
    is NoteFilter.InNotebook -> data.liveNotes.filter { it.notebookId == filter.id }
    is NoteFilter.InStack -> data.notebooksIn(filter.name).map { it.id }.toSet().let { ids -> data.liveNotes.filter { it.notebookId in ids } }
    is NoteFilter.Tagged -> data.tagAndChildren(filter.name).let { all -> data.liveNotes.filter { n -> n.tags.any { it.lowercase() in all } } }
    is NoteFilter.Saved -> data.searches.firstOrNull { it.id == filter.id }?.let { Search.run(it.query, data, today) } ?: emptyList()
    NoteFilter.WithReminders -> data.liveNotes.filter { it.hasReminder }
}

fun filterTitle(filter: NoteFilter, data: Data): String = when (filter) {
    NoteFilter.All -> "All notes"
    is NoteFilter.InNotebook -> data.notebook(filter.id).name
    is NoteFilter.InStack -> filter.name
    is NoteFilter.Tagged -> "#" + filter.name
    is NoteFilter.Saved -> data.searches.firstOrNull { it.id == filter.id }?.name ?: "Search"
    NoteFilter.WithReminders -> "Reminders"
}

fun shortcutFor(filter: NoteFilter): Shortcut? = when (filter) {
    is NoteFilter.InNotebook -> Shortcut(ShortcutKind.NOTEBOOK, filter.id)
    is NoteFilter.InStack -> Shortcut(ShortcutKind.STACK, filter.name)
    is NoteFilter.Tagged -> Shortcut(ShortcutKind.TAG, filter.name)
    is NoteFilter.Saved -> Shortcut(ShortcutKind.SEARCH, filter.id)
    else -> null
}

fun sortNotes(notes: List<Note>, sort: NoteSort): List<Note> {
    val order = when (sort) {
        NoteSort.UPDATED -> compareByDescending<Note> { it.updatedAt }
        NoteSort.CREATED -> compareByDescending<Note> { it.createdAt }
        NoteSort.TITLE -> compareBy<Note> { it.title.ifBlank { "￿" }.lowercase() }
        // Notes with reminders first, soonest first; done ones after.
        NoteSort.REMINDER -> compareBy<Note>({ it.reminderAt == null }, { it.reminderDone != 0L }, { it.reminderAt ?: Long.MAX_VALUE })
    }
    return notes.sortedWith(compareByDescending<Note> { it.pinned }.then(order))
}

/** Where the + button should put a new note: in the notebook (and with the tag) on show in Notes. */
object NotesContext {
    var notebookId: String? = null
    var tag: String? = null
}

/** The Notes tab: every note, filtered by notebook and tag, sorted and shown the way you choose. */
@Composable
fun NotesScreen(data: Data, padding: PaddingValues) {
    val nav = LocalNav.current
    var notebookId by rememberSaveable { mutableStateOf<String?>(null) }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    var newNotebook by remember { mutableStateOf(false) }
    if (notebookId != null && data.notebooks.none { it.id == notebookId }) notebookId = null

    val live = data.liveNotes
    val shown = live.filter { (notebookId == null || it.notebookId == notebookId) && (tag == null || it.tags.any { t -> t.equals(tag, true) }) }
    val selectedNotebook = data.notebooks.firstOrNull { it.id == notebookId }

    SideEffect {
        NotesContext.notebookId = notebookId
        NotesContext.tag = tag
    }

    NoteCollection(
        notes = shown,
        data = data,
        modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        bottomPadding = padding.calculateBottomPadding() + 88.dp,
        showNotebook = notebookId == null,
        emptyTitle = if (live.isEmpty()) "No notes yet" else "No notes here",
        emptyText = if (live.isEmpty()) "Tap + to write your first note." else "Try another notebook or tag, or tap + to write a note here.",
        top = { selection ->
            if (selection == null) {
                ScreenHeader(selectedNotebook?.name ?: "Notes", if (shown.size == 1) "1 note" else "${shown.size} notes") {
                    SearchAction { nav.push(Page.Search()) }
                    ViewMenu(data)
                }
            } else selection()
        },
        header = {
            fullLine {
                Column {
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
                }
            }
        },
    )

    if (newNotebook) {
        NameDialog("New notebook", confirm = "Create", onSave = { name, _ ->
            val nb = Notebook(name = name)
            Store.saveNotebook(nb)
            notebookId = nb.id
        }, onDismiss = { newNotebook = false })
    }
}

/** Sort and list style. */
@Composable
fun ViewMenu(data: Data) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }, Modifier.semantics { contentDescription = "View options" }) { Icon(Icons.Rounded.MoreVert, null) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            Text("Sort by", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (s in NoteSort.entries) {
                DropdownMenuItem(
                    text = { Text(s.label) },
                    onClick = { menu = false; Store.updateSettings { it.copy(noteSort = s) } },
                    trailingIcon = { if (data.settings.noteSort == s) Icon(Icons.Rounded.Check, null) },
                    modifier = Modifier.semantics { contentDescription = "Sort by ${s.label.lowercase()}" },
                )
            }
            HorizontalDivider()
            Text("Show as", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (l in ListStyle.entries) {
                DropdownMenuItem(
                    text = { Text(l.label) },
                    onClick = { menu = false; Store.updateSettings { it.copy(listStyle = l) } },
                    trailingIcon = { if (data.settings.listStyle == l) Icon(Icons.Rounded.Check, null) },
                )
            }
        }
    }
}

fun LazyGridScope.fullLine(key: Any? = null, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

/** A list of notes from the Browse tab, Home or a shortcut: a notebook, stack, tag, saved search or the reminders. */
@Composable
fun NoteListPage(filter: NoteFilter, data: Data) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var editingSearch by remember { mutableStateOf(false) }
    if (filter is NoteFilter.InNotebook && data.notebooks.none { it.id == filter.id }) {
        androidx.compose.runtime.LaunchedEffect(Unit) { nav.pop() }
        return
    }
    val notes = notesFor(filter, data, today)
    val shortcut = shortcutFor(filter)
    Box(Modifier.fillMaxSize()) {
        NoteCollection(
            notes = notes,
            data = data,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            bottomPadding = 104.dp,
            showNotebook = filter !is NoteFilter.InNotebook,
            sort = if (filter == NoteFilter.WithReminders) NoteSort.REMINDER else data.settings.noteSort,
            emptyTitle = "No notes here",
            emptyText = if (filter is NoteFilter.Saved) "Nothing matches this search yet." else "Tap + to write one.",
            top = { selection ->
                if (selection != null) selection() else PageBar(filterTitle(filter, data), onBack = { nav.pop() }) {
                    if (shortcut != null) {
                        val on = data.isShortcut(shortcut)
                        IconButton(onClick = { Store.toggleShortcut(shortcut) }, Modifier.semantics { contentDescription = if (on) "Remove from shortcuts" else "Add to shortcuts" }) {
                            Icon(if (on) Icons.Rounded.Star else Icons.Outlined.StarOutline, null, tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    ViewMenu(data)
                    if (filter is NoteFilter.InNotebook || filter is NoteFilter.Saved) {
                        Box {
                            IconButton(onClick = { menu = true }, Modifier.semantics { contentDescription = "More options" }) { Icon(Icons.Rounded.MoreVert, null) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                if (filter is NoteFilter.InNotebook) {
                                    DropdownMenuItem(text = { Text("Rename notebook") }, onClick = { menu = false; renaming = true })
                                    DropdownMenuItem(text = { Text("Make default notebook") }, onClick = { menu = false; Store.updateSettings { it.copy(defaultNotebookId = filter.id) } })
                                    if (data.notebooks.size > 1) DropdownMenuItem(text = { Text("Delete notebook", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleting = true })
                                } else if (filter is NoteFilter.Saved) {
                                    DropdownMenuItem(text = { Text("Edit search") }, onClick = { menu = false; editingSearch = true })
                                    DropdownMenuItem(text = { Text("Delete search", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; Store.deleteSearch(filter.id); nav.pop() })
                                }
                            }
                        }
                    }
                }
            },
        )
        if (filter !is NoteFilter.Saved) {
            Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp)) {
                Fab("New note") {
                    nav.newNote(
                        data,
                        notebookId = (filter as? NoteFilter.InNotebook)?.id ?: (filter as? NoteFilter.InStack)?.let { data.notebooksIn(it.name).firstOrNull()?.id },
                        tags = listOfNotNull((filter as? NoteFilter.Tagged)?.name),
                    )
                }
            }
        }
    }
    if (renaming && filter is NoteFilter.InNotebook) {
        val nb = data.notebook(filter.id)
        NameDialog("Rename notebook", initial = nb.name, onSave = { name, _ -> Store.saveNotebook(nb.copy(name = name)) }, onDismiss = { renaming = false })
    }
    if (deleting && filter is NoteFilter.InNotebook) {
        val nb = data.notebook(filter.id)
        ConfirmDialog(
            "Delete notebook?",
            "“${nb.name}” is deleted and its ${notes.size} notes move to the trash, where you can restore them.",
            "Delete",
            onConfirm = { Store.deleteNotebook(nb.id); nav.pop() },
            onDismiss = { deleting = false },
        )
    }
    if (editingSearch && filter is NoteFilter.Saved) {
        data.searches.firstOrNull { it.id == filter.id }?.let { s -> SaveSearchDialog(s, onDismiss = { editingSearch = false }) }
    }
}

/**
 * Notes as snippets, cards or a simple list, sorted with pinned notes first. Long-press a note to
 * select notes, then move, tag, pin, merge, print or trash them together.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCollection(
    notes: List<Note>,
    data: Data,
    modifier: Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp,
    showNotebook: Boolean,
    emptyTitle: String,
    emptyText: String,
    sort: NoteSort = data.settings.noteSort,
    top: @Composable (selection: (@Composable () -> Unit)?) -> Unit,
    header: LazyGridScope.() -> Unit = {},
) {
    val nav = LocalNav.current
    val actions = LocalActions.current
    val context = LocalContext.current
    val today = LocalClock.current.today
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var moving by remember { mutableStateOf(false) }
    var tagging by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    val shown = sortNotes(notes, sort)
    selected = selected.filter { id -> shown.any { it.id == id } }.toSet()
    val selecting = selected.isNotEmpty()
    androidx.activity.compose.BackHandler(enabled = selecting) { selected = emptySet() }
    val style = data.settings.listStyle

    val selectionBar: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { selected = emptySet() }, Modifier.semantics { contentDescription = "Clear selection" }) { Icon(Icons.Rounded.Close, null) }
            Text("${selected.size} selected", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { moving = true }, Modifier.semantics { contentDescription = "Move to notebook" }) { Icon(Icons.Rounded.DriveFileMove, null) }
            IconButton(onClick = { tagging = true }, Modifier.semantics { contentDescription = "Tag notes" }) { Icon(Icons.AutoMirrored.Rounded.Label, null) }
            IconButton(onClick = {
                val ids = selected
                Store.trashNotes(ids)
                selected = emptySet()
                actions.offerUndo(if (ids.size == 1) "Note moved to trash" else "${ids.size} notes moved to trash") { ids.forEach(Store::restoreNote) }
            }, Modifier.semantics { contentDescription = "Move to trash" }) { Icon(Icons.Rounded.Delete, null) }
            Box {
                IconButton(onClick = { more = true }, Modifier.semantics { contentDescription = "More for selected" }) { Icon(Icons.Rounded.MoreVert, null) }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    DropdownMenuItem(text = { Text("Select all") }, leadingIcon = { Icon(Icons.Rounded.SelectAll, null) }, onClick = { more = false; selected = shown.map { it.id }.toSet() })
                    val allPinned = shown.filter { it.id in selected }.all { it.pinned }
                    DropdownMenuItem(text = { Text(if (allPinned) "Unpin" else "Pin") }, leadingIcon = { Icon(Icons.Rounded.PushPin, null) }, onClick = {
                        more = false; Store.setNotes(selected) { it.copy(pinned = !allPinned) }
                    })
                    if (selected.size > 1) DropdownMenuItem(text = { Text("Merge notes") }, leadingIcon = { Icon(Icons.Rounded.CallMerge, null) }, onClick = { more = false; merging = true })
                    DropdownMenuItem(text = { Text("Print or save as PDF") }, leadingIcon = { Icon(Icons.Rounded.Print, null) }, onClick = {
                        more = false
                        (context as? Activity)?.let { Exports.print(it, shown.filter { n -> n.id in selected }) }
                    })
                }
            }
        }
    }
    Column(modifier) {
        top(if (selecting) selectionBar else null)
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (style == ListStyle.CARDS) 2 else 1),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = if (style == ListStyle.CARDS) 12.dp else 0.dp, end = if (style == ListStyle.CARDS) 12.dp else 0.dp, bottom = bottomPadding),
            horizontalArrangement = Arrangement.spacedBy(if (style == ListStyle.CARDS) 10.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(if (style == ListStyle.CARDS) 10.dp else 0.dp),
        ) {
            header()
            if (shown.isEmpty()) fullLine("empty") { EmptyState(Icons.Rounded.Description, emptyTitle, emptyText) }
            items(shown, key = { it.id }) { n ->
                val isSelected = n.id in selected
                val click = Modifier.combinedClickable(
                    onClick = { if (selecting) selected = if (isSelected) selected - n.id else selected + n.id else nav.open(n) },
                    onLongClick = { selected = if (isSelected) selected - n.id else selected + n.id },
                )
                when (style) {
                    ListStyle.CARDS -> NoteCard(n, data, isSelected, today, click)
                    ListStyle.LIST -> NoteLine(n, isSelected, today, click)
                    ListStyle.SNIPPETS -> NoteRow(n, data, showNotebook, today, isSelected, click)
                }
            }
        }
    }

    if (moving) {
        NotebookPickerDialog(data, null, onPick = { id -> Store.moveNotes(selected, id); actions.message("Moved to ${data.notebook(id).name}"); selected = emptySet() }, onDismiss = { moving = false })
    }
    if (tagging) {
        LabelsDialogFor("#", "Add tags", "New tag", data.tags, emptyList(), onDone = { tags -> Store.tagNotes(selected, tags); selected = emptySet() }, onDismiss = { tagging = false })
    }
    if (merging) {
        val ordered = shown.filter { it.id in selected }
        ConfirmDialog(
            "Merge ${ordered.size} notes?",
            "They become one note, “${ordered.first().title.ifBlank { "Untitled" }}”. The others move to the trash.",
            "Merge",
            onConfirm = {
                Store.merge(ordered.map { it.id })?.let { id -> Store.data.value.note(id)?.let(nav::open) }
                selected = emptySet()
            },
            onDismiss = { merging = false },
        )
    }
}

@Composable
fun FilterPill(
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

private fun firstImage(n: Note) = n.attachments.firstOrNull { it.isImage }

@Composable
private fun selectedBg(selected: Boolean) = if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier

/** Small icons for what's in a note: its notebook, checklist, attachments, reminder and tags. */
@Composable
private fun NoteMeta(note: Note, data: Data?, showNotebook: Boolean, today: LocalDate) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val checklist = Markdown.checklist(note.body)
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(Dates.shortDate(note.updatedAt, today), style = MaterialTheme.typography.labelMedium, color = muted)
        if (showNotebook && data != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Book, null, Modifier.size(13.dp), tint = muted)
                Spacer(Modifier.width(3.dp))
                Text(data.notebook(note.notebookId).name, style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1)
            }
        }
        if (note.reminderAt != null) {
            Icon(Icons.Rounded.Alarm, if (note.reminderDone != 0L) "Reminder done" else "Reminder", Modifier.size(13.dp), tint = if (note.reminderDone != 0L) muted else MaterialTheme.colorScheme.primary)
        }
        if (checklist != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckBox, null, Modifier.size(13.dp), tint = muted)
                Spacer(Modifier.width(3.dp))
                Text("${checklist.first}/${checklist.second}", style = MaterialTheme.typography.labelMedium, color = muted)
            }
        }
        if (note.attachments.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AttachFile, null, Modifier.size(13.dp), tint = muted)
                Text(note.attachments.size.toString(), style = MaterialTheme.typography.labelMedium, color = muted)
            }
        }
        for (t in note.tags.take(3)) Text("#$t", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
    }
}

/** A note in a list: title, a few lines of it, a picture if it has one, and what's in it. */
@Composable
fun NoteRow(note: Note, data: Data, showNotebook: Boolean, today: LocalDate, selected: Boolean = false, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().then(selectedBg(selected)).then(modifier)) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selected) Icon(Icons.Rounded.Check, "Selected", Modifier.size(18.dp).padding(end = 4.dp), tint = MaterialTheme.colorScheme.primary)
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
                NoteMeta(note, data, showNotebook, today)
            }
            firstImage(note)?.let { a ->
                val bmp = rememberThumbnail(a, 240)
                if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.padding(start = 12.dp).size(64.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            }
        }
        HorizontalDivider(Modifier.padding(start = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun NoteCard(note: Note, data: Data, selected: Boolean, today: LocalDate, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(modifier),
    ) {
        firstImage(note)?.let { a ->
            val bmp = rememberThumbnail(a, 480)
            if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().height(110.dp), contentScale = ContentScale.Crop)
        }
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(note.title.ifBlank { "Untitled" }, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (note.pinned) Icon(Icons.Rounded.PushPin, "Pinned", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            }
            val preview = Markdown.plain(note.body, 300)
            if (preview.isNotBlank()) Text(preview, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 6, overflow = TextOverflow.Ellipsis)
            Text(Dates.shortDate(note.updatedAt, today), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NoteLine(note: Note, selected: Boolean, today: LocalDate, modifier: Modifier) {
    Column(Modifier.fillMaxWidth().then(selectedBg(selected)).then(modifier)) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (note.pinned) Icon(Icons.Rounded.PushPin, "Pinned", Modifier.size(14.dp).padding(end = 2.dp), tint = MaterialTheme.colorScheme.primary)
            Text(note.title.ifBlank { "Untitled" }, Modifier.weight(1f).padding(start = 4.dp), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Dates.shortDate(note.updatedAt, today), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(Modifier.padding(start = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}
