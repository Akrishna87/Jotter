package io.github.akrishna87.jotter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.akrishna87.jotter.model.Attachment
import io.github.akrishna87.jotter.model.Enex
import io.github.akrishna87.jotter.model.Html
import io.github.akrishna87.jotter.model.Markdown
import io.github.akrishna87.jotter.model.Note
import java.io.ByteArrayOutputStream

/** Getting notes in and out: share, print or save as PDF, and files in HTML, Markdown and Evernote's ENEX. */
object Exports {
    fun text(note: Note): String = listOf(note.title.trim(), Markdown.plain(note.body, Int.MAX_VALUE)).filter { it.isNotBlank() }.joinToString("\n\n")

    fun markdown(note: Note): String = (if (note.title.isNotBlank()) "# ${note.title.trim()}\n\n" else "") + note.body.trimEnd() + "\n"

    fun shareText(context: Context, note: Note): Boolean =
        Files.start(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, note.title).putExtra(Intent.EXTRA_TEXT, text(note)), null))

    /** Images as data: URLs, so the HTML stands on its own (and WebView can show them). */
    private fun embedded(context: Context): (Attachment) -> String? = { a ->
        if (!a.isImage) null else Files.thumbnail(context, a, 1600)?.let { bmp ->
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    }

    fun html(context: Context, notes: List<Note>): String = Html.page(notes, embedded(context))

    // Kept so the page isn't collected before printing starts.
    private var printing: WebView? = null

    /** Opens Android's print screen, where "Save as PDF" makes a PDF. */
    fun print(activity: Activity, notes: List<Note>) {
        val name = notes.singleOrNull()?.title?.ifBlank { "Untitled" } ?: "Jotter notes"
        val web = WebView(activity)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val pm = activity.getSystemService(PrintManager::class.java) ?: return
                pm.print(name, view.createPrintDocumentAdapter(name), PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
                printing = null
            }
        }
        printing = web
        web.loadDataWithBaseURL(null, html(activity, notes), "text/html", "utf-8", null)
    }

    private fun write(context: Context, uri: Uri, text: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
        true
    }.getOrDefault(false)

    fun writeHtml(context: Context, uri: Uri, notes: List<Note>) = write(context, uri, html(context, notes))

    fun writeMarkdown(context: Context, uri: Uri, note: Note) = write(context, uri, markdown(note))

    fun writeEnex(context: Context, uri: Uri, notes: List<Note>) = write(context, uri, Enex.export(notes, { Files.bytes(context, it) }))

    /** A file name made from a note's title. */
    fun fileName(note: Note?, ext: String): String =
        ((note?.title ?: "").replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().take(60).ifBlank { if (note == null) "Jotter notes" else "Untitled" }) + ext

    /**
     * Reads an .enex file (into [notebookId]) or a text or Markdown file (as one note), saving any
     * attachments' files. Returns the notes, ready to add; empty if nothing could be read.
     */
    fun read(context: Context, uri: Uri, notebookId: String): List<Note> = runCatching {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        if (text.contains("<en-export")) {
            Enex.import(text, notebookId).map { imp ->
                for ((a, bytes) in imp.files) Files.file(context, a).apply { parentFile?.mkdirs() }.writeBytes(bytes)
                imp.note
            }
        } else {
            var name = "Imported note"
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0)?.let { name = it } }
            val lines = text.replace("\r\n", "\n").lines()
            val heading = lines.firstOrNull()?.takeIf { it.startsWith("# ") }
            listOf(Note(
                title = heading?.removePrefix("# ")?.trim() ?: name.substringBeforeLast('.'),
                body = (if (heading != null) lines.drop(1) else lines).joinToString("\n").trim(),
                notebookId = notebookId,
            ))
        }
    }.getOrDefault(emptyList())
}
