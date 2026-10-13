package io.github.akrishna87.jotter

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import io.github.akrishna87.jotter.model.Attachment
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The files kept with notes: photos, recordings, sketches, scans and anything else. Each is copied
 * into the app's own storage (files/attachments), named by its attachment id, and opened or shared
 * through a FileProvider. JSON backups don't include them; ENEX exports do.
 */
object Files {
    private const val DIR = "attachments"

    private fun authority(context: Context) = context.packageName + ".files"

    private fun extension(name: String): String = name.substringAfterLast('.', "").lowercase().takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }?.let { ".$it" } ?: ""

    fun file(context: Context, a: Attachment): File = File(File(context.filesDir, DIR), a.id + extension(a.name))

    fun uri(context: Context, a: Attachment): Uri = FileProvider.getUriForFile(context, authority(context), file(context, a))

    fun exists(context: Context, a: Attachment): Boolean = file(context, a).exists()

    fun bytes(context: Context, a: Attachment): ByteArray? = runCatching { file(context, a).readBytes() }.getOrNull()

    private fun stamp(): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm").format(LocalDateTime.now())

    /** Copies what [uri] points to into the app; null if it can't be read. */
    fun copy(context: Context, uri: Uri): Attachment? = runCatching {
        val resolver = context.contentResolver
        var name = "File ${stamp()}"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        val mime = resolver.getType(uri)?.takeIf { it != "application/octet-stream" } ?: guessMime(name)
        val a = Attachment(name = name, mime = mime)
        val out = file(context, a)
        out.parentFile?.mkdirs()
        resolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
        a.copy(size = out.length())
    }.getOrNull()

    /** Saves [bytes] as a new attachment. */
    fun write(context: Context, name: String, mime: String, bytes: ByteArray): Attachment {
        val a = Attachment(name = name, mime = mime, size = bytes.size.toLong())
        file(context, a).apply { parentFile?.mkdirs() }.writeBytes(bytes)
        return a
    }

    /** A new attachment for [name] whose file the caller will fill (a recording, a photo). */
    fun create(context: Context, name: String, mime: String): Pair<Attachment, File> {
        val a = Attachment(name = name, mime = mime)
        val f = file(context, a)
        f.parentFile?.mkdirs()
        f.delete()
        return a to f
    }

    /** An empty file for the camera to write a photo into, and the uri to hand it. */
    fun newPhoto(context: Context, scan: Boolean = false): Pair<Attachment, Uri> {
        val (a, f) = create(context, (if (scan) "Scan " else "Photo ") + stamp() + ".jpg", "image/jpeg")
        f.createNewFile()
        return a to FileProvider.getUriForFile(context, authority(context), f)
    }

    /** The photo once the camera has written it, with its size; null (and the file gone) if none was taken. */
    fun finished(context: Context, a: Attachment): Attachment? {
        val f = file(context, a)
        return if (f.exists() && f.length() > 0) a.copy(size = f.length()) else null.also { f.delete() }
    }

    fun saveBitmap(context: Context, bitmap: Bitmap, name: String): Attachment {
        val (a, f) = create(context, name, "image/png")
        f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return a.copy(size = f.length())
    }

    /**
     * Makes a photo of a page read like a scan: upright, grey, brighter paper and darker ink.
     * There's no edge detection (that would need a machine-learning library), so frame the page.
     */
    fun enhanceScan(context: Context, a: Attachment): Attachment {
        val f = file(context, a)
        val src = decode(f.path, 2400) ?: return a
        val upright = rotate(src, f.path)
        val out = Bitmap.createBitmap(upright.width, upright.height, Bitmap.Config.ARGB_8888)
        val grey = ColorMatrix().apply { setSaturation(0f) }
        // Stretch contrast: paper towards white, ink towards black.
        val contrast = 1.6f
        val shift = (-0.5f * contrast + 0.5f) * 255f + 25f
        grey.postConcat(ColorMatrix(floatArrayOf(contrast, 0f, 0f, 0f, shift, 0f, contrast, 0f, 0f, shift, 0f, 0f, contrast, 0f, shift, 0f, 0f, 0f, 1f, 0f)))
        Canvas(out).drawBitmap(upright, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(grey) })
        f.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return a.copy(size = f.length())
    }

    private fun rotate(b: Bitmap, path: String): Bitmap {
        val degrees = runCatching {
            when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (degrees == 0f) return b
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(degrees) }, true)
    }

    private fun decode(path: String, maxSide: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /** A picture of an image attachment (turned upright), or of a PDF's first page, at most [maxSide] across. */
    fun thumbnail(context: Context, a: Attachment, maxSide: Int = 1080): Bitmap? {
        val f = file(context, a)
        if (!f.exists()) return null
        if (a.isImage) return decode(f.path, maxSide)?.let { rotate(it, f.path) }
        if (a.isPdf) return runCatching {
            ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { r ->
                    r.openPage(0).use { page ->
                        val scale = maxSide.toFloat() / maxOf(page.width, page.height)
                        val bmp = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        }.getOrNull()
        return null
    }

    fun pdfPages(context: Context, a: Attachment): Int = runCatching {
        ParcelFileDescriptor.open(file(context, a), ParcelFileDescriptor.MODE_READ_ONLY).use { PdfRenderer(it).use { r -> r.pageCount } }
    }.getOrDefault(0)

    fun open(context: Context, a: Attachment): Boolean {
        if (!exists(context, a)) return false
        return start(context, Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri(context, a), a.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), a.name))
    }

    fun share(context: Context, a: Attachment): Boolean {
        if (!exists(context, a)) return false
        return start(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType(a.mime).putExtra(Intent.EXTRA_STREAM, uri(context, a)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), a.name))
    }

    fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    fun delete(context: Context, a: Attachment) {
        runCatching { file(context, a).delete() }
    }

    /** Removes files no note refers to any more (after notes are deleted for good). */
    fun clean(context: Context, keep: Set<String>) {
        val dir = File(context.filesDir, DIR)
        dir.listFiles()?.forEach { f -> if (f.name.substringBefore('.') !in keep) f.delete() }
    }

    fun sizeLabel(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024.0)
    }

    fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "heic" -> "image/heic"
        "pdf" -> "application/pdf"
        "txt", "md" -> "text/plain"
        "m4a", "aac" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg", "oga" -> "audio/ogg"
        "mp4" -> "video/mp4"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }
}
