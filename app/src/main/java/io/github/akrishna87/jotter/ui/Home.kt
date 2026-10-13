package io.github.akrishna87.jotter.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.jotter.Store
import io.github.akrishna87.jotter.model.Block
import io.github.akrishna87.jotter.model.Data
import io.github.akrishna87.jotter.model.Dates
import io.github.akrishna87.jotter.model.HomeSection
import io.github.akrishna87.jotter.model.Markdown
import io.github.akrishna87.jotter.model.Note
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home, as in Evernote: ways to start a note, a scratch pad, pinned and recent notes, reminders,
 * open checklist items from every note, shortcuts and tags. Settings chooses which sections show.
 */
@Composable
fun HomeScreen(data: Data, padding: PaddingValues) {
    val nav = LocalNav.current
    val clock = LocalClock.current
    var templates by remember { mutableStateOf(false) }
    val hour = clock.now.hour
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    val live = data.liveNotes
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
    ) {
        item {
            ScreenHeader(greeting, DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()).format(clock.today)) {
                SearchAction { nav.push(Page.Search()) }
                IconButton(onClick = { nav.push(Page.SettingsPage) }, Modifier.semantics { contentDescription = "Settings" }) { Icon(Icons.Rounded.Settings, null) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CreateChip(Icons.Rounded.Description, "Note") { nav.newNote(data) }
                CreateChip(Icons.Rounded.CheckBox, "Checklist") { nav.newNote(data, kind = "checklist") }
                CreateChip(Icons.Rounded.PhotoCamera, "Photo") { nav.newNote(data, kind = "photo") }
                CreateChip(Icons.Rounded.DocumentScanner, "Scan") { nav.newNote(data, kind = "scan") }
                CreateChip(Icons.Rounded.Mic, "Audio") { nav.newNote(data, kind = "audio") }
                CreateChip(Icons.Rounded.Brush, "Sketch") { nav.newNote(data, kind = "sketch") }
                CreateChip(Icons.Rounded.NoteAdd, "Template") { templates = true }
            }
        }
        for (section in data.settings.homeSections) {
            when (section) {
                HomeSection.SCRATCH -> item(key = "scratch") { ScratchPad(data) }
                HomeSection.PINNED -> {
                    val pinned = live.filter { it.pinned }.sortedByDescending { it.updatedAt }
                    if (pinned.isNotEmpty()) {
                        item(key = "pinned-h") { HomeHeader("Pinned notes") }
                        item(key = "pinned") { NoteStrip(pinned) }
                    }
                }
                HomeSection.RECENT -> if (live.isNotEmpty()) {
                    item(key = "recent-h") { HomeHeader("Recent notes", "All notes") { nav.tab = Tab.NOTES } }
                    item(key = "recent") { NoteStrip(live.sortedByDescending { it.updatedAt }.take(12)) }
                }
                HomeSection.REMINDERS -> {
                    val due = live.filter { it.reminderAt != null && it.reminderDone == 0L }.sortedBy { it.reminderAt }
                    if (due.isNotEmpty()) {
                        item(key = "rem-h") { HomeHeader("Reminders", "See all") { nav.push(Page.RemindersPage) } }
                        items(due.take(5), key = { "rem-" + it.id }) { n -> ReminderRow(n) }
                    }
                }
                HomeSection.TASKS -> {
                    val open = live.sortedByDescending { it.updatedAt }.flatMap { n -> Markdown.openChecks(n.body).map { n to it } }
                    if (open.isNotEmpty()) {
                        item(key = "tasks-h") { HomeHeader("Open checklists") }
                        items(open.take(8), key = { (n, c) -> "task-${n.id}-${c.line}" }) { (n, c) -> CheckRow(n, c) }
                    }
                }
                HomeSection.SHORTCUTS -> if (data.shortcuts.isNotEmpty()) {
                    item(key = "sc-h") { HomeHeader("Shortcuts") }
                    items(data.shortcuts.mapNotNull { resolveShortcut(it, data) }, key = { "sc-" + it.key }) { s ->
                        ListRow(s.label, leading = { Icon(s.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }, onClick = { nav.push(s.page) })
                    }
                }
                HomeSection.TAGS -> if (data.tags.isNotEmpty()) {
                    item(key = "tags-h") { HomeHeader("Tags", "Manage") { nav.push(Page.Tags) } }
                    item(key = "tags") {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (t in data.tags) FilterPill("#$t", false, small = true) { nav.push(Page.NoteList(NoteFilter.Tagged(t))) }
                        }
                    }
                }
            }
        }
        if (live.isEmpty()) {
            item { EmptyState(Icons.Rounded.Description, "Nothing here yet", "Tap + or one of the buttons above to write your first note.") }
        }
    }
    if (templates) TemplatePickerDialog(data, onPick = { t -> nav.newNote(data, template = t) }, onDismiss = { templates = false })
}

