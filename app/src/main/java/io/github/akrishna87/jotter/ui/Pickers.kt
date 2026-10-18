package io.github.akrishna87.jotter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NextWeek
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.rounded.Book
import io.github.akrishna87.jotter.Store
import io.github.akrishna87.jotter.model.Data
import io.github.akrishna87.jotter.model.Dates
import io.github.akrishna87.jotter.model.Note
import io.github.akrishna87.jotter.model.SavedSearch
import io.github.akrishna87.jotter.model.Template
import io.github.akrishna87.jotter.model.Templates
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Picks tags (marked "#"): tick existing ones, or type a new one. */
@Composable
fun LabelsDialogFor(
    mark: String,
    title: String,
    newHint: String,
    all: List<String>,
    selected: List<String>,
    onDone: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf(selected) }
    var known by remember { mutableStateOf((all + selected).distinctBy { it.lowercase() }) }
    var draft by remember { mutableStateOf("") }
    fun addDraft() {
        val l = draft.trim().removePrefix(mark).replace(" ", "-")
        if (l.isEmpty()) return
        if (known.none { it.equals(l, true) }) known = known + l
        if (chosen.none { it.equals(l, true) }) chosen = chosen + l
        draft = ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(newHint) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addDraft() }),
                    trailingIcon = { TextButton(onClick = ::addDraft, enabled = draft.isNotBlank()) { Text("Add") } },
                )
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                    if (known.isEmpty()) {
                        Text("None yet. Type one above.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    for (l in known) {
                        val on = chosen.any { it.equals(l, true) }
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Checkbox) {
                                chosen = if (on) chosen.filterNot { it.equals(l, true) } else chosen + l
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = null)
                            Text("$mark$l", Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { addDraft(); onDone(chosen); onDismiss() }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A name in a box, for new and renamed notebooks. */
@Composable
fun NameDialog(
    title: String,
    initial: String = "",
    label: String = "Name",
    confirm: String = "Save",
    colors: List<Int>? = null,
    initialColor: Int = 0,
    onSave: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    var color by remember { mutableIntStateOf(if (initialColor != 0) initialColor else colors?.getOrNull(6) ?: 0) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    label = { Text(label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) { onSave(name.trim(), color); onDismiss() } }),
                )
                if (colors != null) {
                    ColorChoices(colors, color, { color = it }, Modifier.padding(top = 16.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), color); onDismiss() }, enabled = name.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorChoices(colors: List<Int>, selected: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (c in colors) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.RadioButton, onClickLabel = "Choose colour") { onPick(c) }
                    .padding(0.dp),
                contentAlignment = Alignment.Center,
            ) {
                ColorDot(c, 32.dp)
                if (c == selected) Icon(Icons.Rounded.Check, "Selected", Modifier.size(18.dp), tint = Color.White)
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(initial: LocalTime, is24Hour: Boolean, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = is24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}

/** Picks a notebook, shown under their stacks. */
@Composable
fun NotebookPickerDialog(data: Data, current: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to notebook") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                @Composable
                fun row(nb: io.github.akrishna87.jotter.model.Notebook, indent: Boolean) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(nb.id); onDismiss() }.padding(start = if (indent) 20.dp else 0.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Book, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(nb.name, Modifier.padding(start = 10.dp).weight(1f))
                        if (nb.id == current) Icon(Icons.Rounded.Check, "Current", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                for (stack in data.stacks) {
                    Text(stack, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    for (nb in data.notebooksIn(stack)) row(nb, true)
                }
                for (nb in data.notebooksIn(null)) row(nb, false)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Names a search to keep, or changes a kept one. */
@Composable
fun SaveSearchDialog(existing: SavedSearch?, query: String = existing?.query.orEmpty(), onSaved: (SavedSearch) -> Unit = {}, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: query.take(40)) }
    var q by remember { mutableStateOf(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Save search" else "Edit search") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), label = { Text("Search") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = (existing ?: SavedSearch(name = name.trim(), query = q.trim())).copy(name = name.trim(), query = q.trim())
                Store.saveSearch(s)
                onSaved(s)
                onDismiss()
            }, enabled = name.isNotBlank() && q.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** When to be reminded: later today, tomorrow, next week, or a date and time. Also marks it done or clears it. */
@Composable
fun ReminderDialog(current: Long?, done: Boolean, onPick: (Long?) -> Unit, onDone: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val clock = LocalClock.current
    val zone = ZoneId.systemDefault()
    var pickingDate by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    fun at(d: LocalDate, t: LocalTime) = d.atTime(t).atZone(zone).toInstant().toEpochMilli()
    val now = clock.now
    val laterToday = now.plusHours(3).withMinute(0).withSecond(0).withNano(0).takeIf { it.toLocalDate() == now.toLocalDate() }
    val nextMonday = clock.today.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY))
    val fmt = { millis: Long -> Dates.reminder(millis, clock.today, clock.is24Hour) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminder") },
        text = {
            Column {
                if (current != null) {
                    Text(
                        (if (done) "Done · " else "") + fmt(current),
                        Modifier.padding(bottom = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                @Composable
                fun choice(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, detail: String, onClick: () -> Unit) {
                    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(label, Modifier.padding(start = 12.dp).weight(1f))
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (laterToday != null) choice(Icons.Rounded.LightMode, "Later today", Dates.time(laterToday.toLocalTime(), clock.is24Hour)) {
                    onPick(laterToday.atZone(zone).toInstant().toEpochMilli()); onDismiss()
                }
                choice(Icons.Rounded.Today, "Tomorrow", Dates.time(LocalTime.of(9, 0), clock.is24Hour)) { onPick(at(clock.today.plusDays(1), LocalTime.of(9, 0))); onDismiss() }
                choice(Icons.Rounded.NextWeek, "Next week", nextMonday.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()) + " " + Dates.time(LocalTime.of(9, 0), clock.is24Hour)) {
                    onPick(at(nextMonday, LocalTime.of(9, 0))); onDismiss()
                }
                choice(Icons.Rounded.CalendarMonth, "Pick a date and time", "") { pickingDate = true }
                if (current != null) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    choice(Icons.Rounded.Check, if (done) "Mark as not done" else "Mark as done", "") { onDone(!done); onDismiss() }
                    choice(Icons.Rounded.Block, "Remove reminder", "") { onPick(null); onDismiss() }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickingDate) DatePickDialog(clock.today, onPick = { date = it }, onDismiss = { pickingDate = false })
    date?.let { d ->
        TimePickDialog(LocalTime.of(9, 0), clock.is24Hour, onPick = { t -> onPick(at(d, t)); onDismiss() }, onDismiss = { date = null })
    }
}

/** A web link: the words to show and where it goes. */
@Composable
fun LinkDialog(initialText: String, onInsert: (String, String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initialText) }
    var url by remember { mutableStateOf("https://") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("Text") }, singleLine = true)
                OutlinedTextField(
                    url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Web address") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onInsert(text.trim().ifEmpty { url.trim() }, url.trim()); onDismiss() }, enabled = url.trim().length > 8 && !url.contains(' ')) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Finds a note to link to. */
@Composable
fun NoteLinkDialog(data: Data, exclude: String?, onPick: (Note) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val notes = data.liveNotes.filter { it.id != exclude && (q.isBlank() || it.title.contains(q, true)) }.sortedByDescending { it.updatedAt }.take(50)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link to a note") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Find a note") }, singleLine = true)
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                    if (notes.isEmpty()) Text("No notes found", Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (n in notes) {
                        Text(
                            n.title.ifBlank { "Untitled" },
                            Modifier.fillMaxWidth().clickable { onPick(n); onDismiss() }.padding(vertical = 10.dp),
                            maxLines = 1,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A table's size. */
@Composable
fun TableDialog(onInsert: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var cols by remember { mutableIntStateOf(3) }
    var rows by remember { mutableIntStateOf(3) }
    @Composable
    fun stepper(label: String, value: Int, set: (Int) -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            TextButton(onClick = { set((value - 1).coerceAtLeast(1)) }, Modifier.semantics { contentDescription = "Fewer $label" }) { Text("−") }
            Text(value.toString(), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { set((value + 1).coerceAtMost(8)) }, Modifier.semantics { contentDescription = "More $label" }) { Text("+") }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert table") },
        text = { Column { stepper("Columns", cols) { cols = it }; stepper("Rows", rows) { rows = it } } },
        confirmButton = { TextButton(onClick = { onInsert(cols, rows); onDismiss() }) { Text("Insert") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Where a tag sits: at the top, or under another tag. */
@Composable
fun TagParentDialog(data: Data, tag: String, onDismiss: () -> Unit) {
    val own = data.tagAndChildren(tag)
    val choices = data.tags.filter { it.lowercase() !in own }
    val current = data.parentOf(tag)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Put #$tag under") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                @Composable
                fun row(label: String, parent: String?) {
                    Row(Modifier.fillMaxWidth().clickable { Store.setTagParent(tag, parent); onDismiss() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f))
                        if (current.equals(parent, true)) Icon(Icons.Rounded.Check, "Current", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                row("No parent (top level)", null)
                for (t in choices) row("#$t", t)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The built-in templates and your own, to start a note from. */
@Composable
fun TemplatePickerDialog(data: Data, onPick: (Template) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Use a template") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Its text is copied into this note. The template itself stays as it is.",
                    Modifier.padding(bottom = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (data.templates.isNotEmpty()) {
                    Text("Yours", Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    for (t in data.templates) Text(t.name, Modifier.fillMaxWidth().clickable { onPick(t); onDismiss() }.padding(vertical = 10.dp))
                    Text("Jotter's", Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                for (t in Templates.builtIn) Text(t.name, Modifier.fillMaxWidth().clickable { onPick(t); onDismiss() }.padding(vertical = 10.dp))
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
