package io.github.akrishna87.jotter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.SavedSearch
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.jotter.Store
import io.github.akrishna87.jotter.model.Data
import io.github.akrishna87.jotter.model.Dates
import io.github.akrishna87.jotter.model.Markdown
import io.github.akrishna87.jotter.model.Notebook
import io.github.akrishna87.jotter.model.Shortcut
import io.github.akrishna87.jotter.model.ShortcutKind
import io.github.akrishna87.jotter.model.Templates
import io.github.akrishna87.jotter.model.Version
import androidx.compose.ui.graphics.Color

/** A shortcut, ready to show: what it's called, its icon, and where it goes. */
data class ShortcutItem(val key: String, val label: String, val icon: ImageVector, val page: Page)

fun resolveShortcut(s: Shortcut, data: Data): ShortcutItem? {
    val key = s.kind.name + ":" + s.ref
    return when (s.kind) {
        ShortcutKind.NOTE -> data.note(s.ref)?.takeIf { !it.trashed }?.let { ShortcutItem(key, it.title.ifBlank { "Untitled" }, Icons.Rounded.Description, Page.NotePage(it.id)) }
        ShortcutKind.NOTEBOOK -> data.notebooks.firstOrNull { it.id == s.ref }?.let { ShortcutItem(key, it.name, Icons.Rounded.Book, Page.NoteList(NoteFilter.InNotebook(it.id))) }
        ShortcutKind.STACK -> data.stacks.firstOrNull { it.equals(s.ref, true) }?.let { ShortcutItem(key, it, Icons.Rounded.LibraryBooks, Page.NoteList(NoteFilter.InStack(it))) }
        ShortcutKind.TAG -> data.tags.firstOrNull { it.equals(s.ref, true) }?.let { ShortcutItem(key, "#$it", Icons.AutoMirrored.Rounded.Label, Page.NoteList(NoteFilter.Tagged(it))) }
        ShortcutKind.SEARCH -> data.searches.firstOrNull { it.id == s.ref }?.let { ShortcutItem(key, it.name, Icons.Rounded.SavedSearch, Page.NoteList(NoteFilter.Saved(it.id))) }
    }
}

