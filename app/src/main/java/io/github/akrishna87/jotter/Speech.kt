package io.github.akrishna87.jotter

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.akrishna87.jotter.model.Attachment
import io.github.akrishna87.jotter.model.SpeechQuality
import io.github.akrishna87.jotter.model.Transcript
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** whisper.cpp, compiled into the app. Every call is on the caller's thread and can take a while. */
object Whisper {
    /** Told how far through a piece of audio the model is, 0 to 100. Called from native code. */
    fun interface Progress {
        fun onProgress(percent: Int)
    }

    private val loaded: Boolean by lazy { runCatching { System.loadLibrary("jotter_speech") }.isSuccess }

    fun available(): Boolean = loaded

    external fun loadBackends(dir: String): Int
    external fun open(model: String): Long
    external fun close(ctx: Long)
    external fun setCancelled(on: Boolean)
    external fun transcribe(ctx: Long, pcm: FloatArray, threads: Int, language: String, translate: Boolean, prompt: String, progress: Progress?): ByteArray?
}

/** The speech models, downloaded once into the app's own storage. */
object SpeechModels {
    private const val BASE = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"

    fun file(context: Context, q: SpeechQuality): File = File(File(context.filesDir, "models"), q.file)

    fun isReady(context: Context, q: SpeechQuality): Boolean = file(context, q).let { it.exists() && it.length() > 1_000_000 }

    fun downloadedBytes(context: Context): Long = File(context.filesDir, "models").listFiles()?.sumOf { it.length() } ?: 0L

    fun deleteAll(context: Context) {
        File(context.filesDir, "models").listFiles()?.forEach { it.delete() }
    }

    /** Downloads [q]'s model, telling [progress] the percentage; true once it's on the phone. */
    fun download(context: Context, q: SpeechQuality, cancelled: () -> Boolean, progress: (Int) -> Unit): Boolean {
        val target = file(context, q)
        if (isReady(context, q)) return true
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        return runCatching {
            // Follow the download's redirects (to the file host) by hand, as they change host.
            var url = URL(BASE + q.file)
            var conn = url.openConnection() as HttpURLConnection
            var hops = 0
            while (true) {
                conn.connectTimeout = 20_000
                conn.readTimeout = 60_000
                conn.instanceFollowRedirects = false
                val code = conn.responseCode
                if (code !in 300..399 || hops >= 6) {
                    if (code != 200) error("HTTP $code")
                    break
                }
                url = URL(url, conn.getHeaderField("Location"))
                conn.disconnect()
                conn = url.openConnection() as HttpURLConnection
                hops++
            }
            val total = conn.contentLengthLong
            var done = 0L
            var last = -1
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        if (cancelled()) error("cancelled")
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 100 / total).toInt() else 0
                        if (pct != last) { last = pct; progress(pct) }
                    }
                }
            }
            conn.disconnect()
            if (total > 0 && done != total) error("incomplete")
            part.renameTo(target)
        }.getOrElse { part.delete(); false }
    }
}

/** Reads any audio file the phone can play as 16 kHz mono samples, the form the speech model takes. */
object AudioDecoder {
    const val RATE = 16_000

