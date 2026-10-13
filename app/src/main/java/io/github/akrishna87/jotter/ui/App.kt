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
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akrishna87.jotter.Files
import io.github.akrishna87.jotter.LaunchRequest
import io.github.akrishna87.jotter.Lock
import io.github.akrishna87.jotter.MainActivity
import io.github.akrishna87.jotter.Store
import io.github.akrishna87.jotter.model.Data
import io.github.akrishna87.jotter.model.Markdown
import io.github.akrishna87.jotter.model.Note
import io.github.akrishna87.jotter.model.StartPage
import io.github.akrishna87.jotter.model.Template
import io.github.akrishna87.jotter.model.Templates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    NOTES("Notes", Icons.Rounded.Description),
    BROWSE("Browse", Icons.Rounded.Menu),
}

/** Which notes a list shows. */
sealed interface NoteFilter {
    data object All : NoteFilter
    data class InNotebook(val id: String) : NoteFilter
    data class InStack(val name: String) : NoteFilter
    data class Tagged(val name: String) : NoteFilter
    data class Saved(val id: String) : NoteFilter
    data object WithReminders : NoteFilter
}

/** Screens that open on top of the tabs. */
sealed interface Page {
    /**
     * An existing note, or a new one ([draft]) that's saved once it has something in it. [start] is
     * something to do as it opens: "photo", "scan", "audio", "sketch" or "file".
     */
    data class NotePage(val id: String, val draft: Note? = null, val start: String? = null) : Page
    data class NoteList(val filter: NoteFilter) : Page
    data class Search(val query: String = "") : Page
    data class History(val id: String) : Page
    data object Trash : Page
    data object SettingsPage : Page
    data object Tags : Page
    data object Notebooks : Page
    data object TemplatesPage : Page
    data object RemindersPage : Page
}

class Nav(start: Tab) {
    var tab by mutableStateOf(start)
    val pages = mutableStateListOf<Page>()

    fun push(page: Page) {
        pages.add(page)
    }

    fun pop() {
        if (pages.isNotEmpty()) pages.removeAt(pages.lastIndex)
    }

    fun open(note: Note) = push(Page.NotePage(note.id))

    /**
     * Starts a note: in [notebookId] (or the default notebook), with [tags], from [template], and
     * for [kind] "checklist" ready with a box, or for a photo, scan, recording, sketch or file,
     * starting with that.
     */
    fun newNote(data: Data, notebookId: String? = null, tags: List<String> = emptyList(), template: Template? = null, kind: String = "note") {
        val nb = notebookId?.takeIf { id -> data.notebooks.any { it.id == id } }
            ?: data.settings.defaultNotebookId?.takeIf { id -> data.notebooks.any { it.id == id } }
            ?: data.notebooks.sortedBy { it.createdAt }.first().id
        val (title, body) = template?.let { Templates.fill(it, LocalDate.now()) } ?: ("" to if (kind == "checklist") "[ ] " else "")
        val note = Note(notebookId = nb, tags = tags, title = title, body = body)
        push(Page.NotePage(note.id, note, start = kind.takeIf { it in listOf("photo", "scan", "audio", "sketch", "file", "checklist") }))
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
    val nav = remember { Nav(if (data.settings.startPage == StartPage.NOTES) Tab.NOTES else Tab.HOME) }
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

    // The passcode lock: checked on start and each time the app comes back.
    var locked by remember { mutableStateOf(Lock.needsUnlock(data.settings.passcode, data.settings.lockAfterMinutes)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_START) {
                val s = Store.data.value.settings
                if (Lock.needsUnlock(s.passcode, s.lockAfterMinutes)) locked = true
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (data.settings.passcode == null && locked) locked = false

    BackHandler(enabled = nav.pages.isNotEmpty() && !locked) { nav.pop() }

    // What the app was opened for, once it's unlocked.
    LaunchedEffect(locked) {
        if (locked) return@LaunchedEffect
        MainActivity.launch.collect { request ->
            if (request == null) return@collect
            MainActivity.launch.value = null
            val d = Store.data.value
            when (request) {
                is LaunchRequest.OpenNote -> d.note(request.id)?.let { nav.pages.clear(); nav.open(it) }
                is LaunchRequest.NewNote -> { nav.pages.clear(); nav.newNote(d, kind = request.kind) }
                is LaunchRequest.Open -> when (request.page) {
                    "search" -> { nav.pages.clear(); nav.push(Page.Search()) }
                    "notes" -> { nav.pages.clear(); nav.tab = Tab.NOTES }
                }
                is LaunchRequest.Shared -> {
                    val note = withContext(Dispatchers.IO) { sharedNote(context, d, request) }
                    Store.addNotes(listOf(note))
                    nav.pages.clear()
                    nav.open(note)
                    actions.message("Saved to Jotter")
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalNav provides nav,
        LocalActions provides actions,
        LocalClock provides clock,
    ) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                bottomBar = {
                    NavigationBar {
                        for (t in Tab.entries) {
                            NavigationBarItem(
                                selected = nav.tab == t,
                                onClick = { nav.tab = t },
                                icon = { Icon(t.icon, null) },
                                label = { Text(t.label) },
                                modifier = Modifier.semantics { contentDescription = t.label },
                            )
                        }
                    }
                },
                floatingActionButton = {
                    Fab("New note") { nav.newNote(Store.data.value, notebookId = NotesContext.notebookId, tags = listOfNotNull(NotesContext.tag)) }
                },
            ) { padding ->
                when (nav.tab) {
                    Tab.HOME -> HomeScreen(data, padding)
                    Tab.NOTES -> NotesScreen(data, padding)
                    Tab.BROWSE -> BrowseScreen(data, padding)
                }
            }

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
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = 80.dp),
            )

            if (locked) {
                LockScreen(data.settings) {
                    Lock.unlocked = true
                    Lock.leftAt = 0L
                    locked = false
                }
            }
        }
    }
}

/** Shared text becomes the note (a link is kept as its source); shared files become its attachments. */
private fun sharedNote(context: android.content.Context, d: Data, r: LaunchRequest.Shared): Note {
    val text = r.text.trim()
    val url = Regex("https?://\\S+").find(text)?.value
    val atts = r.files.mapNotNull { Files.copy(context, it) }
    val (title, words) = when {
        r.title.isNotBlank() -> r.title.trim() to text
        url != null && text == url -> url.substringAfter("://").substringBefore('/') to text
        '\n' !in text && text.length <= 80 -> text to ""
        else -> text.lineSequence().first().take(60) to text
    }
    val body = listOf(words, atts.joinToString("\n") { Markdown.mediaLine(it) }).filter { it.isNotEmpty() }.joinToString("\n\n")
    val nb = d.settings.defaultNotebookId?.takeIf { id -> d.notebooks.any { it.id == id } } ?: d.notebooks.sortedBy { it.createdAt }.first().id
    return Note(title = title.ifBlank { atts.firstOrNull()?.name.orEmpty() }, body = body, notebookId = nb, attachments = atts, source = url.orEmpty())
}

@Composable
private fun PageContent(page: Page, data: Data) {
    when (page) {
        is Page.NotePage -> NoteEditorPage(page, data)
        is Page.NoteList -> NoteListPage(page.filter, data)
        is Page.Search -> SearchPage(data, page.query)
        is Page.History -> HistoryPage(page.id, data)
        Page.Trash -> TrashPage(data)
        Page.SettingsPage -> SettingsPage(data)
        Page.Tags -> TagsPage(data)
        Page.Notebooks -> NotebooksPage(data)
        Page.TemplatesPage -> TemplatesPage(data)
        Page.RemindersPage -> RemindersPage(data)
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
