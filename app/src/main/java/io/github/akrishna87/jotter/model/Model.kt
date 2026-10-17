package io.github.akrishna87.jotter.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

const val DEFAULT_NOTEBOOK_ID = "notebook-default"

/** A notebook. Notebooks with the same [stack] are shown together under that name, as in Evernote. */
data class Notebook(
    val id: String = newId(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val stack: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("createdAt", createdAt).put("stack", stack ?: "")

    companion object {
        val Default = Notebook(DEFAULT_NOTEBOOK_ID, "My notebook", 0L)

        fun fromJson(o: JSONObject) = Notebook(
            id = o.optString("id").ifEmpty { newId() },
            name = o.optString("name"),
            createdAt = o.optLong("createdAt"),
            stack = o.optString("stack").takeIf { it.isNotBlank() },
        )
    }
}

/**
 * A file kept with a note: a photo, a recording, a sketch, a PDF or anything else. The file itself is
 * stored by the app under its [id]; the note's text shows it where `![name](attachment:id)` is.
 */
data class Attachment(
    val id: String = newId(),
    val name: String,
    val mime: String,
    val size: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isImage: Boolean get() = mime.startsWith("image/")
    val isAudio: Boolean get() = mime.startsWith("audio/")
    val isPdf: Boolean get() = mime == "application/pdf"

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("mime", mime).put("size", size).put("createdAt", createdAt)

    companion object {
        fun fromJson(o: JSONObject) = Attachment(
            id = o.optString("id").ifEmpty { newId() },
            name = o.optString("name"),
            mime = o.optString("mime").ifEmpty { "application/octet-stream" },
            size = o.optLong("size"),
            createdAt = o.optLong("createdAt"),
        )
    }
}

/** An earlier version of a note, for its history. */
data class Version(val savedAt: Long, val title: String, val body: String) {
    fun toJson(): JSONObject = JSONObject().put("savedAt", savedAt).put("title", title).put("body", body)

    companion object {
        fun fromJson(o: JSONObject) = Version(o.optLong("savedAt"), o.optString("title"), o.optString("body"))
    }
}

data class Note(
    val id: String = newId(),
    val title: String = "",
    /** Markdown; see [Markdown] for what's understood. */
    val body: String = "",
    val notebookId: String = DEFAULT_NOTEBOOK_ID,
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    /** When it was moved to the trash; 0 if it isn't there. */
    val trashedAt: Long = 0L,
    /** A reminder: when to notify, or null for none. */
    val reminderAt: Long? = null,
    /** When the reminder was marked done; 0 while it's still to do. */
    val reminderDone: Long = 0L,
    val attachments: List<Attachment> = emptyList(),
    /** Earlier versions, oldest first. */
    val history: List<Version> = emptyList(),
    /** Where it came from, for notes clipped from a web page or shared from another app. */
    val source: String = "",
) {
    val isBlank: Boolean get() = title.isBlank() && body.isBlank() && attachments.isEmpty()
    val trashed: Boolean get() = trashedAt != 0L
    val hasReminder: Boolean get() = reminderAt != null

    /**
     * This note after an edit ([next]), keeping the version before it in the history when that version had
     * settled: it was last changed at least [gapMillis] ago. So a burst of typing makes one version, not
     * one per keystroke. At most [keep] versions are kept.
     */
    fun edited(next: Note, now: Long, gapMillis: Long = 10 * 60_000L, keep: Int = 30): Note {
        val changed = next.title != title || next.body != body
        if (!changed || (title.isBlank() && body.isBlank()) || now - updatedAt < gapMillis) return next.copy(history = history)
        return next.copy(history = (history + Version(updatedAt, title, body)).takeLast(keep))
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("body", body)
        .put("notebookId", notebookId)
        .put("tags", JSONArray(tags))
        .put("pinned", pinned)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("trashedAt", trashedAt)
        .put("reminderAt", reminderAt ?: 0L)
        .put("reminderDone", reminderDone)
        .put("attachments", JSONArray(attachments.map { it.toJson() }))
        .put("history", JSONArray(history.map { it.toJson() }))
        .put("source", source)

    companion object {
        fun fromJson(o: JSONObject): Note {
            val created = o.optLong("createdAt", o.optLong("updatedAt"))
            return Note(
                id = o.optString("id").ifEmpty { newId() },
                title = o.optString("title"),
                body = o.optString("body"),
                notebookId = o.optString("notebookId").ifEmpty { DEFAULT_NOTEBOOK_ID },
                tags = o.optJSONArray("tags").strings(),
                pinned = o.optBoolean("pinned"),
                createdAt = created,
                updatedAt = o.optLong("updatedAt", created),
                trashedAt = o.optLong("trashedAt"),
                reminderAt = o.optLong("reminderAt").takeIf { it > 0 },
                reminderDone = o.optLong("reminderDone"),
                attachments = o.optJSONArray("attachments").objects().map(Attachment::fromJson),
                history = o.optJSONArray("history").objects().map(Version::fromJson),
                source = o.optString("source"),
            )
        }
    }
}

/** Where a tag sits: tags can be nested under another tag, as in Evernote. Tags without one are at the top. */
data class TagInfo(val name: String, val parent: String? = null) {
    fun toJson(): JSONObject = JSONObject().put("name", name).put("parent", parent ?: "")

    companion object {
        fun fromJson(o: JSONObject) = TagInfo(o.optString("name"), o.optString("parent").takeIf { it.isNotBlank() })
    }
}

/** A search kept under a name, in Evernote's search syntax (see [Search]). */
data class SavedSearch(val id: String = newId(), val name: String, val query: String) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("query", query)

    companion object {
        fun fromJson(o: JSONObject) = SavedSearch(o.optString("id").ifEmpty { newId() }, o.optString("name"), o.optString("query"))
    }
}

enum class ShortcutKind { NOTE, NOTEBOOK, STACK, TAG, SEARCH }

/** Something kept at hand on Home and in Browse: a note, notebook, stack, tag or saved search, by id or name. */
data class Shortcut(val kind: ShortcutKind, val ref: String) {
    fun toJson(): JSONObject = JSONObject().put("kind", kind.name).put("ref", ref)

    companion object {
        fun fromJson(o: JSONObject): Shortcut? = runCatching { Shortcut(ShortcutKind.valueOf(o.optString("kind")), o.optString("ref")) }.getOrNull()
    }
}

/** A note to start new notes from. The built-in ones are in [Templates]. */
data class Template(val id: String = newId(), val name: String, val title: String = "", val body: String) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("title", title).put("body", body)

    companion object {
        fun fromJson(o: JSONObject) = Template(o.optString("id").ifEmpty { newId() }, o.optString("name"), o.optString("title"), o.optString("body"))
    }
}

