package io.github.akrishna87.jotter.ui

import android.content.Intent
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
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.StrikethroughS
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.akrishna87.jotter.Store
import io.github.akrishna87.jotter.model.Block
import io.github.akrishna87.jotter.model.Data
import io.github.akrishna87.jotter.model.Dates
import io.github.akrishna87.jotter.model.Markdown
import io.github.akrishna87.jotter.model.Note
import kotlinx.coroutines.delay

/**
 * A note, Evernote style. It opens ready to read, with checklists you can tick; tap the text (or
 * the pencil) to edit, with a toolbar for checklists, lists, headings and bold. It saves as you
 * type.
 */
@Composable
fun NoteEditorPage(page: Page.NotePage, data: Data) {
    val nav = LocalNav.current
    val actions = LocalActions.current
    val clock = LocalClock.current
    val context = LocalContext.current
    val id = page.id
    val start = data.note(id) ?: page.draft
    if (start == null) {
        LaunchedEffect(Unit) { nav.pop() }
        return
    }
    var title by remember(id) { mutableStateOf(TextFieldValue(start.title)) }
    var body by remember(id) { mutableStateOf(TextFieldValue(start.body)) }
    var notebookId by remember(id) { mutableStateOf(start.notebookId) }
    var tags by remember(id) { mutableStateOf(start.tags) }
    var pinned by remember(id) { mutableStateOf(start.pinned) }
    var editing by remember(id) { mutableStateOf(start.isBlank) }
    var menu by remember { mutableStateOf(false) }
    var notebookMenu by remember { mutableStateOf(false) }
    var tagDialog by remember { mutableStateOf(false) }
    var trashing by remember { mutableStateOf(false) }
    val bodyFocus = remember { FocusRequester() }
    val titleFocus = remember { FocusRequester() }

    fun save() {
        val base = Store.data.value.note(id) ?: page.draft ?: return
        if (base.trashed) return
        val next = base.copy(title = title.text.trim(), body = body.text.trimEnd(), notebookId = notebookId, tags = tags, pinned = pinned)
        if (next != base || Store.data.value.note(id) == null) Store.saveNote(next.copy(updatedAt = System.currentTimeMillis()))
    }
    LaunchedEffect(title.text, body.text, notebookId, tags, pinned) {
        delay(700)
        save()
    }
    DisposableEffect(id) { onDispose { save() } }
    LaunchedEffect(id) {
        if (start.isBlank) runCatching { titleFocus.requestFocus() }
    }

    val note = data.note(id)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageBar(onBack = { nav.pop() }) {
            if (editing) {
                IconButton(onClick = { editing = false; save() }, Modifier.semantics { contentDescription = "Done editing" }) { Icon(Icons.Rounded.Check, null) }
            } else {
                IconButton(onClick = { editing = true }, Modifier.semantics { contentDescription = "Edit note" }) { Icon(Icons.Rounded.Edit, null) }
            }
            IconButton(onClick = { pinned = !pinned }, Modifier.semantics { contentDescription = if (pinned) "Unpin note" else "Pin note" }) {
                Icon(if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin, null, tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            Box {
                IconButton(onClick = { menu = true }, Modifier.semantics { contentDescription = "Note options" }) { Icon(Icons.Rounded.MoreVert, null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit tags") }, onClick = { menu = false; tagDialog = true })
                    DropdownMenuItem(text = { Text("Share") }, onClick = {
                        menu = false
                        val text = listOf(title.text.trim(), Markdown.plain(body.text, Int.MAX_VALUE)).filter { it.isNotBlank() }.joinToString("\n\n")
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                    })
                    DropdownMenuItem(text = { Text("Move to trash", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; trashing = true })
                }
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            // Notebook and tags.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    MetaPill(Icons.Rounded.Book, data.notebook(notebookId).name) { notebookMenu = true }
                    DropdownMenu(expanded = notebookMenu, onDismissRequest = { notebookMenu = false }) {
                        for (nb in data.notebooks.sortedBy { it.createdAt }) {
                            DropdownMenuItem(
                                text = { Text(nb.name) },
                                onClick = { notebookId = nb.id; notebookMenu = false },
                                trailingIcon = { if (nb.id == notebookId) Icon(Icons.Rounded.Check, null) },
                            )
                        }
                    }
                }
                for (t in tags) MetaPill(null, "#$t") { tagDialog = true }
                MetaPill(Icons.Rounded.Add, if (tags.isEmpty()) "Tag" else "") { tagDialog = true }
            }
            PlainField(
                value = title,
                onChange = { title = it },
                placeholder = "Title",
                description = "Title",
                style = MaterialTheme.typography.headlineSmall,
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
                    placeholder = "Start writing, or use the toolbar for a checklist",
                    description = "Note text",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.defaultMinSize(minHeight = 240.dp).focusRequester(bodyFocus),
                )
            } else {
                MarkdownView(
                    body.text,
                    onToggle = { line -> body = TextFieldValue(Markdown.toggleCheck(body.text, line)) },
                    onEdit = { editing = true },
                )
            }

            Spacer(Modifier.height(32.dp))
        }

        if (editing) {
            FormatBar(
                Modifier.navigationBarsPadding().imePadding(),
                onPrefix = { prefix ->
                    val (text, cursor) = Markdown.toggleLinePrefix(body.text, body.selection.start, prefix)
                    body = TextFieldValue(text, TextRange(cursor))
                    runCatching { bodyFocus.requestFocus() }
                },
                onWrap = { mark ->
                    val (text, s, e) = Markdown.wrap(body.text, body.selection.start, body.selection.end, mark)
                    body = TextFieldValue(text, TextRange(s, e))
                    runCatching { bodyFocus.requestFocus() }
                },
                onDivider = {
                    val c = body.selection.end
                    val insert = (if (c > 0 && body.text[c - 1] != '\n') "\n" else "") + "---\n"
                    body = TextFieldValue(body.text.substring(0, c) + insert + body.text.substring(c), TextRange(c + insert.length))
                    runCatching { bodyFocus.requestFocus() }
                },
            )
        } else {
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (tagDialog) {
        TagsDialog(data.tags, tags, onDone = { tags = it }, onDismiss = { tagDialog = false })
    }
    if (trashing) {
        ConfirmDialog(
            "Move to trash?",
            "You can restore it from the menu → Trash.",
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
private fun MetaPill(icon: ImageVector?, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label.ifEmpty { "Add tag" } }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (icon != null && label.isNotEmpty()) Spacer(Modifier.width(5.dp))
        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The note drawn from its Markdown: headings, lists, tickable checklists and inline styles. */
@Composable
fun MarkdownView(body: String, onToggle: (Int) -> Unit, onEdit: () -> Unit) {
    val blocks = Markdown.parse(body.trimEnd())
    val edit = Modifier.fillMaxWidth().clickable(onClick = onEdit)
    if (body.isBlank()) {
        Text("Tap to start writing", edit.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodyLarge)
        return
    }
    Column {
        for (b in blocks) {
            when (b) {
                is Block.Heading -> Text(
                    styled(Markdown.inline(b.text)),
                    edit.padding(top = 12.dp, bottom = 4.dp),
                    style = when (b.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                )
                is Block.Bullet -> Row(edit.padding(vertical = 2.dp)) {
                    Text("•", Modifier.width(22.dp).padding(start = 6.dp), style = MaterialTheme.typography.bodyLarge)
                    Text(styled(Markdown.inline(b.text)), style = MaterialTheme.typography.bodyLarge)
                }
                is Block.Numbered -> Row(edit.padding(vertical = 2.dp)) {
                    Text("${b.number}.", Modifier.width(26.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(styled(Markdown.inline(b.text)), style = MaterialTheme.typography.bodyLarge)
                }
                is Block.Check -> Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(b.line) }
                        .semantics { contentDescription = (if (b.checked) "Untick " else "Tick ") + b.text },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = b.checked,
                        onCheckedChange = { onToggle(b.line) },
                        modifier = Modifier.size(36.dp),
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                    )
                    Text(
                        styled(Markdown.inline(b.text)),
                        Modifier.padding(start = 6.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (b.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (b.checked) TextDecoration.LineThrough else null,
                    )
                }
                is Block.Paragraph -> Text(styled(Markdown.inline(b.text)), edit.padding(vertical = 2.dp), style = MaterialTheme.typography.bodyLarge)
                is Block.Divider -> HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                is Block.Blank -> Spacer(edit.height(10.dp))
            }
        }
    }
}

@Composable
private fun FormatBar(modifier: Modifier, onPrefix: (String) -> Unit, onWrap: (String) -> Unit, onDivider: () -> Unit) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
            FormatButton(Icons.Rounded.CheckBox, "Checklist") { onPrefix("[ ] ") }
            FormatButton(Icons.Rounded.FormatListBulleted, "Bulleted list") { onPrefix("- ") }
            FormatButton(Icons.Rounded.FormatListNumbered, "Numbered list") { onPrefix("1. ") }
            FormatButton(Icons.Rounded.Title, "Heading") { onPrefix("## ") }
            FormatButton(Icons.Rounded.FormatBold, "Bold") { onWrap("**") }
            FormatButton(Icons.Rounded.FormatItalic, "Italic") { onWrap("*") }
            FormatButton(Icons.Rounded.StrikethroughS, "Strikethrough") { onWrap("~~") }
            FormatButton(Icons.Rounded.HorizontalRule, "Divider", onDivider)
        }
    }
}

@Composable
private fun FormatButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, Modifier.semantics { contentDescription = label }) { Icon(icon, null) }
}

/** Ticks tags on and off, and makes new ones. */
@Composable
private fun TagsDialog(all: List<String>, selected: List<String>, onDone: (List<String>) -> Unit, onDismiss: () -> Unit) {
    // Labels and tags behave the same; only the mark differs.
    LabelsDialogFor(mark = "#", title = "Tags", newHint = "New tag", all = all, selected = selected, onDone = onDone, onDismiss = onDismiss)
}
