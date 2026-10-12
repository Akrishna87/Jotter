package io.github.akrishna87.jotter.ui

import android.graphics.Color as AndroidColor
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akrishna87.jotter.Store
import io.github.akrishna87.jotter.model.Data
import io.github.akrishna87.jotter.model.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Screens that open on top of the notes list. */
sealed interface Page {
    /** An existing note, or a new one ([draft]) that's saved once it has something in it. */
    data class NotePage(val id: String, val draft: Note? = null) : Page
    data object Trash : Page
    data object SettingsPage : Page
    data object Search : Page
}

class Nav {
    val pages = mutableStateListOf<Page>()

    fun push(page: Page) {
        pages.add(page)
    }

    fun pop() {
        if (pages.isNotEmpty()) pages.removeAt(pages.lastIndex)
    }
}

val LocalNav = staticCompositionLocalOf<Nav> { error("No Nav") }

/** Short messages at the bottom of the screen, some with Undo. */
class Actions(private val scope: CoroutineScope, private val snackbar: SnackbarHostState) {
    fun message(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text, duration = SnackbarDuration.Short)
        }
    }

    fun offerUndo(text: String, undo: () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(text, actionLabel = "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) undo()
        }
    }
}

@Composable
fun JotterApp() {
    val data by Store.data.collectAsStateWithLifecycle()
    val dark = isDark(data.settings.theme)
    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(dark) {
        // Keep the status and navigation bar icons readable when the app's theme differs from the phone's.
        val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT) else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    JotterTheme(dark) { AppContent(data) }
}

@Composable
private fun AppContent(data: Data) {
    val context = LocalContext.current
    val nav = remember { Nav() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val actions = remember { Actions(scope, snackbar) }
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            val n = LocalDateTime.now()
            value = n
            delay(60_000L - n.second * 1000L - n.nano / 1_000_000L)
        }
    }
    val clock = Clock(now, DateFormat.is24HourFormat(context))

    BackHandler(enabled = nav.pages.isNotEmpty()) { nav.pop() }

    CompositionLocalProvider(
        LocalNav provides nav,
        LocalActions provides actions,
        LocalClock provides clock,
    ) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                floatingActionButton = {
                    Fab("New note") {
                        val note = NotesContext.newNote(Store.data.value)
                        nav.push(Page.NotePage(note.id, note))
                    }
                },
            ) { padding -> NotesScreen(data, padding) }

            AnimatedContent(
                targetState = nav.pages.lastOrNull(),
                transitionSpec = { (slideInHorizontally { it / 8 } + fadeIn()) togetherWith fadeOut() },
                label = "page",
            ) { page ->
                if (page != null) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { PageContent(page, data) }
                } else {
                    Box(Modifier)
                }
            }

            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = 72.dp),
            )
        }
    }
}

@Composable
private fun PageContent(page: Page, data: Data) {
    when (page) {
        is Page.NotePage -> NoteEditorPage(page, data)
        Page.Trash -> TrashPage(data)
        Page.SettingsPage -> SettingsPage(data)
        Page.Search -> SearchPage(data)
    }
}

@Composable
fun Fab(label: String, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        // Label the button itself so screen readers (and the UI tests) find it.
        modifier = Modifier.semantics { contentDescription = label },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) { Icon(Icons.Rounded.Add, null) }
}