enum class ThemeMode(val label: String) { SYSTEM("System default"), LIGHT("Light"), DARK("Dark") }

enum class NoteSort(val label: String) { UPDATED("Date updated"), CREATED("Date created"), TITLE("Title"), REMINDER("Reminder") }

enum class ListStyle(val label: String) { SNIPPETS("Snippets"), CARDS("Cards"), LIST("Simple list") }

enum class TextSize(val label: String, val scale: Float) { SMALL("Small", 0.9f), NORMAL("Normal", 1f), LARGE("Large", 1.15f), HUGE("Huge", 1.3f) }

enum class StartPage(val label: String) { HOME("Home"), NOTES("Notes") }

/** Home's sections, in order; each can be hidden. */
enum class HomeSection(val label: String) { SCRATCH("Scratch pad"), PINNED("Pinned notes"), RECENT("Recent notes"), REMINDERS("Reminders"), TASKS("Open checklists"), SHORTCUTS("Shortcuts"), TAGS("Tags") }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val noteSort: NoteSort = NoteSort.UPDATED,
    val listStyle: ListStyle = ListStyle.SNIPPETS,
    val textSize: TextSize = TextSize.NORMAL,
    val startPage: StartPage = StartPage.HOME,
    /** Where new notes go when no notebook is open; null for the first notebook. */
    val defaultNotebookId: String? = null,
    val homeSections: List<HomeSection> = HomeSection.entries,
    /** Home's quick text, kept until it's turned into a note. */
    val scratchPad: String = "",
    /** A hash of the passcode that locks Jotter, or null when it isn't locked. */
    val passcode: String? = null,
    /** Lock again after this many minutes away. */
    val lockAfterMinutes: Int = 1,
    val fingerprint: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("theme", theme.name)
        .put("noteSort", noteSort.name)
        .put("listStyle", listStyle.name)
        .put("textSize", textSize.name)
        .put("startPage", startPage.name)
        .put("defaultNotebookId", defaultNotebookId ?: "")
        .put("homeSections", JSONArray(homeSections.map { it.name }))
        .put("scratchPad", scratchPad)
        .put("passcode", passcode ?: "")
        .put("lockAfterMinutes", lockAfterMinutes)
        .put("fingerprint", fingerprint)

    companion object {
        fun fromJson(o: JSONObject?): Settings {
            val d = Settings()
            if (o == null) return d
            return Settings(
                theme = enumOr(o.optString("theme"), d.theme),
                noteSort = enumOr(o.optString("noteSort"), d.noteSort),
                listStyle = enumOr(o.optString("listStyle"), d.listStyle),
                textSize = enumOr(o.optString("textSize"), d.textSize),
                startPage = enumOr(o.optString("startPage"), d.startPage),
                defaultNotebookId = o.optString("defaultNotebookId").takeIf { it.isNotBlank() },
                homeSections = o.optJSONArray("homeSections")?.strings()?.mapNotNull { s -> HomeSection.entries.firstOrNull { it.name == s } } ?: d.homeSections,
                scratchPad = o.optString("scratchPad"),
                passcode = o.optString("passcode").takeIf { it.isNotBlank() },
                lockAfterMinutes = o.optInt("lockAfterMinutes", d.lockAfterMinutes),
                fingerprint = o.optBoolean("fingerprint", d.fingerprint),
            )
        }
    }
}

