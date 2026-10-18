package io.github.akrishna87.jotter.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.akrishna87.jotter.Backup
import io.github.akrishna87.jotter.Exports
import io.github.akrishna87.jotter.Lock
import io.github.akrishna87.jotter.Store
import io.github.akrishna87.jotter.model.Data
import io.github.akrishna87.jotter.model.HomeSection
import io.github.akrishna87.jotter.model.ListStyle
import io.github.akrishna87.jotter.model.Settings
import io.github.akrishna87.jotter.model.StartPage
import io.github.akrishna87.jotter.model.TextSize
import io.github.akrishna87.jotter.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import io.github.akrishna87.jotter.model.SpeechQuality
import io.github.akrishna87.jotter.model.SpeechLanguages
import io.github.akrishna87.jotter.Models
import io.github.akrishna87.jotter.SummaryModels
import io.github.akrishna87.jotter.model.SummaryModel
import io.github.akrishna87.jotter.Files

/** Look and feel, Home, the passcode lock, importing and exporting, backups and the version. */
@Composable
fun SettingsPage(data: Data) {
    val nav = LocalNav.current
    val actions = LocalActions.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = data.settings
    var restoring by remember { mutableStateOf<Data?>(null) }
    var settingCode by remember { mutableStateOf(false) }
    var removingCode by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) actions.message(if (Backup.export(context, uri)) "Backup saved" else "Couldn't save the backup")
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val d = Backup.read(context, uri)
            if (d == null) actions.message("That file isn't a Jotter backup") else restoring = d
        }
    }
    val enexOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/enex+xml")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { Exports.writeEnex(context, uri, Store.data.value.liveNotes) }
            actions.message(if (ok) "Notes exported for Evernote" else "Couldn't save the file")
        }
    }
    val htmlOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { Exports.writeHtml(context, uri, Store.data.value.liveNotes) }
            actions.message(if (ok) "Notes exported as a web page" else "Couldn't save the file")
        }
    }
    val notesIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val nb = data.settings.defaultNotebookId?.takeIf { id -> data.notebooks.any { it.id == id } } ?: data.notebooks.sortedBy { it.createdAt }.first().id
            val notes = withContext(Dispatchers.IO) { uris.flatMap { Exports.read(context, it, nb) } }
            Store.addNotes(notes)
            actions.message(if (notes.isEmpty()) "No notes found in that file" else if (notes.size == 1) "1 note imported" else "${notes.size} notes imported")
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageBar("Settings", onBack = { nav.pop() })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { SectionHeader("Theme") }
            card {
                ThemeMode.entries.forEach { mode -> row {
                    ListRow(
                        mode.label,
                        leading = { RadioButton(selected = s.theme == mode, onClick = null) },
                        onClick = { Store.updateSettings { it.copy(theme = mode) } },
                        modifier = Modifier.semantics { contentDescription = "Theme: ${mode.label}" },
                    )
                } }
            }
            item { SectionHeader("Notes") }
            card {
                row { ChoiceRow("Text size", s.textSize.label, TextSize.entries.map { it.label }) { i -> Store.updateSettings { it.copy(textSize = TextSize.entries[i]) } } }
                row { ChoiceRow("Show notes as", s.listStyle.label, ListStyle.entries.map { it.label }) { i -> Store.updateSettings { it.copy(listStyle = ListStyle.entries[i]) } } }
                row { ChoiceRow("Open on", s.startPage.label, StartPage.entries.map { it.label }) { i -> Store.updateSettings { it.copy(startPage = StartPage.entries[i]) } } }
                row {
                    val nbs = data.notebooks.sortedBy { it.createdAt }
                    val current = nbs.firstOrNull { it.id == s.defaultNotebookId } ?: nbs.first()
                    ChoiceRow("Default notebook", current.name, nbs.map { it.name }) { i -> Store.updateSettings { it.copy(defaultNotebookId = nbs[i].id) } }
                }
            }
            item { SectionHeader("Home") }
            card {
                HomeSection.entries.forEach { section -> row {
                    val on = section in s.homeSections
                    ListRow(
                        section.label,
                        leading = { Checkbox(checked = on, onCheckedChange = null) },
                        onClick = {
                            Store.updateSettings { st ->
                                st.copy(homeSections = if (on) st.homeSections - section else HomeSection.entries.filter { it in st.homeSections || it == section })
                            }
                        },
                    )
                } }
            }
            item { SectionHeader("Recordings to text") }
            card {
                row {
                    val qs = SpeechQuality.entries
                    ChoiceRow("Speech model", "${s.speechQuality.label} · about ${s.speechQuality.sizeMb} MB", qs.map { "${it.label} · about ${it.sizeMb} MB" }) { i -> Store.updateSettings { it.copy(speechQuality = qs[i]) } }
                }
                row {
                    val current = SpeechLanguages.firstOrNull { it.first == s.speechLanguage }?.second ?: s.speechLanguage
                    ChoiceRow("Spoken language", current, SpeechLanguages.map { it.second }) { i -> Store.updateSettings { it.copy(speechLanguage = SpeechLanguages[i].first) } }
                }
                row {
                    ListRow(
                        "Write in English",
                        detail = "Translate recordings in other languages",
                        trailing = { Switch(checked = s.speechTranslate, onCheckedChange = { on -> Store.updateSettings { it.copy(speechTranslate = on) } }) },
                        onClick = { Store.updateSettings { it.copy(speechTranslate = !it.speechTranslate) } },
                    )
                }
                row {
                    val recommended = remember { SummaryModels.recommended(context) }
                    val options = listOf<SummaryModel?>(null) + SummaryModel.entries
                    fun name(m: SummaryModel?) = if (m == null) "Automatic (${recommended.label} for this phone)" else "${m.label} · about ${m.sizeLabel}"
                    ChoiceRow("Smart summary model", name(s.summaryModel), options.map(::name)) { i -> Store.updateSettings { it.copy(summaryModel = options[i]) } }
                }
                row {
                    var bytes by remember { mutableStateOf(Models.downloadedBytes(context)) }
                    ListRow(
                        "Downloaded models",
                        detail = if (bytes > 0) "${Files.sizeLabel(bytes)} · tap to remove" else "None yet. One is downloaded the first time you transcribe.",
                        onClick = if (bytes > 0) ({ Models.deleteAll(context); bytes = 0L }) else null,
                    )
                }
            }
            item { SectionHeader("Passcode lock") }
            card {
                row {
                    ListRow(
                        if (s.passcode == null) "Lock with a passcode" else "Passcode lock is on",
                        detail = if (s.passcode == null) "Ask for a code when Jotter opens" else "Change or turn off",
                        leading = { Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailing = { Switch(checked = s.passcode != null, onCheckedChange = { on -> if (on) settingCode = true else removingCode = true }) },
                        onClick = { if (s.passcode == null) settingCode = true else removingCode = true },
                    )
                }
                if (s.passcode != null) {
                    row { ListRow("Change passcode", onClick = { settingCode = true }) }
                    row {
                        val choices = listOf(0, 1, 5, 15, 60)
                        ChoiceRow("Lock again after", lockLabel(s.lockAfterMinutes), choices.map(::lockLabel)) { i -> Store.updateSettings { it.copy(lockAfterMinutes = choices[i]) } }
                    }
                    if (Lock.canUseFingerprint(context)) {
                        row {
                            ListRow(
                                "Unlock with fingerprint",
                                leading = { Icon(Icons.Rounded.Fingerprint, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                trailing = { Switch(checked = s.fingerprint, onCheckedChange = { on -> Store.updateSettings { it.copy(fingerprint = on) } }) },
                                onClick = { Store.updateSettings { it.copy(fingerprint = !it.fingerprint) } },
                            )
                        }
                    }
                }
            }
            item { SectionHeader("Import and export") }
            card {
                row {
                    ListRow(
                        "Import notes",
                        detail = "From Evernote (.enex), Markdown or text files",
                        onClick = { notesIn.launch(arrayOf("application/enex+xml", "application/xml", "text/xml", "text/plain", "text/markdown", "application/octet-stream")) },
                    )
                }
                row { ListRow("Export all for Evernote", detail = "Every note with its attachments, as an .enex file", onClick = { enexOut.launch("Jotter notes.enex") }) }
                row { ListRow("Export all as a web page", detail = "One HTML file with every note", onClick = { htmlOut.launch("Jotter notes.html") }) }
                row {
                    ListRow("Print or save all as PDF", onClick = { (context as? Activity)?.let { Exports.print(it, data.liveNotes) } })
                }
            }
            item { SectionHeader("Backup") }
            card {
                row {
                    ListRow(
                        "Export backup",
                        detail = "Every note, notebook and setting, in one file (without attachments)",
                        onClick = { exporter.launch("jotter-backup-${LocalDate.now()}.json") },
                    )
                }
                row {
                    ListRow(
                        "Restore from backup",
                        detail = "Replace everything with a backup file",
                        onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    )
                }
            }
            item { SectionHeader("About") }
            card {
                row {
                    val version = remember {
                        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
                    }
                    ListRow("Jotter", detail = "Version $version · Your notes, on your phone only")
                }
            }
        }
    }
    restoring?.let { backup ->
        ConfirmDialog(
            "Restore this backup?",
            "It has ${backup.notes.size} notes in ${backup.notebooks.size} notebooks. Everything in Jotter now is replaced.",
            "Restore",
            onConfirm = {
                // Keep the passcode as it is now, so a backup can't lock you out or unlock Jotter.
                Store.replaceAll(backup.copy(settings = backup.settings.copy(passcode = Store.data.value.settings.passcode)))
                actions.message("Backup restored")
            },
            onDismiss = { restoring = null },
        )
    }
    if (settingCode) PasscodeDialog(onDone = { code ->
        Store.updateSettings { it.copy(passcode = Lock.hash(code)) }
        Lock.unlocked = true
        actions.message("Passcode set")
    }, onDismiss = { settingCode = false })
    if (removingCode) {
        ConfirmDialog("Turn off the passcode?", "Anyone with your phone can open Jotter.", "Turn off", onConfirm = { Store.updateSettings { it.copy(passcode = null) } }, onDismiss = { removingCode = false })
    }
}

