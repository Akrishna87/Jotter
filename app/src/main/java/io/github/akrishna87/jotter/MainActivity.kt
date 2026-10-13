package io.github.akrishna87.jotter

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.akrishna87.jotter.ui.JotterApp
import kotlinx.coroutines.flow.MutableStateFlow

/** What the app was opened for, from a notification, widget, shortcut, tile or another app's Share. */
sealed interface LaunchRequest {
    data class OpenNote(val id: String) : LaunchRequest
    /** A new note: "note", "checklist", "photo", "scan", "audio" or "sketch". */
    data class NewNote(val kind: String) : LaunchRequest
    data class Open(val page: String) : LaunchRequest
    /** Text, a link or files shared from another app, to become a note. */
    data class Shared(val title: String, val text: String, val files: List<Uri>) : LaunchRequest
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Store.init(this)
        if (savedInstanceState == null) handle(intent)
        setContent { JotterApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStop() {
        super.onStop()
        // Changing configuration (turning the phone) isn't leaving.
        if (!isChangingConfigurations) Lock.leftAt = System.currentTimeMillis()
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        val request: LaunchRequest? = when {
            intent.getStringExtra(EXTRA_OPEN_NOTE) != null -> LaunchRequest.OpenNote(intent.getStringExtra(EXTRA_OPEN_NOTE)!!)
            intent.getStringExtra(EXTRA_NEW) != null -> LaunchRequest.NewNote(intent.getStringExtra(EXTRA_NEW)!!)
            intent.getStringExtra(EXTRA_OPEN) != null -> LaunchRequest.Open(intent.getStringExtra(EXTRA_OPEN)!!)
            intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE -> {
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
                val title = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
                val files = streams(intent)
                if (text.isBlank() && files.isEmpty()) null else LaunchRequest.Shared(title, text, files)
            }
            else -> null
        }
        if (request != null) launch.value = request
    }

    @Suppress("DEPRECATION")
    private fun streams(intent: Intent): List<Uri> = when {
        intent.action == Intent.ACTION_SEND_MULTIPLE ->
            (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)).orEmpty()
        else -> listOfNotNull(if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
    }

    companion object {
        const val EXTRA_OPEN_NOTE = "open_note"
        const val EXTRA_NEW = "new_note"
        const val EXTRA_OPEN = "open"

        /** Read (and cleared) by the app once it has acted on it. */
        val launch = MutableStateFlow<LaunchRequest?>(null)
    }
}