    /**
     * Decodes [file] and hands it to [onChunk] in pieces of about [chunkSeconds], each with where it
     * starts. [onChunk] returns false to stop. Returns false if the file couldn't be decoded.
     */
    fun decode(file: File, chunkSeconds: Int, onChunk: (FloatArray, Long) -> Boolean): Boolean {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return false
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val c = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = c
            c.configure(format, null, null, 0)
            c.start()

            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            // Mono samples at the file's own rate, until there's a chunk's worth.
            var mono = FloatArray(rate * chunkSeconds)
            var filled = 0
            var startedAtMs = 0L
            var inputDone = false
            val info = MediaCodec.BufferInfo()

            fun emit(): Boolean {
                if (filled == 0) return true
                val out = resample(mono, filled, rate)
                val at = startedAtMs
                startedAtMs += filled * 1000L / rate
                filled = 0
                return onChunk(out, at)
            }

            while (true) {
                if (!inputDone) {
                    val i = c.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = c.getInputBuffer(i)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val o = c.dequeueOutputBuffer(info, 10_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = c.outputFormat
                        val newRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        if (newRate != rate) {
                            if (!emit()) return true
                            rate = newRate
                            mono = FloatArray(rate * chunkSeconds)
                        }
                    }
                    o >= 0 -> {
                        val buf = c.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder())
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val frames = if (floatPcm) info.size / 4 / channels else info.size / 2 / channels
                        for (f in 0 until frames) {
                            var sum = 0f
                            for (ch in 0 until channels) sum += if (floatPcm) buf.float else buf.short / 32768f
                            mono[filled++] = sum / channels
                            if (filled == mono.size && !emit()) {
                                c.releaseOutputBuffer(o, false)
                                return true
                            }
                        }
                        c.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            emit()
                            return true
                        }
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE")
            return true
        } catch (e: Exception) {
            return false
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    /** Linear interpolation from [from] Hz to 16 kHz. */
    fun resample(src: FloatArray, n: Int, from: Int): FloatArray {
        if (from == RATE) return src.copyOf(n)
        val outLen = (n.toLong() * RATE / from).toInt()
        val out = FloatArray(outLen)
        val step = from.toDouble() / RATE
        for (i in 0 until outLen) {
            val pos = i * step
            val k = pos.toInt()
            val t = (pos - k).toFloat()
            val a = src[k.coerceAtMost(n - 1)]
            val b = src[(k + 1).coerceAtMost(n - 1)]
            out[i] = a + (b - a) * t
        }
        return out
    }
}

/** Transcripts, one file per recording, kept next to the attachments. */
object Transcripts {
    private val changeCount = MutableStateFlow(0)

    /** Changes whenever a transcript is saved or deleted, so views showing one can reload it. */
    val changes: StateFlow<Int> = changeCount

    private fun dir(context: Context) = File(context.filesDir, "transcripts")

    private fun file(context: Context, attachmentId: String) = File(dir(context), "$attachmentId.json")

    fun load(context: Context, attachmentId: String): Transcript? =
        runCatching { Transcript.fromJson(JSONObject(file(context, attachmentId).readText())) }.getOrNull()

    fun save(context: Context, t: Transcript) {
        dir(context).mkdirs()
        val f = file(context, t.attachmentId)
        val tmp = File(f.path + ".tmp")
        tmp.writeText(t.toJson().toString())
        tmp.renameTo(f)
        changeCount.value++
    }

    fun delete(context: Context, attachmentId: String) {
        file(context, attachmentId).delete()
        changeCount.value++
    }
}

/** Turns recordings into transcripts, one at a time, in [TranscribeService]. */
object Transcriber {
    sealed interface Job {
        val noteId: String
        data class Waiting(override val noteId: String) : Job
        data class Downloading(override val noteId: String, val percent: Int) : Job
        data class Running(override val noteId: String, val percent: Int) : Job
    }

    private val state = MutableStateFlow<Map<String, Job>>(emptyMap())

    /** What's happening to each recording being transcribed, by attachment id. */
    val jobs: StateFlow<Map<String, Job>> = state

    private val failed = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Why the last try failed, by attachment id, until it's tried again. */
    val failures: StateFlow<Map<String, String>> = failed

    private data class Request(val attachment: Attachment, val noteId: String)

    private val queue = ArrayDeque<Request>()
    @Volatile private var cancelledIds = emptySet<String>()

    private fun set(id: String, job: Job?) {
        state.value = if (job == null) state.value - id else state.value + (id to job)
    }

    fun start(context: Context, attachment: Attachment, noteId: String) {
        if (attachment.id in state.value) return
        failed.value = failed.value - attachment.id
        synchronized(queue) {
            cancelledIds = cancelledIds - attachment.id
            queue.addLast(Request(attachment, noteId))
        }
        set(attachment.id, Job.Waiting(noteId))
        ContextCompat.startForegroundService(context, Intent(context, TranscribeService::class.java))
    }

    fun cancel(attachmentId: String) {
        synchronized(queue) {
            cancelledIds = cancelledIds + attachmentId
            queue.removeAll { it.attachment.id == attachmentId }
        }
        if (Whisper.available()) Whisper.setCancelled(true)
        set(attachmentId, null)
    }

    private fun next(): Request? = synchronized(queue) { queue.removeFirstOrNull() }

    /** Works through the queue; [onProgress] keeps the notification up to date. */
    internal fun runAll(context: Context, onProgress: (String) -> Unit) {
        while (true) {
            val r = next() ?: return
            val id = r.attachment.id
            if (Whisper.available()) Whisper.setCancelled(false)
            val result = runCatching { transcribe(context, r, onProgress) }.getOrElse { "Transcription stopped: ${it.message ?: "unknown error"}" }
            set(id, null)
            if (result != null && id !in cancelledIds) failed.value = failed.value + (id to result)
            if (result == null) Notifier.done(context, r.noteId)
        }
    }

    /** Null when the transcript was saved; otherwise why not. */
    private fun transcribe(context: Context, r: Request, onProgress: (String) -> Unit): String? {
        val id = r.attachment.id
        val settings = Store.data.value.settings
        val quality = settings.speechQuality
        val stopped = { id in cancelledIds }

        if (!SpeechModels.isReady(context, quality)) {
            set(id, Job.Downloading(r.noteId, 0))
            onProgress("Downloading the speech model")
            val ok = SpeechModels.download(context, quality, stopped) { pct ->
                set(id, Job.Downloading(r.noteId, pct))
                onProgress("Downloading the speech model · $pct%")
            }
            if (stopped()) return "Stopped"
            if (!ok) return "Couldn't download the speech model. Check the internet connection and try again."
        }
        if (!Whisper.available()) return "Speech recognition isn't available on this phone"
        Whisper.loadBackends(context.applicationInfo.nativeLibraryDir)
        val ctx = Whisper.open(SpeechModels.file(context, quality).path)
        if (ctx == 0L) {
            SpeechModels.file(context, quality).delete()
            return "The speech model couldn't be opened. Try again to download it afresh."
        }
        try {
            set(id, Job.Running(r.noteId, 0))
            onProgress("Transcribing · 0%")
            val audio = Files.file(context, r.attachment)
            val totalMs = Recorder.duration(context, r.attachment).toLong().coerceAtLeast(1)
            val threads = Runtime.getRuntime().availableProcessors().let { if (it >= 8) 6 else (it - 1).coerceIn(2, 4) }
            val segments = ArrayList<Transcript.Segment>()
            var language = ""
            var failure: String? = null
            val ok = AudioDecoder.decode(audio, CHUNK_SECONDS) { pcm, startMs ->
                if (stopped()) return@decode false
                val chunkMs = pcm.size * 1000L / AudioDecoder.RATE
                // The end of what came before, so words and names carry on across pieces.
                val prompt = segments.takeLast(3).joinToString(" ") { it.text.trim() }.takeLast(200)
                val out = Whisper.transcribe(ctx, pcm, threads, settings.speechLanguage, settings.speechTranslate, prompt, Whisper.Progress { pct ->
                    val overall = ((startMs + chunkMs * pct / 100) * 100 / totalMs).toInt().coerceIn(0, 99)
                    if ((state.value[id] as? Job.Running)?.percent != overall) {
                        set(id, Job.Running(r.noteId, overall))
                        onProgress("Transcribing · $overall%")
                    }
                })
                if (out == null) {
                    if (!stopped()) failure = "The speech model stopped part-way"
                    return@decode false
                }
                val lines = String(out, Charsets.UTF_8).split('\n')
                if (language.isEmpty()) language = lines.first().trim()
                for (line in lines.drop(1)) {
                    val p = line.split('\t', limit = 3)
                    if (p.size == 3 && p[2].isNotBlank()) {
                        segments += Transcript.Segment(startMs + (p[0].toLongOrNull() ?: 0), startMs + (p[1].toLongOrNull() ?: 0), p[2].trim())
                    }
                }
                true
            }
            if (stopped()) return "Stopped"
            failure?.let { return it }
            if (!ok) return "This recording couldn't be read"
            Transcripts.save(context, Transcript(id, segments, language, translated = settings.speechTranslate))
            return null
        } finally {
            Whisper.close(ctx)
        }
    }

    private const val CHUNK_SECONDS = 120
}

/** Keeps transcription going while Jotter is in the background, with its progress as a notification. */
class TranscribeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            Transcriber.jobs.value.keys.forEach { Transcriber.cancel(it) }
            return START_NOT_STICKY
        }
        Store.init(this)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, Notifier.PROGRESS_ID, Notifier.progress(this, "Getting ready to transcribe"), type)
        if (!running) {
            running = true
            scope.launch {
                Transcriber.runAll(this@TranscribeService) { text -> Notifier.update(this@TranscribeService, text) }
                running = false
                ServiceCompat.stopForeground(this@TranscribeService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Transcriber.jobs.value.keys.forEach { Transcriber.cancel(it) }
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL = "io.github.akrishna87.jotter.CANCEL_TRANSCRIPTION"
    }
}

private object Notifier {
    const val PROGRESS_ID = 4201
    private const val CHANNEL = "transcription"

    private fun channel(context: Context) {
        val m = context.getSystemService(NotificationManager::class.java) ?: return
        if (m.getNotificationChannel(CHANNEL) == null) {
            m.createNotificationChannel(NotificationChannel(CHANNEL, "Transcription", NotificationManager.IMPORTANCE_LOW).apply { description = "Progress while recordings are turned into text" })
        }
    }

    private fun open(context: Context, noteId: String?): PendingIntent = PendingIntent.getActivity(
        context, (noteId ?: "transcribe").hashCode(),
        Intent(context, MainActivity::class.java).apply {
            if (noteId != null) setData(Uri.parse("jotter://note/$noteId")).putExtra(MainActivity.EXTRA_OPEN_NOTE, noteId)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun progress(context: Context, text: String) = NotificationCompat.Builder(context, CHANNEL.also { channel(context) })
        .setSmallIcon(R.drawable.ic_stat_jotter)
        .setColor(ContextCompat.getColor(context, R.color.brand))
        .setContentTitle("Transcribing a recording")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setProgress(100, Regex("""(\d+)%""").find(text)?.groupValues?.get(1)?.toInt() ?: 0, !text.contains('%'))
        .setContentIntent(open(context, Transcriber.jobs.value.values.firstOrNull()?.noteId))
        .addAction(0, "Cancel", PendingIntent.getService(context, 1, Intent(context, TranscribeService::class.java).setAction(TranscribeService.ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE))
        .build()

    private fun allowed(context: Context) =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun update(context: Context, text: String) {
        if (allowed(context)) runCatching { NotificationManagerCompat.from(context).notify(PROGRESS_ID, progress(context, text)) }
    }

    fun done(context: Context, noteId: String) {
        if (!allowed(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL.also { channel(context) })
            .setSmallIcon(R.drawable.ic_stat_jotter)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle("Transcript and summary ready")
            .setContentText(Store.data.value.note(noteId)?.title?.ifBlank { null } ?: "Tap to open the note")
            .setAutoCancel(true)
            .setContentIntent(open(context, noteId))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(noteId.hashCode(), n) }
    }
}