/** Browse: shortcuts, notebooks and stacks, tags, saved searches, reminders, templates, the trash and settings. */
@Composable
fun BrowseScreen(data: Data, padding: PaddingValues) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    var newNotebook by remember { mutableStateOf(false) }
    val live = data.liveNotes
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
    ) {
        item {
            ScreenHeader("Browse") {
                SearchAction { nav.push(Page.Search()) }
                CircleAction(Icons.Rounded.Settings, "Settings") { nav.push(Page.SettingsPage) }
            }
        }
        val shortcuts = data.shortcuts.mapNotNull { resolveShortcut(it, data) }
        if (shortcuts.isNotEmpty()) {
            item { SectionHeader("Shortcuts") }
            cardItems(shortcuts, key = { "sc-" + it.key }) { sc, seg ->
                ListRow(sc.label, seg, leading = { IconTile(sc.icon, MaterialTheme.colorScheme.primary) }, onClick = { nav.push(sc.page) })
            }
        }
        item {
            SectionHeader("Notebooks") {
                IconButton(onClick = { newNotebook = true }, Modifier.semantics { contentDescription = "New notebook" }) { Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary) }
                TextButton(onClick = { nav.push(Page.Notebooks) }) { Text("Manage") }
            }
        }
        // All notes, each stack (with its notebooks), then the notebooks in no stack: one card.
        val books: List<Pair<String, @Composable () -> Unit>> =
            listOf<Pair<String, @Composable () -> Unit>>("all" to @Composable {
                ListRow("All notes", leading = { IconTile(Icons.Rounded.Description, Color(0xFFD9861C)) }, trailing = { Count(live.size) }, onClick = { nav.tab = Tab.NOTES })
            }) +
                data.stacks.map { stack -> ("stack-$stack" to @Composable { StackRow(stack, data) }) } +
                data.notebooksIn(null).map { nb -> ("nb-" + nb.id to @Composable { NotebookRow(nb, data, indent = false) }) }
        cardItems(books, key = { it.first }) { (_, row), seg -> Box(seg) { row() } }
        item { SectionHeader("Tags") { TextButton(onClick = { nav.push(Page.Tags) }) { Text("Manage") } } }
        if (data.tags.isEmpty()) {
            item { Text("Tag notes to find them across notebooks.", Modifier.padding(horizontal = 22.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        cardItems(data.childrenOf(null), key = { "tag-$it" }) { t, seg ->
            ListRow("#$t", seg, leading = { IconTile(Icons.AutoMirrored.Rounded.Label, Color(0xFF7C3AED)) }, trailing = { Count(notesFor(NoteFilter.Tagged(t), data, today).size) }, onClick = { nav.push(Page.NoteList(NoteFilter.Tagged(t))) })
        }
        if (data.searches.isNotEmpty()) {
            item { SectionHeader("Saved searches") }
            cardItems(data.searches, key = { "s-" + it.id }) { sv, seg ->
                ListRow(sv.name, seg, detail = sv.query, leading = { IconTile(Icons.Rounded.SavedSearch, Color(0xFF2F6BFF)) }, onClick = { nav.push(Page.NoteList(NoteFilter.Saved(sv.id))) })
            }
        }
        item { SectionHeader("More") }
        card {
            row {
                val n = live.count { it.reminderAt != null && it.reminderDone == 0L }
                ListRow("Reminders", leading = { IconTile(Icons.Rounded.Alarm, Color(0xFFE5484D)) }, trailing = { Count(n) }, onClick = { nav.push(Page.RemindersPage) })
            }
            row { ListRow("Templates", leading = { IconTile(Icons.Rounded.NoteAdd, Color(0xFF0E8A8A)) }, trailing = { Count(Templates.builtIn.size + data.templates.size) }, onClick = { nav.push(Page.TemplatesPage) }) }
            row { ListRow("Trash", leading = { IconTile(Icons.Rounded.Delete, Color(0xFF625A4E)) }, trailing = { Count(data.notes.count { it.trashed }) }, onClick = { nav.push(Page.Trash) }) }
            row { ListRow("Settings", leading = { IconTile(Icons.Rounded.Settings, Color(0xFF625A4E)) }, onClick = { nav.push(Page.SettingsPage) }) }
        }
    }
    if (newNotebook) {
        NameDialog("New notebook", confirm = "Create", onSave = { name, _ -> Store.saveNotebook(Notebook(name = name)) }, onDismiss = { newNotebook = false })
    }
}

@Composable
private fun StackRow(stack: String, data: Data) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    var open by rememberSaveable(stack) { mutableStateOf(true) }
    Column {
        ListRow(
            stack,
            leading = { IconTile(Icons.Rounded.LibraryBooks, Color(0xFF2F6BFF)) },
            trailing = {
                Count(notesFor(NoteFilter.InStack(stack), data, today).size)
                IconButton(onClick = { open = !open }, Modifier.semantics { contentDescription = if (open) "Collapse $stack" else "Expand $stack" }) {
                    Icon(if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null)
                }
            },
            onClick = { nav.push(Page.NoteList(NoteFilter.InStack(stack))) },
        )
        if (open) for (nb in data.notebooksIn(stack)) NotebookRow(nb, data, indent = true)
    }
}

@Composable
private fun NotebookRow(nb: Notebook, data: Data, indent: Boolean) {
    val nav = LocalNav.current
    ListRow(
        nb.name,
        modifier = if (indent) Modifier.padding(start = 24.dp) else Modifier,
        leading = { IconTile(Icons.Rounded.Book, Color(0xFFB06400)) },
        detail = if (data.settings.defaultNotebookId == nb.id) "Default notebook" else null,
        trailing = { Count(data.liveNotes.count { it.notebookId == nb.id }) },
        onClick = { nav.push(Page.NoteList(NoteFilter.InNotebook(nb.id))) },
    )
}

/** Notebooks and stacks: rename, put in a stack, make default, add to shortcuts, delete. */
@Composable
fun NotebooksPage(data: Data) {
    val nav = LocalNav.current
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Notebook?>(null) }
    var stacking by remember { mutableStateOf<Notebook?>(null) }
    var deleting by remember { mutableStateOf<Notebook?>(null) }
    var renamingStack by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageBar("Notebooks", onBack = { nav.pop() }) {
            IconButton(onClick = { adding = true }, Modifier.semantics { contentDescription = "New notebook" }) { Icon(Icons.Rounded.Add, null) }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
            for (stack in data.stacks) {
                item(key = "s-$stack") {
                    SectionHeader(stack) {
                        IconButton(onClick = { renamingStack = stack }, Modifier.semantics { contentDescription = "Rename stack $stack" }) { Icon(Icons.Rounded.Edit, null) }
                        StarButton(Shortcut(ShortcutKind.STACK, stack), data)
                    }
                }
                items(data.notebooksIn(stack), key = { it.id }) { nb -> ManageNotebookRow(nb, data, { renaming = nb }, { stacking = nb }, { deleting = nb }) }
            }
            if (data.stacks.isNotEmpty()) item { SectionHeader("Not in a stack") }
            items(data.notebooksIn(null), key = { it.id }) { nb -> ManageNotebookRow(nb, data, { renaming = nb }, { stacking = nb }, { deleting = nb }) }
        }
    }
    if (adding) NameDialog("New notebook", confirm = "Create", onSave = { name, _ -> Store.saveNotebook(Notebook(name = name)) }, onDismiss = { adding = false })
    renaming?.let { nb -> NameDialog("Rename notebook", initial = nb.name, onSave = { name, _ -> Store.saveNotebook(nb.copy(name = name)) }, onDismiss = { renaming = null }) }
    stacking?.let { nb ->
        StackDialog(data, nb, onDismiss = { stacking = null })
    }
    renamingStack?.let { s ->
        NameDialog("Rename stack", initial = s, label = "Stack name", onSave = { name, _ -> Store.renameStack(s, name) }, onDismiss = { renamingStack = null })
    }
    deleting?.let { nb ->
        val n = data.liveNotes.count { it.notebookId == nb.id }
        ConfirmDialog(
            "Delete “${nb.name}”?",
            if (n == 0) "It has no notes." else "Its $n note${if (n == 1) "" else "s"} move${if (n == 1) "s" else ""} to the trash, where you can restore them.",
            "Delete",
            onConfirm = { Store.deleteNotebook(nb.id) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun StarButton(s: Shortcut, data: Data) {
    val on = data.isShortcut(s)
    IconButton(onClick = { Store.toggleShortcut(s) }, Modifier.semantics { contentDescription = if (on) "Remove from shortcuts" else "Add to shortcuts" }) {
        Icon(if (on) Icons.Rounded.Star else Icons.Outlined.StarOutline, null, tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ManageNotebookRow(nb: Notebook, data: Data, onRename: () -> Unit, onStack: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ListRow(
        nb.name,
        leading = { Icon(Icons.Rounded.Book, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        detail = listOfNotNull(
            data.liveNotes.count { it.notebookId == nb.id }.let { if (it == 1) "1 note" else "$it notes" },
            if (data.settings.defaultNotebookId == nb.id) "default" else null,
        ).joinToString(" · "),
        trailing = {
            StarButton(Shortcut(ShortcutKind.NOTEBOOK, nb.id), data)
            Box {
                IconButton(onClick = { menu = true }, Modifier.semantics { contentDescription = "${nb.name} options" }) { Icon(Icons.Rounded.MoreVert, null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text(if (nb.stack == null) "Add to stack" else "Change stack") }, leadingIcon = { Icon(Icons.Rounded.FolderCopy, null) }, onClick = { menu = false; onStack() })
                    if (data.settings.defaultNotebookId != nb.id) {
                        DropdownMenuItem(text = { Text("Make default notebook") }, onClick = { menu = false; Store.updateSettings { it.copy(defaultNotebookId = nb.id) } })
                    }
                    if (data.notebooks.size > 1) {
                        DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        },
    )
}

/** Puts a notebook in a stack: an existing one, a new one, or none. */
@Composable
private fun StackDialog(data: Data, nb: Notebook, onDismiss: () -> Unit) {
    var newStack by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stack for “${nb.name}”") },
        text = {
            Column {
                @Composable
                fun row(label: String, stack: String?) {
                    Text(
                        label + if (nb.stack.equals(stack, true) || (stack == null && nb.stack == null)) "  ✓" else "",
                        Modifier.fillMaxWidth().clickable { Store.saveNotebook(nb.copy(stack = stack)); onDismiss() }.padding(vertical = 12.dp),
                    )
                }
                row("No stack", null)
                for (s in data.stacks) row(s, s)
                Text("New stack…", Modifier.fillMaxWidth().clickable { newStack = true }.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (newStack) {
        NameDialog("New stack", label = "Stack name", confirm = "Create", onSave = { name, _ -> Store.saveNotebook(nb.copy(stack = name)); onDismiss() }, onDismiss = { newStack = false })
    }
}

/** Tags, nested as you arrange them: rename, nest, add to shortcuts, delete. */
@Composable
fun TagsPage(data: Data) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var nesting by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    // The tree, depth first.
    val rows = remember(data.tags, data.tagInfo) {
        val out = ArrayList<Pair<String, Int>>()
        fun add(parent: String?, depth: Int) {
            for (t in data.childrenOf(parent)) {
                if (out.any { it.first.equals(t, true) }) continue
                out += t to depth
                add(t, depth + 1)
            }
        }
        add(null, 0)
        out
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageBar("Tags", onBack = { nav.pop() }) {
            IconButton(onClick = { adding = true }, Modifier.semantics { contentDescription = "New tag" }) { Icon(Icons.Rounded.Add, null) }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (rows.isEmpty()) item { EmptyState(Icons.AutoMirrored.Rounded.Label, "No tags yet", "Add tags to notes from their options, or make one here.") }
            items(rows, key = { it.first }) { (t, depth) ->
                var menu by remember { mutableStateOf(false) }
                ListRow(
                    "#$t",
                    modifier = Modifier.padding(start = (depth * 20).dp),
                    leading = { Icon(Icons.AutoMirrored.Rounded.Label, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailing = {
                        Count(notesFor(NoteFilter.Tagged(t), data, today).size)
                        StarButton(Shortcut(ShortcutKind.TAG, t), data)
                        Box {
                            IconButton(onClick = { menu = true }, Modifier.semantics { contentDescription = "#$t options" }) { Icon(Icons.Rounded.MoreVert, null) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = t })
                                DropdownMenuItem(text = { Text("Put under another tag") }, onClick = { menu = false; nesting = t })
                                DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleting = t })
                            }
                        }
                    },
                    onClick = { nav.push(Page.NoteList(NoteFilter.Tagged(t))) },
                )
            }
        }
    }
    if (adding) NameDialog("New tag", label = "Tag", confirm = "Add", onSave = { name, _ -> Store.addTag(name, null) }, onDismiss = { adding = false })
    renaming?.let { t -> NameDialog("Rename #$t", initial = t, label = "Tag", onSave = { name, _ -> Store.renameTag(t, name) }, onDismiss = { renaming = null }) }
    nesting?.let { t -> TagParentDialog(data, t, onDismiss = { nesting = null }) }
    deleting?.let { t ->
        ConfirmDialog("Delete #$t?", "It comes off every note. The notes stay, and tags under it move up a level.", "Delete", onConfirm = { Store.deleteTag(t) }, onDismiss = { deleting = null })
    }
}

/** Notes with reminders: coming up, then done. */
@Composable
fun RemindersPage(data: Data) {
    val nav = LocalNav.current
    val withReminders = data.liveNotes.filter { it.reminderAt != null }
    val open = withReminders.filter { it.reminderDone == 0L }.sortedBy { it.reminderAt }
    val done = withReminders.filter { it.reminderDone != 0L }.sortedByDescending { it.reminderDone }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageBar("Reminders", onBack = { nav.pop() })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (withReminders.isEmpty()) item { EmptyState(Icons.Rounded.Alarm, "No reminders", "Open a note and tap the alarm to be reminded about it.") }
            if (open.isNotEmpty()) {
                item { SectionHeader("Coming up") }
                items(open, key = { "o-" + it.id }) { ReminderRow(it) }
            }
            if (done.isNotEmpty()) {
                item { SectionHeader("Done") }
                items(done, key = { "d-" + it.id }) { ReminderRow(it) }
            }
        }
    }
}

/** Jotter's templates and yours: start a note from one, or delete your own. */
@Composable
fun TemplatesPage(data: Data) {
    val nav = LocalNav.current
    var deleting by remember { mutableStateOf<io.github.akrishna87.jotter.model.Template?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageBar("Templates", onBack = { nav.pop() })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { SectionHeader("Yours") }
            if (data.templates.isEmpty()) {
                item { Text("Save any note as a template from its options.", Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(data.templates, key = { it.id }) { t ->
                ListRow(
                    t.name,
                    detail = Markdown.plain(t.body, 80).replace('\n', ' '),
                    leading = { Icon(Icons.Rounded.NoteAdd, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailing = { IconButton(onClick = { deleting = t }, Modifier.semantics { contentDescription = "Delete template ${t.name}" }) { Icon(Icons.Rounded.Delete, null) } },
                    onClick = { nav.pop(); nav.newNote(data, template = t) },
                )
            }
            item { SectionHeader("Jotter's") }
            items(Templates.builtIn, key = { it.id }) { t ->
                ListRow(
                    t.name,
                    detail = Markdown.plain(t.body, 80).replace('\n', ' '),
                    leading = { Icon(Icons.Rounded.NoteAdd, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = { nav.pop(); nav.newNote(data, template = t) },
                )
            }
        }
    }
    deleting?.let { t -> ConfirmDialog("Delete “${t.name}”?", "Notes made from it stay as they are.", "Delete", onConfirm = { Store.deleteTemplate(t.id) }, onDismiss = { deleting = null }) }
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

/** Earlier versions of a note: read one, and bring it back if you want it. */
@Composable
fun HistoryPage(id: String, data: Data) {
    val nav = LocalNav.current
    val actions = LocalActions.current
    val clock = LocalClock.current
    val note = data.note(id)
    if (note == null) {
        LaunchedEffect(Unit) { nav.pop() }
        return
    }
    var viewing by remember { mutableStateOf<Version?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageBar("Note history", onBack = { if (viewing != null) viewing = null else nav.pop() })
        val v = viewing
        if (v == null) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    ListRow("Now", detail = Dates.full(note.updatedAt, clock.is24Hour), leading = { Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.primary) })
                }
                items(note.history.reversed(), key = { it.savedAt }) { ver ->
                    ListRow(
                        ver.title.ifBlank { "Untitled" },
                        detail = Dates.full(ver.savedAt, clock.is24Hour) + " · " + Markdown.wordCount(ver.body) + " words",
                        leading = { Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { viewing = ver },
                    )
                }
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Text(Dates.full(v.savedAt, clock.is24Hour), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(v.title.ifBlank { "Untitled" }, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.headlineSmall)
                MarkdownView(v.body, note.attachments, noteStyles(data.settings.textSize.scale), onToggle = {}, onEdit = {}, onNoteLink = {})
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                TextButton(onClick = {
                    Store.restoreVersion(id, v)
                    actions.message("Version restored. The one before is in the history.")
                    nav.pop()
                }) { Text("Restore this version") }
            }
        }
    }
}