private inline fun <reified E : Enum<E>> enumOr(name: String, default: E): E = enumValues<E>().firstOrNull { it.name == name } ?: default

/** Everything Jotter keeps, saved as one JSON file (and exported as the same JSON for backups). */
data class Data(
    val notes: List<Note> = emptyList(),
    val notebooks: List<Notebook> = listOf(Notebook.Default),
    val settings: Settings = Settings(),
    val tagInfo: List<TagInfo> = emptyList(),
    val searches: List<SavedSearch> = emptyList(),
    val shortcuts: List<Shortcut> = emptyList(),
    val templates: List<Template> = emptyList(),
) {
    fun notebook(id: String?): Notebook = notebooks.firstOrNull { it.id == id } ?: notebooks.firstOrNull() ?: Notebook.Default

    fun note(id: String?): Note? = notes.firstOrNull { it.id == id }

    val liveNotes: List<Note> get() = notes.filter { !it.trashed }

    /** Every tag: those on notes not in the trash and those set up in the tag list, alphabetically. */
    val tags: List<String>
        get() = (liveNotes.flatMap { it.tags } + tagInfo.map { it.name } + tagInfo.mapNotNull { it.parent })
            .distinctBy { it.lowercase() }.sortedWith(NaturalOrder)

    fun parentOf(tag: String): String? = tagInfo.firstOrNull { it.name.equals(tag, true) }?.parent

    fun childrenOf(tag: String?): List<String> = tags.filter { t -> parentOf(t)?.equals(tag, true) ?: (tag == null) }

    /** A tag and every tag nested under it, at any depth. */
    fun tagAndChildren(tag: String): Set<String> {
        val out = linkedSetOf<String>()
        fun add(t: String) {
            if (out.add(t.lowercase())) childrenOf(t).forEach(::add)
        }
        add(tag)
        return out
    }

    /** Stacks by name, each with its notebooks, then the notebooks in no stack. */
    val stacks: List<String> get() = notebooks.mapNotNull { it.stack }.distinctBy { it.lowercase() }.sortedWith(NaturalOrder)

    fun notebooksIn(stack: String?): List<Notebook> =
        notebooks.filter { if (stack == null) it.stack == null else it.stack.equals(stack, true) }.sortedWith(compareBy(NaturalOrder) { it.name })

    /** Notes that link to note [id], by `[text](note:id)` or by its title in `[[Title]]`. */
    fun backlinks(id: String): List<Note> {
        val title = note(id)?.title?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        return liveNotes.filter { n ->
            n.id != id && (Links.targets(n.body).contains(id) || (title != null && Links.titles(n.body).contains(title)))
        }
    }

    /** The note a link in a note goes to: `note:<id>` or `title:<title>`; null for web links and missing notes. */
    fun linked(target: String): Note? = when {
        target.startsWith("note:") -> note(target.removePrefix("note:"))?.takeIf { !it.trashed }
        target.startsWith("title:") -> target.removePrefix("title:").trim().let { t -> liveNotes.firstOrNull { it.title.trim().equals(t, true) } }
        else -> null
    }

    fun isShortcut(s: Shortcut): Boolean = shortcuts.any { it.kind == s.kind && it.ref.equals(s.ref, s.kind == ShortcutKind.TAG || s.kind == ShortcutKind.STACK) }

    fun toJson(): JSONObject = JSONObject()
        .put("version", 2)
        .put("notes", JSONArray(notes.map { it.toJson() }))
        .put("notebooks", JSONArray(notebooks.map { it.toJson() }))
        .put("settings", settings.toJson())
        .put("tagInfo", JSONArray(tagInfo.map { it.toJson() }))
        .put("searches", JSONArray(searches.map { it.toJson() }))
        .put("shortcuts", JSONArray(shortcuts.map { it.toJson() }))
        .put("templates", JSONArray(templates.map { it.toJson() }))

    companion object {
        /** Reads Jotter's own file, or a Daybook backup (its tasks are left out). */
        fun fromJson(o: JSONObject): Data {
            val notebooks = o.optJSONArray("notebooks").objects().map(Notebook::fromJson).ifEmpty { listOf(Notebook.Default) }
            val notes = o.optJSONArray("notes").objects().map(Note::fromJson).map { n ->
                // A note whose notebook is gone goes to the first notebook.
                if (notebooks.any { it.id == n.notebookId }) n else n.copy(notebookId = notebooks.first().id)
            }
            return Data(
                notes = notes,
                notebooks = notebooks,
                settings = Settings.fromJson(o.optJSONObject("settings")),
                tagInfo = o.optJSONArray("tagInfo").objects().map(TagInfo::fromJson).filter { it.name.isNotBlank() },
                searches = o.optJSONArray("searches").objects().map(SavedSearch::fromJson),
                shortcuts = o.optJSONArray("shortcuts").objects().mapNotNull(Shortcut::fromJson),
                templates = o.optJSONArray("templates").objects().map(Template::fromJson),
            )
        }

        /** What a new install starts with: a welcome note that shows what notes can do. */
        fun firstRun(): Data = Data(
            notes = listOf(Note(title = "Welcome to Jotter", body = WELCOME_NOTE, pinned = true)),
        )

        private val WELCOME_NOTE = """
            Jotter keeps your **notes** in notebooks, with tags, checklists, photos, recordings and sketches.

            ## Writing
            - Tap the text (or the pencil) to edit
            - The toolbar has headings, lists, checklists, ==highlights==, tables, links and more
            - Notes save as you type, and **Note history** keeps earlier versions

            ## Checklists
            [x] Open this note
            [ ] Tap a box to tick it off
            [ ] Pin the notes you use most

            ## Finding things
            Group notes into *notebooks* and stacks, add **#tags**, and search everything. Searches like `tag:work todo:false` work as in Evernote.
        """.trimIndent()
    }
}

internal fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else List(length()) { getJSONObject(it) }

internal fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else List(length()) { getString(it) }.filter { it.isNotBlank() }