private fun lockLabel(m: Int) = when (m) {
    0 -> "Right away"
    1 -> "1 minute"
    60 -> "1 hour"
    else -> "$m minutes"
}

@Composable
private fun ChoiceRow(label: String, value: String, choices: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ListRow(label, detail = value, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEachIndexed { i, c -> DropdownMenuItem(text = { Text(c) }, onClick = { open = false; onPick(i) }) }
        }
    }
}

/** A new passcode, typed twice. */
@Composable
private fun PasscodeDialog(onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val ok = code.length >= 4 && code == again
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set a passcode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("At least 4 digits. If you forget it, the only way back in is to clear Jotter's data.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    code, { v -> code = v.filter(Char::isDigit).take(12) }, Modifier.fillMaxWidth().semantics { contentDescription = "New passcode" },
                    label = { Text("Passcode") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                OutlinedTextField(
                    again, { v -> again = v.filter(Char::isDigit).take(12) }, Modifier.fillMaxWidth().semantics { contentDescription = "Passcode again" },
                    label = { Text("Again") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = again.isNotEmpty() && again.length >= code.length && again != code,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onDone(code); onDismiss() }, enabled = ok) { Text("Set passcode") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Covers everything until the passcode (or a fingerprint) is given. */
@Composable
fun LockScreen(settings: Settings, onUnlock: () -> Unit) {
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val canFinger = settings.fingerprint && Lock.canUseFingerprint(context)
    LaunchedEffect(Unit) { if (canFinger) Lock.fingerprint(context, onUnlock) }
    fun press(d: String) {
        wrong = false
        code = (code + d).take(12)
        if (code.length >= 4 && Lock.matches(code, settings.passcode)) onUnlock()
    }
    androidx.activity.compose.BackHandler { (context as? Activity)?.moveTaskToBack(true) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Jotter is locked", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleLarge)
            Text(if (wrong) "That's not it. Try again." else "Enter your passcode", Modifier.padding(top = 4.dp), color = if (wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(vertical = 24.dp).height(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(maxOf(4, code.length)) { i ->
                    Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < code.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                }
            }
            for (row in listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("finger", "0", "back"))) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    for (k in row) {
                        Box(
                            Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .then(if (k.length == 1) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh) else Modifier)
                                .clickable(enabled = k != "finger" || canFinger) {
                                    when (k) {
                                        "back" -> code = code.dropLast(1)
                                        "finger" -> Lock.fingerprint(context, onUnlock)
                                        else -> press(k)
                                    }
                                }
                                .semantics { contentDescription = when (k) { "back" -> "Delete digit"; "finger" -> "Use fingerprint"; else -> k } },
                            contentAlignment = Alignment.Center,
                        ) {
                            when (k) {
                                "back" -> Icon(Icons.AutoMirrored.Rounded.Backspace, null)
                                "finger" -> if (canFinger) Icon(Icons.Rounded.Fingerprint, null, tint = MaterialTheme.colorScheme.primary)
                                else -> Text(k, style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                }
            }
            TextButton(onClick = { if (!Lock.matches(code, settings.passcode)) { wrong = true; code = "" } else onUnlock() }, enabled = code.length >= 4) { Text("Unlock") }
            Spacer(Modifier.weight(1f))
        }
    }
}
