package io.github.akrishna87.jotter

import android.content.Context
import io.github.akrishna87.jotter.model.DEFAULT_NOTEBOOK_ID
import io.github.akrishna87.jotter.model.Data
import io.github.akrishna87.jotter.model.Note
import io.github.akrishna87.jotter.model.Notebook
import io.github.akrishna87.jotter.model.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Jotter's notes, notebooks and settings, kept in memory and saved to one JSON file. Every
 * screen reads the same [data], so a change anywhere shows up everywhere at once.
 */
object Store {
    private val state = MutableStateFlow(Data())
    val data: StateFlow<Data> = state

    private val writer = Executors.newSingleThreadExecutor()
    private var file: File? = null
    private lateinit var app: Context

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        app = context.applicationContext
        val f = File(app.filesDir, "jotter.json")
        file = f
        val loaded = runCatching { if (f.exists()) Data.fromJson(JSONObject(f.readText())) else null }.getOrNull()
        state.value = loaded ?: Data.firstRun()
        if (loaded == null) write(state.value)
    }

    fun update(transform: (Data) -> Data) {
        val next = synchronized(this) { transform(state.value).also { state.value = it } }
        write(next)
    }

    /** Replaces everything, for restoring a backup. */
    fun replaceAll(d: Data) = update { d }

    private fun write(d: Data) {
        val f = file ?: return
        val json = d.toJson().toString()
        writer.execute {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json)
            if (!tmp.renameTo(f)) {
                f.writeText(json)
                tmp.delete()
            }
        }
    }

    // Notes

    /** Saves a note, or drops it when it was left empty. */
    fun saveNote(n: Note) = update { d ->
        val i = d.notes.indexOfFirst { it.id == n.id }
        val notes = when {
            n.isBlank -> d.notes.filter { it.id != n.id }
            i < 0 -> d.notes + n
            else -> d.notes.toMutableList().also { it[i] = n }
        }
        d.copy(notes = notes)
    }

    fun trashNote(id: String) = update { d ->
        d.copy(notes = d.notes.map { if (it.id == id) it.copy(trashedAt = System.currentTimeMillis(), pinned = false) else it })
    }

    fun restoreNote(id: String) = update { d ->
        d.copy(notes = d.notes.map { if (it.id == id) it.copy(trashedAt = 0L) else it })
    }

    /** Deletes a note for good. */
    fun deleteNoteForever(id: String) = update { d -> d.copy(notes = d.notes.filter { it.id != id }) }

    fun emptyTrash() = update { d -> d.copy(notes = d.notes.filter { !it.trashed }) }

    fun renameTag(old: String, new: String) = update { d ->
        d.copy(notes = d.notes.map { n -> n.copy(tags = n.tags.map { if (it.equals(old, true)) new else it }.distinctBy { it.lowercase() }) })
    }

    // Notebooks

    fun saveNotebook(nb: Notebook) = update { d ->
        val i = d.notebooks.indexOfFirst { it.id == nb.id }
        d.copy(notebooks = if (i < 0) d.notebooks + nb else d.notebooks.toMutableList().also { it[i] = nb })
    }

    /** Deletes a notebook; its notes move to the first remaining notebook. There's always at least one. */
    fun deleteNotebook(id: String) = update { d ->
        val rest = d.notebooks.filter { it.id != id }
        if (rest.isEmpty()) return@update d
        val target = rest.firstOrNull { it.id == DEFAULT_NOTEBOOK_ID } ?: rest.first()
        d.copy(notebooks = rest, notes = d.notes.map { if (it.notebookId == id) it.copy(notebookId = target.id) else it })
    }

    fun updateSettings(transform: (Settings) -> Settings) = update { d -> d.copy(settings = transform(d.settings)) }
}
