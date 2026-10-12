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
