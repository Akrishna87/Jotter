package io.github.akrishna87.jotter.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.jotter.Files
import io.github.akrishna87.jotter.Recorder
import io.github.akrishna87.jotter.model.Attachment
import io.github.akrishna87.jotter.model.Block
import io.github.akrishna87.jotter.model.Markdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The text styles for a note, scaled by the text size chosen in Settings. */
data class NoteStyles(val body: TextStyle, val h1: TextStyle, val h2: TextStyle, val h3: TextStyle, val code: TextStyle)

@Composable
fun noteStyles(scale: Float): NoteStyles {
    val t = MaterialTheme.typography
    fun TextStyle.scaled() = copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)
    return NoteStyles(
        body = t.bodyLarge.scaled(),
        h1 = t.headlineSmall.scaled(),
        h2 = t.titleLarge.scaled(),
        h3 = t.titleMedium.copy(fontWeight = FontWeight.SemiBold).scaled(),
        code = t.bodyMedium.copy(fontFamily = FontFamily.Monospace).scaled(),
    )
}

/**
 * A note drawn from its Markdown: headings, lists, tickable checklists, quotes, code, tables,
 * attachments (photos, PDFs, recordings and files) and links. Tapping text calls [onEdit].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarkdownView(
    body: String,
    attachments: List<Attachment>,
    styles: NoteStyles,
    onToggle: (Int) -> Unit,
    onEdit: () -> Unit,
    onNoteLink: (String) -> Unit,
    onRemoveAttachment: ((Attachment) -> Unit)? = null,
    /** The note shown, so its recordings can be transcribed; null where that isn't offered. */
    noteId: String? = null,
    onInsertText: ((String) -> Unit)? = null,
) {
    val blocks = remember(body) { Markdown.parse(body.trimEnd()) }
    val m = marks()
    val edit = Modifier.fillMaxWidth().clickable(onClick = onEdit)
    if (body.isBlank()) {
        Text("Tap to start writing", edit.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline, style = styles.body)
        return
    }
    fun text(s: String) = styled(Markdown.inline(s), m, onNoteLink)
    Column {
        for (b in blocks) {
            when (b) {
                is Block.Heading -> Text(
                    text(b.text),
                    edit.padding(top = 12.dp, bottom = 4.dp),
                    style = when (b.level) {
                        1 -> styles.h1
                        2 -> styles.h2
                        else -> styles.h3
                    },
                )
                is Block.Bullet -> Row(edit.padding(start = (b.indent * 20).dp, top = 2.dp, bottom = 2.dp)) {
                    Text(if (b.indent % 2 == 0) "•" else "◦", Modifier.width(22.dp).padding(start = 6.dp), style = styles.body)
                    Text(text(b.text), style = styles.body)
                }
                is Block.Numbered -> Row(edit.padding(start = (b.indent * 20).dp, top = 2.dp, bottom = 2.dp)) {
                    Text("${b.number}.", Modifier.width(28.dp), style = styles.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text(b.text), style = styles.body)
                }
                is Block.Check -> Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = (b.indent * 20).dp)
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
                        text(b.text),
                        Modifier.padding(start = 6.dp),
                        style = styles.body,
                        color = if (b.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (b.checked) TextDecoration.LineThrough else null,
                    )
                }
                is Block.Quote -> Row(edit.padding(vertical = 2.dp).height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                    Text(text(b.text), Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp), style = styles.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is Block.Code -> Box(
                    edit
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                ) { Text(b.text, style = styles.code, softWrap = false) }
                is Block.Table -> TableView(b, styles, onEdit) { s -> text(s) }
                is Block.Media -> {
                    val a = b.attachmentId?.let { id -> attachments.firstOrNull { it.id == id } }
                    if (a != null) AttachmentView(a, onRemoveAttachment, noteId, onInsertText) else Text("📎 ${b.name}", edit.padding(vertical = 4.dp), style = styles.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is Block.Paragraph -> Text(text(b.text), edit.padding(vertical = 2.dp), style = styles.body)
                is Block.Divider -> HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                is Block.Blank -> Spacer(edit.height(10.dp))
            }
        }
    }
}

@Composable
private fun TableView(t: Block.Table, styles: NoteStyles, onEdit: () -> Unit, text: (String) -> androidx.compose.ui.text.AnnotatedString) {
    val line = MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).horizontalScroll(rememberScrollState())) {
        Column(Modifier.border(1.dp, line, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)).clickable(onClick = onEdit)) {
            t.rows.forEachIndexed { r, row ->
                Row(Modifier.height(IntrinsicSize.Min).then(if (r == 0) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh) else Modifier)) {
                    row.forEachIndexed { c, cell ->
                        if (c > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(line))
                        Text(
                            text(cell),
                            Modifier.widthIn(min = 72.dp, max = 220.dp).padding(horizontal = 10.dp, vertical = 6.dp),
                            style = if (r == 0) styles.body.copy(fontWeight = FontWeight.SemiBold) else styles.body,
                        )
                    }
                }
                if (r < t.rows.lastIndex) HorizontalDivider(color = line)
            }
        }
    }
}

