package io.github.akrishna87.jotter.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

const val DEFAULT_NOTEBOOK_ID = "notebook-default"

data class Notebook(
    val id: String = newId(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("createdAt", createdAt)

    companion object {
        val Default = Notebook(DEFAULT_NOTEBOOK_ID, "My notebook", 0L)

        fun fromJson(o: JSONObject) = Notebook(
            id = o.optString("id").ifEmpty { newId() },
            name = o.optString("name"),
            createdAt = o.optLong("createdAt"),
        )
    }
}

data class Note(
    val id: String = newId(),
    val title: String = "",
    /** Light Markdown: headings, bullets, numbered lists, "[ ]" checklists, **bold**, *italic*, ~~strike~~. */
    val body: String = "",
    val notebookId: String = DEFAULT_NOTEBOOK_ID,
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    /** When it was moved to the trash; 0 if it isn't there. */
    val trashedAt: Long = 0L,
) {
    val isBlank: Boolean get() = title.isBlank() && body.isBlank()
    val trashed: Boolean get() = trashedAt != 0L

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
            )
        }
    }
}

enum class ThemeMode(val label: String) { SYSTEM("System default"), LIGHT("Light"), DARK("Dark") }

enum class NoteSort(val label: String) { UPDATED("Date updated"), CREATED("Date created"), TITLE("Title") }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val noteSort: NoteSort = NoteSort.UPDATED,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("theme", theme.name)
        .put("noteSort", noteSort.name)

    companion object {
        fun fromJson(o: JSONObject?): Settings {
            val d = Settings()
            if (o == null) return d
            return Settings(
                theme = runCatching { ThemeMode.valueOf(o.optString("theme")) }.getOrDefault(d.theme),
                noteSort = runCatching { NoteSort.valueOf(o.optString("noteSort")) }.getOrDefault(d.noteSort),
            )
        }
    }
}

/** Everything Jotter keeps, saved as one JSON file (and exported as the same JSON for backups). */
data class Data(
    val notes: List<Note> = emptyList(),
    val notebooks: List<Notebook> = listOf(Notebook.Default),
    val settings: Settings = Settings(),
) {
    fun notebook(id: String?): Notebook = notebooks.firstOrNull { it.id == id } ?: notebooks.firstOrNull() ?: Notebook.Default

    fun note(id: String?): Note? = notes.firstOrNull { it.id == id }

    /** Every tag used on a note that isn't in the trash, alphabetically. */
    val tags: List<String> get() = notes.filter { !it.trashed }.flatMap { it.tags }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }

    fun toJson(): JSONObject = JSONObject()
        .put("version", 1)
        .put("notes", JSONArray(notes.map { it.toJson() }))
        .put("notebooks", JSONArray(notebooks.map { it.toJson() }))
        .put("settings", settings.toJson())

    companion object {
        /** Reads Jotter's own file, or a Daybook backup (its tasks are left out). */
        fun fromJson(o: JSONObject): Data {
            val notebooks = o.optJSONArray("notebooks").objects().map(Notebook::fromJson).ifEmpty { listOf(Notebook.Default) }
            val notes = o.optJSONArray("notes").objects().map(Note::fromJson).map { n ->
                // A note whose notebook is gone goes to the first notebook.
                if (notebooks.any { it.id == n.notebookId }) n else n.copy(notebookId = notebooks.first().id)
            }
            return Data(notes = notes, notebooks = notebooks, settings = Settings.fromJson(o.optJSONObject("settings")))
        }

        /** What a new install starts with: a welcome note that shows what notes can do. */
        fun firstRun(): Data = Data(
            notes = listOf(Note(title = "Welcome to Jotter", body = WELCOME_NOTE, pinned = true)),
        )

        private val WELCOME_NOTE = """
            Jotter keeps your **notes** in notebooks, with tags and checklists.

            ## Writing
            - Tap the text (or the pencil) to edit
            - Use the toolbar for headings, lists and checklists
            - Notes save as you type

            ## Checklists
            [x] Open this note
            [ ] Tap a box to tick it off
            [ ] Pin the notes you use most

            ## Finding things
            Group notes into *notebooks*, add **#tags**, and search everything with the magnifier.
        """.trimIndent()
    }
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else List(length()) { getJSONObject(it) }

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else List(length()) { getString(it) }.filter { it.isNotBlank() }
