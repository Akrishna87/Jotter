package io.github.akrishna87.jotter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.akrishna87.jotter.Recorder
import io.github.akrishna87.jotter.SpeechModels
import io.github.akrishna87.jotter.Store
import io.github.akrishna87.jotter.Transcriber
import io.github.akrishna87.jotter.Transcripts
import io.github.akrishna87.jotter.model.Attachment
import io.github.akrishna87.jotter.model.MeetingSummary
import io.github.akrishna87.jotter.model.SpeechLanguages
import io.github.akrishna87.jotter.model.Summarizer
import io.github.akrishna87.jotter.model.Transcript

/**
 * Under a recording: a button to turn it into text, its progress, and then a short summary (main
 * points, decisions and action items) with the full transcript folded away underneath.
 */
@Composable
fun TranscriptPanel(a: Attachment, noteId: String?, onInsert: ((String) -> Unit)?) {
    val context = LocalContext.current
    val version by Transcripts.changes.collectAsState()
    val transcript = remember(a.id, version) { Transcripts.load(context, a.id) }
    val job = Transcriber.jobs.collectAsState().value[a.id]
    val failure = Transcriber.failures.collectAsState().value[a.id]
    var asking by remember { mutableStateOf(false) }
    val quality = Store.data.collectAsState().value.settings.speechQuality

    fun start() {
        if (noteId == null) return
        if (SpeechModels.isReady(context, quality)) Transcriber.start(context, a, noteId) else asking = true
    }

    when {
        job != null -> JobRow(job) { Transcriber.cancel(a.id) }
        transcript != null -> SummaryCard(a, transcript, onInsert, onRedo = ::start)
        noteId != null -> Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            if (failure != null && failure != "Stopped") {
                Text(failure, Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            OutlinedButton(onClick = ::start, Modifier.semantics { contentDescription = "Transcribe recording" }) {
                Icon(Icons.AutoMirrored.Rounded.Notes, null, Modifier.size(18.dp))
                Text("Transcribe and summarise", Modifier.padding(start = 8.dp))
            }
        }
    }

    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Download the speech model?") },
            text = {
                Text(
                    "Jotter turns recordings into text on this phone. Nothing you record is uploaded.\n\n" +
                        "The ${quality.label.lowercase()} speech model is about ${quality.sizeMb} MB and is downloaded once. " +
                        "You can change it in Settings.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    if (noteId != null) Transcriber.start(context, a, noteId)
                }) { Text("Download") }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Not now") } },
        )
    }
}

@Composable
private fun JobRow(job: Transcriber.Job, onCancel: () -> Unit) {
    val (label, pct) = when (job) {
        is Transcriber.Job.Waiting -> "Waiting to transcribe" to null
        is Transcriber.Job.Downloading -> "Downloading the speech model · ${job.percent}%" to job.percent
        is Transcriber.Job.Running -> "Transcribing · ${job.percent}%" to job.percent
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (pct == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
            } else {
                LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
        IconButton(onClick = onCancel, Modifier.semantics { contentDescription = "Cancel transcription" }) { Icon(Icons.Rounded.Close, null) }
    }
}

@Composable
private fun SummaryCard(a: Attachment, t: Transcript, onInsert: ((String) -> Unit)?, onRedo: () -> Unit) {
    val context = LocalContext.current
    val actions = LocalActions.current
    val clipboard = LocalClipboardManager.current
    val kit = LocalKit.current
    val summary = remember(t) { Summarizer.summarize(t) }
    var showAll by rememberSaveable(a.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val play = { ms: Long -> if (!Recorder.play(context, a, fromMs = ms.toInt()) {}) actions.message("Couldn't play ${a.name}") }
    val language = SpeechLanguages.firstOrNull { it.first == t.language }?.second

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (kit.dark) 0.35f else 0.45f))
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.Notes, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Summary", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
            if (language != null && t.language != "en") {
                Text(
                    if (t.translated) "$language → English" else language,
                    Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }, Modifier.semantics { contentDescription = "Transcript options" }) { Icon(Icons.Rounded.MoreVert, null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Copy summary") }, onClick = {
                        menu = false
                        clipboard.setText(AnnotatedString(summary.toMarkdown()))
                        actions.message("Summary copied")
                    })
                    DropdownMenuItem(text = { Text("Copy full transcript") }, onClick = {
                        menu = false
                        clipboard.setText(AnnotatedString(t.text))
                        actions.message("Transcript copied")
                    })
                    DropdownMenuItem(text = { Text("Transcribe again") }, onClick = {
                        menu = false
                        Transcripts.delete(context, a.id)
                        onRedo()
                    })
                    DropdownMenuItem(text = { Text("Delete transcript", color = MaterialTheme.colorScheme.error) }, onClick = {
                        menu = false
                        Transcripts.delete(context, a.id)
                    })
                }
            }
        }

        if (summary.isEmpty) {
            Text("No speech was heard in this recording.", Modifier.padding(end = 10.dp, bottom = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            summary.keyPoints.forEach { Point(it, null, play) }
            if (summary.decisions.isNotEmpty()) {
                Heading("Decisions")
                summary.decisions.forEach { Point(it, Icons.Rounded.Gavel, play) }
            }
            if (summary.actions.isNotEmpty()) {
                Heading("Action items")
                summary.actions.forEach { Point(it, Icons.Rounded.CheckBoxOutlineBlank, play) }
            }
            if (onInsert != null) {
                FilledTonalButton(
                    onClick = { onInsert(summary.toMarkdown()); actions.message("Summary added to the note") },
                    Modifier.padding(top = 10.dp).semantics { contentDescription = "Add summary to note" },
                ) {
                    Icon(Icons.Rounded.NoteAdd, null, Modifier.size(18.dp))
                    Text("Add to note", Modifier.padding(start = 8.dp))
                }
            }
        }

        if (t.segments.isNotEmpty()) {
            val words = remember(t) { t.text.split(Regex("""\s+""")).count { it.isNotBlank() } }
            TextButton(onClick = { showAll = !showAll }, Modifier.padding(top = 2.dp)) {
                Text(if (showAll) "Hide full transcript" else "Show full transcript · $words words")
            }
            if (showAll) {
                Column(Modifier.padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    t.segments.forEach { s ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable(onClickLabel = "Play from here") { play(s.startMs) }.padding(vertical = 3.dp)) {
                            Text(Recorder.clock(s.startMs.toInt()), Modifier.width(48.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(s.text, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, Modifier.padding(top = 10.dp, bottom = 2.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

/** One line of the summary; tapping it plays the recording from when it was said. */
@Composable
private fun Point(line: MeetingSummary.Line, icon: ImageVector?, play: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(end = 10.dp).clip(RoundedCornerShape(6.dp)).clickable(onClickLabel = "Play from here") { play(line.atMs) }.padding(vertical = 3.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.padding(top = 2.dp, end = 8.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("•", Modifier.width(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text(line.text, style = MaterialTheme.typography.bodyMedium)
    }
}