@Composable
private fun CreateChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .width(72.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "New ${label.lowercase()}" }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun HomeHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

/** A quick place to jot something down; it stays until it's saved as a note or cleared. */
@Composable
private fun ScratchPad(data: Data) {
    val nav = LocalNav.current
    val actions = LocalActions.current
    var text by remember { mutableStateOf(TextFieldValue(data.settings.scratchPad)) }
    LaunchedEffect(text.text) {
        delay(600)
        if (text.text != Store.data.value.settings.scratchPad) Store.updateSettings { it.copy(scratchPad = text.text) }
    }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Text("Scratch pad", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        PlainField(
            value = text,
            onChange = { text = it },
            placeholder = "Jot something down…",
            description = "Scratch pad",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 6.dp, end = 8.dp).heightIn(min = 56.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (text.text.isNotBlank()) {
                TextButton(onClick = { text = TextFieldValue(""); Store.updateSettings { it.copy(scratchPad = "") } }) { Text("Clear") }
                TextButton(onClick = {
                    val lines = text.text.trim().lines()
                    val nb = data.settings.defaultNotebookId?.takeIf { id -> data.notebooks.any { it.id == id } } ?: data.notebooks.sortedBy { it.createdAt }.first().id
                    val note = Note(title = lines.first().take(80), body = lines.drop(1).joinToString("\n").trim(), notebookId = nb)
                    Store.addNotes(listOf(note))
                    text = TextFieldValue("")
                    Store.updateSettings { it.copy(scratchPad = "") }
                    actions.message("Saved as a note")
                    nav.open(note)
                }) { Text("Save as note") }
            }
        }
    }
}

/** Notes as cards in a row you can scroll sideways. */
@Composable
private fun NoteStrip(notes: List<Note>) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(notes, key = { it.id }) { n ->
            val shape = RoundedCornerShape(14.dp)
            Column(
                Modifier
                    .width(156.dp)
                    .height(176.dp)
                    .clip(shape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable { nav.open(n) },
            ) {
                val image = n.attachments.firstOrNull { it.isImage }?.let { rememberThumbnail(it, 320) }
                if (image != null) Image(image.asImageBitmap(), null, Modifier.fillMaxWidth().height(72.dp), contentScale = ContentScale.Crop)
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n.title.ifBlank { "Untitled" }, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (n.pinned) Icon(Icons.Rounded.PushPin, "Pinned", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        Markdown.plain(n.body, 160),
                        Modifier.padding(top = 4.dp).weight(1f, fill = false),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (image != null) 2 else 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(Dates.shortDate(n.updatedAt, today), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun ReminderRow(n: Note) {
    val nav = LocalNav.current
    val clock = LocalClock.current
    val at = n.reminderAt ?: return
    val done = n.reminderDone != 0L
    val overdue = !done && at < System.currentTimeMillis()
    Row(Modifier.fillMaxWidth().clickable { nav.open(n) }.padding(start = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = done, onCheckedChange = { Store.reminderDone(n.id, it) }, modifier = Modifier.semantics { contentDescription = if (done) "Mark ${n.title} not done" else "Mark ${n.title} done" })
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(n.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textDecoration = if (done) TextDecoration.LineThrough else null)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Alarm, null, Modifier.size(13.dp), tint = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    " " + Dates.reminder(at, clock.today, clock.is24Hour),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** An unticked checklist item from a note: tick it here, or tap to open the note. */
@Composable
private fun CheckRow(n: Note, c: Block.Check) {
    val nav = LocalNav.current
    Row(Modifier.fillMaxWidth().clickable { nav.open(n) }.padding(start = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = false, onCheckedChange = { Store.toggleCheck(n.id, c.line) }, modifier = Modifier.semantics { contentDescription = "Tick ${c.text}" })
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(styled(Markdown.inline(c.text)), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(n.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
