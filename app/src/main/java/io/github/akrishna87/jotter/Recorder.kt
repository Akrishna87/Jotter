package io.github.akrishna87.jotter

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import io.github.akrishna87.jotter.model.Attachment
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Records audio notes (AAC in .m4a) and plays them back, one at a time. */
object Recorder {
    private var recorder: MediaRecorder? = null
    private var recording: Pair<Attachment, File>? = null
    private var player: MediaPlayer? = null
    var playingId: String? = null
        private set

    /** Starts recording; false if the microphone couldn't be used. */
    fun start(context: Context): Boolean {
        stopPlaying()
        cancel()
        val name = "Recording " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm").format(LocalDateTime.now()) + ".m4a"
        val (a, f) = Files.create(context, name, "audio/mp4")
        return runCatching {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(f.path)
            r.prepare()
            r.start()
            recorder = r
            recording = a to f
            true
        }.getOrElse { f.delete(); false }
    }

    /** The loudness right now, 0 to 1, for the level meter. */
    fun level(): Float = runCatching { (recorder?.maxAmplitude ?: 0) / 32767f }.getOrDefault(0f).coerceIn(0f, 1f)

    /** Stops and returns the recording, or null if nothing usable was recorded. */
    fun stop(): Attachment? {
        val r = recorder ?: return null
        val (a, f) = recording ?: return null
        recorder = null
        recording = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        return if (ok && f.length() > 0) a.copy(size = f.length()) else null.also { f.delete() }
    }

    fun cancel() {
        recorder?.let { runCatching { it.stop() }; it.release() }
        recorder = null
        recording?.second?.delete()
        recording = null
    }

    fun play(context: Context, a: Attachment, onDone: () -> Unit): Boolean {
        stopPlaying()
        return runCatching {
            val p = MediaPlayer()
            p.setDataSource(Files.file(context, a).path)
            p.setOnCompletionListener { stopPlaying(); onDone() }
            p.prepare()
            p.start()
            player = p
            playingId = a.id
            true
        }.getOrDefault(false)
    }

    fun position(): Pair<Int, Int> = runCatching { (player?.currentPosition ?: 0) to (player?.duration ?: 0) }.getOrDefault(0 to 0)

    fun stopPlaying() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        playingId = null
    }

    fun duration(context: Context, a: Attachment): Int = runCatching {
        val r = android.media.MediaMetadataRetriever()
        r.setDataSource(Files.file(context, a).path)
        val ms = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toInt() ?: 0
        r.release()
        ms
    }.getOrDefault(0)

    fun clock(ms: Int): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }
}