/** A loaded picture of an attachment (image, or a PDF's first page), loaded off the main thread. */
@Composable
fun rememberThumbnail(a: Attachment, maxSide: Int = 1080): Bitmap? {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(null, a.id, a.size) {
        value = withContext(Dispatchers.IO) { Files.thumbnail(context, a, maxSide) }
    }
    return bmp
}

/** An attachment in a note: a photo, a PDF's first page, a player for a recording, or a file card. Long-press for more. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttachmentView(a: Attachment, onRemove: ((Attachment) -> Unit)?, noteId: String? = null, onInsertText: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val actions = LocalActions.current
    var menu by remember { mutableStateOf(false) }
    val open = { if (!Files.open(context, a)) actions.message("No app here can open ${a.name}") }
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        val click = Modifier.combinedClickable(onClick = { if (!a.isAudio) open() }, onLongClick = { menu = true }, onClickLabel = "Open ${a.name}")
        when {
            a.isImage -> {
                val bmp = rememberThumbnail(a)
                if (bmp != null) {
                    Image(
                        bmp.asImageBitmap(), a.name,
                        Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(shape).then(click),
                        contentScale = ContentScale.Fit,
                    )
                } else FileCard(a, Icons.AutoMirrored.Rounded.InsertDriveFile, click)
            }
            a.isPdf -> {
                val bmp = rememberThumbnail(a, 720)
                val pages = remember(a.id) { Files.pdfPages(context, a) }
                Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).then(click)) {
                    if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 300.dp).background(androidx.compose.ui.graphics.Color.White), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PictureAsPdf, null, tint = MaterialTheme.colorScheme.error)
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text((if (pages > 0) "$pages page${if (pages == 1) "" else "s"} · " else "") + Files.sizeLabel(a.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            a.isAudio -> Column {
                AudioPlayer(a, Modifier.then(click))
                TranscriptPanel(a, noteId, onInsertText)
            }
            else -> FileCard(a, Icons.AutoMirrored.Rounded.InsertDriveFile, click)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Open") }, leadingIcon = { Icon(Icons.Rounded.OpenInNew, null) }, onClick = { menu = false; open() })
            DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = { menu = false; Files.share(context, a) })
            if (onRemove != null) {
                DropdownMenuItem(text = { Text("Remove from note") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; onRemove(a) })
            }
        }
    }
}

@Composable
private fun FileCard(a: Attachment, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).then(modifier).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(a.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Files.sizeLabel(a.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AudioPlayer(a: Attachment, modifier: Modifier) {
    val context = LocalContext.current
    val actions = LocalActions.current
    var playing by remember { mutableStateOf(Recorder.playingId == a.id) }
    var progress by remember { mutableIntStateOf(0) }
    val length = remember(a.id) { Recorder.duration(context, a) }
    // Playback can also start from the transcript, so follow the player rather than this button.
    LaunchedEffect(a.id) {
        while (true) {
            playing = Recorder.playingId == a.id
            progress = if (playing) Recorder.position().first else 0
            delay(250)
        }
    }
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = {
                if (playing) { Recorder.stopPlaying(); playing = false; progress = 0 }
                else if (Recorder.play(context, a) { playing = false; progress = 0 }) playing = true
                else actions.message("Couldn't play ${a.name}")
            },
            Modifier.semantics { contentDescription = if (playing) "Stop ${a.name}" else "Play ${a.name}" },
        ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null) }
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Mic, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(a.name.substringBeforeLast('.'), Modifier.padding(start = 4.dp).weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (playing) Recorder.clock(progress) + " / " + Recorder.clock(length) else Recorder.clock(length), style = MaterialTheme.typography.labelSmall)
            }
            LinearProgressIndicator(
                progress = { if (length > 0) (progress.toFloat() / length).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}
