package io.github.akrishna87.jotter.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.rounded.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StrikethroughS
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.jotter.Exports
import io.github.akrishna87.jotter.Files
import io.github.akrishna87.jotter.Reminders
import io.github.akrishna87.jotter.Store
import io.github.akrishna87.jotter.model.Attachment
import io.github.akrishna87.jotter.model.Data
import io.github.akrishna87.jotter.model.Dates
import io.github.akrishna87.jotter.model.Links
import io.github.akrishna87.jotter.model.Markdown
import io.github.akrishna87.jotter.model.Note
import io.github.akrishna87.jotter.model.Shortcut
import io.github.akrishna87.jotter.model.ShortcutKind
import io.github.akrishna87.jotter.model.Template
import kotlinx.coroutines.delay
import androidx.compose.material3.FilledIconButton
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera

/**
 * A note, Evernote style. It opens ready to read, with checklists you can tick and attachments you
 * can open; tap the text (or the pencil) to edit, with a toolbar for formatting, tables, links and
 * attachments. It saves as you type, keeping earlier versions in its history.
 */
@Composable
fun NoteEditorPage(page: Page.NotePage, data: Data) {
    val nav = LocalNav.current
    val actions = LocalActions.current
    val clock = LocalClock.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val id = page.id
    val start = data.note(id) ?: page.draft
    if (start == null) {
        LaunchedEffect(Unit) { nav.pop() }
        return
    }
    var title by remember(id) { mutableStateOf(TextFieldValue(start.title)) }
    var body by remember(id) { mutableStateOf(TextFieldValue(start.body, TextRange(start.body.length))) }
    var notebookId by remember(id) { mutableStateOf(start.notebookId) }
    var tags by remember(id) { mutableStateOf(start.tags) }
    var pinned by remember(id) { mutableStateOf(start.pinned) }
    var attachments by remember(id) { mutableStateOf(start.attachments) }
    var reminderAt by remember(id) { mutableStateOf(start.reminderAt) }
    var reminderDone by remember(id) { mutableStateOf(start.reminderDone) }
    var editing by remember(id) { mutableStateOf(start.isBlank || page.start == "checklist") }
    var menu by remember { mutableStateOf(false) }
    var exportMenu by remember { mutableStateOf(false) }
    var notebookPicker by remember { mutableStateOf(false) }
    var tagDialog by remember { mutableStateOf(false) }
    var trashing by remember { mutableStateOf(false) }
    var reminderDialog by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    var savingTemplate by remember { mutableStateOf(false) }
    val bodyFocus = remember { FocusRequester() }
    val titleFocus = remember { FocusRequester() }
    val styles = noteStyles(data.settings.textSize.scale)

    // Changes made elsewhere (a box ticked on Home, a reminder done from its notification) show up here.
    val stored = data.note(id)
    LaunchedEffect(stored?.body, stored?.reminderDone, stored?.reminderAt) {
        if (stored != null && !editing && stored.body != body.text) body = TextFieldValue(stored.body)
        if (stored != null) { reminderDone = stored.reminderDone; reminderAt = stored.reminderAt }
    }

    fun current(): Note? {
        val base = Store.data.value.note(id) ?: page.draft ?: return null
        return base.copy(
            title = title.text.trim(), body = body.text.trimEnd(), notebookId = notebookId, tags = tags, pinned = pinned,
            attachments = attachments, reminderAt = reminderAt, reminderDone = reminderDone,
        )
    }
    fun save() {
        val base = Store.data.value.note(id) ?: page.draft ?: return
        if (base.trashed) return
        val next = current() ?: return
        if (next != base || Store.data.value.note(id) == null) Store.saveNote(next.copy(updatedAt = System.currentTimeMillis()))
    }
    LaunchedEffect(title.text, body.text, notebookId, tags, pinned, attachments, reminderAt, reminderDone) {
        delay(700)
        save()
    }
    DisposableEffect(id) { onDispose { save() } }

    fun insertBlock(text: String) {
        val (t, c) = Markdown.insertBlock(body.text, if (editing) body.selection.end else body.text.length, text)
        body = TextFieldValue(t, TextRange(c))
    }
    val attacher = rememberAttacher { atts ->
        attachments = attachments + atts
        insertBlock(atts.joinToString("\n") { Markdown.mediaLine(it) })
        save()
    }
    LaunchedEffect(id) {
        when {
            page.start == "checklist" -> runCatching { bodyFocus.requestFocus() }
            page.start != null -> attacher.start(page.start)
            start.isBlank -> runCatching { titleFocus.requestFocus() }
        }
    }
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun setReminder(at: Long?) {
        reminderAt = at
        reminderDone = 0L
        if (at != null && Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(context)) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (at != null) actions.message("Reminder set for ${Dates.reminder(at, clock.today, clock.is24Hour)}")
    }
    fun removeAttachment(a: Attachment) {
        attachments = attachments.filter { it.id != a.id }
        body = TextFieldValue(body.text.lines().filterNot { it.trim().startsWith("![") && it.trim().endsWith("(attachment:${a.id})") }.joinToString("\n"))
        save()
        Files.delete(context, a)
    }
    val htmlOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        if (uri != null) current()?.let { actions.message(if (Exports.writeHtml(context, uri, listOf(it))) "Saved as HTML" else "Couldn't save the file") }
    }
    val mdOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) current()?.let { actions.message(if (Exports.writeMarkdown(context, uri, it)) "Saved as Markdown" else "Couldn't save the file") }
    }
    val enexOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/enex+xml")) { uri ->
        if (uri != null) current()?.let { actions.message(if (Exports.writeEnex(context, uri, listOf(it))) "Saved for Evernote" else "Couldn't save the file") }
    }

    val note = data.note(id)
    val shortcut = Shortcut(ShortcutKind.NOTE, id)
    val backlinks = if (note != null) data.backlinks(id) else emptyList()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageBar(onBack = { nav.pop() }) {
            if (editing) {
                FilledIconButton(onClick = { editing = false; save() }, Modifier.semantics { contentDescription = "Done editing" }) { Icon(Icons.Rounded.Check, null) }
            } else {
                IconButton(onClick = { attacher.audio() }, Modifier.semantics { contentDescription = "Record audio" }) { Icon(Icons.Rounded.Mic, null) }
                IconButton(onClick = { editing = true }, Modifier.semantics { contentDescription = "Edit note" }) { Icon(Icons.Rounded.Edit, null) }
            }
            IconButton(onClick = { reminderDialog = true }, Modifier.semantics { contentDescription = if (reminderAt != null) "Change reminder" else "Add reminder" }) {
                Icon(if (reminderAt != null && reminderDone == 0L) Icons.Rounded.Alarm else Icons.Outlined.Alarm, null, tint = if (reminderAt != null && reminderDone == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { pinned = !pinned }, Modifier.semantics { contentDescription = if (pinned) "Unpin note" else "Pin note" }) {
                Icon(if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin, null, tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            Box {
                IconButton(onClick = { menu = true }, Modifier.semantics { contentDescription = "Note options" }) { Icon(Icons.Rounded.MoreVert, null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit tags") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Label, null) }, onClick = { menu = false; tagDialog = true })
                    DropdownMenuItem(text = { Text("Move to notebook") }, leadingIcon = { Icon(Icons.Rounded.Book, null) }, onClick = { menu = false; notebookPicker = true })
                    val isShortcut = data.isShortcut(shortcut)
                    DropdownMenuItem(
                        text = { Text(if (isShortcut) "Remove from shortcuts" else "Add to shortcuts") },
                        leadingIcon = { Icon(if (isShortcut) Icons.Rounded.Star else Icons.Outlined.StarOutline, null) },
                        onClick = { menu = false; save(); Store.toggleShortcut(shortcut) },
                    )
                    DropdownMenuItem(text = { Text("Copy note link") }, leadingIcon = { Icon(Icons.Rounded.Link, null) }, onClick = {
                        menu = false
                        save()
                        current()?.let { clipboard.setText(AnnotatedString(Links.to(it))) }
                        actions.message("Link copied. Paste it into another note.")
                    })
                    DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = {
                        menu = false
                        save()
                        Store.duplicate(id)?.let { copy -> nav.pop(); nav.open(copy) }
                    })
                    DropdownMenuItem(text = { Text("Save as template") }, leadingIcon = { Icon(Icons.Rounded.NoteAdd, null) }, onClick = { menu = false; savingTemplate = true })
                    DropdownMenuItem(text = { Text("Note info") }, leadingIcon = { Icon(Icons.Rounded.Info, null) }, onClick = { menu = false; save(); info = true })
                    if (note != null && note.history.isNotEmpty()) {
                        DropdownMenuItem(text = { Text("Note history") }, leadingIcon = { Icon(Icons.Rounded.History, null) }, onClick = { menu = false; save(); nav.push(Page.History(id)) })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = { menu = false; current()?.let { Exports.shareText(context, it) } })
                    DropdownMenuItem(text = { Text("Print or save as PDF") }, leadingIcon = { Icon(Icons.Rounded.Print, null) }, onClick = {
                        menu = false
                        current()?.let { n -> (context as? Activity)?.let { Exports.print(it, listOf(n)) } }
                    })
                    DropdownMenuItem(text = { Text("Export…") }, leadingIcon = { Icon(Icons.Rounded.FileDownload, null) }, onClick = { menu = false; exportMenu = true })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Move to trash", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menu = false; trashing = true })
                }
                DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                    val n = current()
                    DropdownMenuItem(text = { Text("HTML web page") }, onClick = { exportMenu = false; htmlOut.launch(Exports.fileName(n, ".html")) })
                    DropdownMenuItem(text = { Text("Markdown") }, onClick = { exportMenu = false; mdOut.launch(Exports.fileName(n, ".md")) })
                    DropdownMenuItem(text = { Text("Evernote (.enex)") }, onClick = { exportMenu = false; enexOut.launch(Exports.fileName(n, ".enex")) })
                }
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            // Notebook, reminder and tags.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MetaPill(Icons.Rounded.Book, data.notebook(notebookId).name, "Notebook: ${data.notebook(notebookId).name}") { notebookPicker = true }
                reminderAt?.let { at ->
                    MetaPill(Icons.Rounded.Alarm, (if (reminderDone != 0L) "✓ " else "") + Dates.reminder(at, clock.today, clock.is24Hour), "Reminder") { reminderDialog = true }
                }
                for (t in tags) MetaPill(null, "#$t") { tagDialog = true }
                MetaPill(Icons.Rounded.Add, if (tags.isEmpty()) "Tag" else "", "Add tag") { tagDialog = true }
            }
            PlainField(
                value = title,
                onChange = { title = it },
                placeholder = "Title",
                description = "Title",
                style = styles.h1,
                modifier = Modifier.padding(top = 16.dp).focusRequester(titleFocus),
            )
            Text(
                (if (note != null) "Edited ${Dates.shortDate(note.updatedAt, clock.today)}" else "New note"),
                Modifier.padding(top = 4.dp, bottom = 12.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (editing) {
                PlainField(
                    value = body,
                    onChange = { new ->
                        val continued = if (new.selection.collapsed) Markdown.continueList(body.text, new.text, new.selection.start) else null
                        body = if (continued != null) TextFieldValue(continued.first, TextRange(continued.second)) else new
                    },
                    placeholder = "Start writing, or use the toolbar below",
                    description = "Note text",
                    style = styles.body,
                    modifier = Modifier.defaultMinSize(minHeight = 240.dp).focusRequester(bodyFocus),
                )
            } else {
                MarkdownView(
                    body.text,
                    attachments,
                    styles,
                    onToggle = { line -> body = TextFieldValue(Markdown.toggleCheck(body.text, line)) },
                    onEdit = { editing = true },
                    onNoteLink = { target ->
                        val linked = data.linked(target)
                        if (linked != null) { save(); nav.open(linked) } else actions.message("That note isn't here any more")
                    },
                    onRemoveAttachment = ::removeAttachment,
                    noteId = id,
                    onInsertText = { text -> insertBlock(text); save() },
                )
            }

            if (start.source.isNotBlank()) {
                Text(
                    styled(Markdown.inline("From " + start.source), marks()),
                    Modifier.padding(top = 20.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (backlinks.isNotEmpty()) {
                Text("Linked from", Modifier.padding(top = 24.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                for (b in backlinks) {
                    Row(
                        Modifier.fillMaxWidth().clickable { save(); nav.open(b) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Description, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(b.title.ifBlank { "Untitled" }, Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        if (editing) {
            FormatBar(
                Modifier.navigationBarsPadding().imePadding(),
                data = data,
                noteId = id,
                attacher = attacher,
                selectedText = body.text.substring(body.selection.min.coerceIn(0, body.text.length), body.selection.max.coerceIn(0, body.text.length)),
                onPrefix = { prefix ->
                    val (text, cursor) = Markdown.toggleLinePrefix(body.text, body.selection.start, prefix)
                    body = TextFieldValue(text, TextRange(cursor))
                    runCatching { bodyFocus.requestFocus() }
                },
                onIndent = { by ->
                    val (text, cursor) = Markdown.indent(body.text, body.selection.start, by)
                    body = TextFieldValue(text, TextRange(cursor))
                    runCatching { bodyFocus.requestFocus() }
                },
                onWrap = { open, close ->
                    val (text, s, e) = Markdown.wrap(body.text, body.selection.min, body.selection.max, open, close)
                    body = TextFieldValue(text, TextRange(s, e))
                    runCatching { bodyFocus.requestFocus() }
                },
                onInsert = { inline ->
                    val s = body.selection.min.coerceIn(0, body.text.length)
                    val e = body.selection.max.coerceIn(0, body.text.length)
                    body = TextFieldValue(body.text.substring(0, s) + inline + body.text.substring(e), TextRange(s + inline.length))
                    runCatching { bodyFocus.requestFocus() }
                },
                onBlock = { block ->
                    insertBlock(block)
                    runCatching { bodyFocus.requestFocus() }
                },
            )
        } else {
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (tagDialog) {
        LabelsDialogFor(mark = "#", title = "Tags", newHint = "New tag", all = data.tags, selected = tags, onDone = { tags = it }, onDismiss = { tagDialog = false })
    }
    if (notebookPicker) NotebookPickerDialog(data, notebookId, onPick = { notebookId = it }, onDismiss = { notebookPicker = false })
    if (reminderDialog) {
        ReminderDialog(reminderAt, reminderDone != 0L, onPick = ::setReminder, onDone = { d -> reminderDone = if (d) System.currentTimeMillis() else 0L }, onDismiss = { reminderDialog = false })
    }
    if (savingTemplate) {
        NameDialog("Save as template", initial = title.text.trim(), confirm = "Save", onSave = { name, _ ->
            Store.saveTemplate(Template(name = name, title = title.text.trim(), body = body.text.trimEnd()))
            actions.message("Template saved")
        }, onDismiss = { savingTemplate = false })
    }
    if (info) {
        current()?.let { n -> NoteInfoDialog(n, data, backlinks.size, onDismiss = { info = false }) }
    }
    if (trashing) {
        ConfirmDialog(
            "Move to trash?",
            "You can restore it from Browse → Trash.",
            "Move to trash",
            onConfirm = {
                save()
                if (Store.data.value.note(id) != null) {
                    Store.trashNote(id)
                    actions.offerUndo("Note moved to trash") { Store.restoreNote(id) }
                }
                nav.pop()
            },
            onDismiss = { trashing = false },
        )
    }
}

@Composable
private fun NoteInfoDialog(n: Note, data: Data, backlinks: Int, onDismiss: () -> Unit) {
    val clock = LocalClock.current
    val rows = listOfNotNull(
        "Notebook" to data.notebook(n.notebookId).let { nb -> (nb.stack?.let { "$it › " } ?: "") + nb.name },
        "Created" to Dates.full(n.createdAt, clock.is24Hour),
        "Updated" to Dates.full(n.updatedAt, clock.is24Hour),
        "Words" to Markdown.wordCount(n.body).toString(),
        "Characters" to Markdown.plain(n.body, Int.MAX_VALUE).length.toString(),
        if (n.attachments.isEmpty()) null else "Attachments" to "${n.attachments.size} · ${Files.sizeLabel(n.attachments.sumOf { it.size })}",
        if (n.history.isEmpty()) null else "Earlier versions" to n.history.size.toString(),
        if (backlinks == 0) null else "Linked from" to (if (backlinks == 1) "1 note" else "$backlinks notes"),
        if (n.source.isBlank()) null else "Source" to n.source,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note info") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((k, v) in rows) {
                    Column {
                        Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun MetaPill(icon: ImageVector?, label: String, description: String = label, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (icon != null && label.isNotEmpty()) Spacer(Modifier.width(5.dp))
        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The editing toolbar: attachments, lists, indents, headings, inline styles, quotes, code, tables, links and dividers. */
@Composable
private fun FormatBar(
    modifier: Modifier,
    data: Data,
    noteId: String,
    attacher: Attacher,
    selectedText: String,
    onPrefix: (String) -> Unit,
    onIndent: (Int) -> Unit,
    onWrap: (String, String) -> Unit,
    onInsert: (String) -> Unit,
    onBlock: (String) -> Unit,
) {
    var attachMenu by remember { mutableStateOf(false) }
    var headingMenu by remember { mutableStateOf(false) }
    var linkMenu by remember { mutableStateOf(false) }
    var webLink by remember { mutableStateOf(false) }
    var noteLink by remember { mutableStateOf(false) }
    var table by remember { mutableStateOf(false) }
    val barShape = RoundedCornerShape(28.dp)
    Surface(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).softShadow(barShape, 10.dp),
        shape = barShape,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Box {
                FormatButton(Icons.Rounded.AttachFile, "Attach") { attachMenu = true }
                AttachMenu(attachMenu, attacher) { attachMenu = false }
            }
            FormatButton(Icons.Rounded.Mic, "Record audio") { attacher.audio() }
            FormatButton(Icons.Rounded.PhotoCamera, "Take photo") { attacher.camera() }
            FormatButton(Icons.Rounded.CheckBox, "Checklist") { onPrefix("[ ] ") }
            FormatButton(Icons.AutoMirrored.Rounded.FormatListBulleted, "Bulleted list") { onPrefix("- ") }
            FormatButton(Icons.Rounded.FormatListNumbered, "Numbered list") { onPrefix("1. ") }
            FormatButton(Icons.AutoMirrored.Rounded.FormatIndentIncrease, "Indent") { onIndent(1) }
            FormatButton(Icons.AutoMirrored.Rounded.FormatIndentDecrease, "Outdent") { onIndent(-1) }
            Box {
                FormatButton(Icons.Rounded.Title, "Heading") { headingMenu = true }
                DropdownMenu(expanded = headingMenu, onDismissRequest = { headingMenu = false }) {
                    for (level in 1..3) {
                        DropdownMenuItem(
                            text = { Text("Heading $level", style = when (level) { 1 -> MaterialTheme.typography.titleLarge; 2 -> MaterialTheme.typography.titleMedium; else -> MaterialTheme.typography.titleSmall }) },
                            onClick = { headingMenu = false; onPrefix("#".repeat(level) + " ") },
                        )
                    }
                }
            }
            FormatButton(Icons.Rounded.FormatBold, "Bold") { onWrap("**", "**") }
            FormatButton(Icons.Rounded.FormatItalic, "Italic") { onWrap("*", "*") }
            FormatButton(Icons.Rounded.FormatUnderlined, "Underline") { onWrap("<u>", "</u>") }
            FormatButton(Icons.Rounded.StrikethroughS, "Strikethrough") { onWrap("~~", "~~") }
            FormatButton(Icons.Rounded.BorderColor, "Highlight") { onWrap("==", "==") }
            FormatButton(Icons.Rounded.Code, "Inline code") { onWrap("`", "`") }
            FormatButton(Icons.Rounded.FormatQuote, "Quote") { onPrefix("> ") }
            FormatButton(Icons.Rounded.DataObject, "Code block") { onBlock("```\n\n```") }
            FormatButton(Icons.Rounded.TableChart, "Table") { table = true }
            Box {
                FormatButton(Icons.Rounded.Link, "Link") { linkMenu = true }
                DropdownMenu(expanded = linkMenu, onDismissRequest = { linkMenu = false }) {
                    DropdownMenuItem(text = { Text("Web link") }, onClick = { linkMenu = false; webLink = true })
                    DropdownMenuItem(text = { Text("Link to a note") }, onClick = { linkMenu = false; noteLink = true })
                }
            }
            FormatButton(Icons.Rounded.HorizontalRule, "Divider") { onBlock("---") }
        }
    }
    if (webLink) LinkDialog(selectedText, onInsert = { text, url -> onInsert("[$text]($url)") }, onDismiss = { webLink = false })
    if (noteLink) NoteLinkDialog(data, noteId, onPick = { onInsert(Links.to(it)) }, onDismiss = { noteLink = false })
    if (table) TableDialog(onInsert = { c, r -> onBlock(Markdown.table(c, r)) }, onDismiss = { table = false })
}

@Composable
private fun FormatButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, Modifier.semantics { contentDescription = label }) { Icon(icon, null) }
}
