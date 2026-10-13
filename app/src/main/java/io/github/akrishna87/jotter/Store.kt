package io.github.akrishna87.jotter

import android.content.Context
import io.github.akrishna87.jotter.model.Attachment
import io.github.akrishna87.jotter.model.DEFAULT_NOTEBOOK_ID
import io.github.akrishna87.jotter.model.Data
import io.github.akrishna87.jotter.model.Note
import io.github.akrishna87.jotter.model.Notebook
import io.github.akrishna87.jotter.model.SavedSearch
import io.github.akrishna87.jotter.model.Settings
import io.github.akrishna87.jotter.model.Shortcut
import io.github.akrishna87.jotter.model.ShortcutKind
import io.github.akrishna87.jotter.model.TagInfo
import io.github.akrishna87.jotter.model.Template
import io.github.akrishna87.jotter.model.Version
import io.github.akrishna87.jotter.model.newId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Jotter's notes, notebooks and settings, kept in memory and saved to one JSON file. Every
 * screen reads the same [data], so a change anywhere shows up everywhere at once. Reminders and
 * widgets are brought up to date after each change.
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
        val before = state.value
        val next = synchronized(this) { transform(state.value).also { state.value = it } }
        if (next === before) return
        write(next)
        if (::app.isInitialized) {
            if (next.notes !== before.notes) {
                Reminders.sync(app, next)
                Widgets.refresh(app)
            }
        }
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

    private fun Data.withNote(id: String, change: (Note) -> Note): Data =
        copy(notes = notes.map { if (it.id == id) change(it) else it })

    // Notes

    /** Saves a note (keeping its history), or drops a new one that was left empty. */
    fun saveNote(n: Note) = update { d ->
        val i = d.notes.indexOfFirst { it.id == n.id }
        val notes = when {
            n.isBlank && i < 0 -> d.notes
            i < 0 -> d.notes + n
            else -> d.notes.toMutableList().also { it[i] = d.notes[i].edited(n, System.currentTimeMillis()) }
        }
        d.copy(notes = notes)
    }

    /** Adds notes as they are (imports, shared text, duplicates). */
    fun addNotes(notes: List<Note>) = update { d -> d.copy(notes = d.notes + notes) }

    fun setNotes(ids: Collection<String>, change: (Note) -> Note) = update { d ->
        d.copy(notes = d.notes.map { if (it.id in ids) change(it) else it })
    }

    fun trashNotes(ids: Collection<String>) = setNotes(ids) { it.copy(trashedAt = System.currentTimeMillis(), pinned = false) }

    fun trashNote(id: String) = trashNotes(listOf(id))

    fun restoreNote(id: String) = setNotes(listOf(id)) { it.copy(trashedAt = 0L) }

    /** Deletes a note for good, with its files. */
    fun deleteNoteForever(id: String) {
        val gone = data.value.note(id)
        update { d -> d.copy(notes = d.notes.filter { it.id != id }, shortcuts = d.shortcuts.filterNot { it.kind == ShortcutKind.NOTE && it.ref == id }) }
        gone?.attachments?.forEach { Files.delete(app, it) }
    }

    fun emptyTrash() {
        val gone = data.value.notes.filter { it.trashed }
        update { d -> d.copy(notes = d.notes.filter { !it.trashed }) }
        gone.flatMap { it.attachments }.forEach { Files.delete(app, it) }
    }

    fun setPinned(id: String, pinned: Boolean) = setNotes(listOf(id)) { it.copy(pinned = pinned) }

    fun setReminder(id: String, at: Long?) = setNotes(listOf(id)) { it.copy(reminderAt = at, reminderDone = 0L) }

    fun reminderDone(id: String, done: Boolean) = setNotes(listOf(id)) { it.copy(reminderDone = if (done) System.currentTimeMillis() else 0L) }

    fun toggleCheck(id: String, line: Int) = update { d ->
        d.withNote(id) { it.copy(body = io.github.akrishna87.jotter.model.Markdown.toggleCheck(it.body, line), updatedAt = System.currentTimeMillis()) }
    }

    fun duplicate(id: String): Note? {
        val n = data.value.note(id) ?: return null
        val now = System.currentTimeMillis()
        // The copy gets its own copies of the files, so deleting one note leaves the other's.
        val copies = n.attachments.associateWith { a -> Files.bytes(app, a)?.let { Files.write(app, a.name, a.mime, it) } }
        var body = n.body
        val atts = n.attachments.mapNotNull { a -> copies[a]?.also { c -> body = body.replace("attachment:${a.id}", "attachment:${c.id}") } }
        val copy = n.copy(id = newId(), title = n.title.ifBlank { "Untitled" } + " (copy)", body = body, attachments = atts, createdAt = now, updatedAt = now, pinned = false, history = emptyList())
        addNotes(listOf(copy))
        return copy
    }

    /** Joins notes into the first one, in the order given; the others go to the trash. */
    fun merge(ids: List<String>): String? {
        val d = data.value
        val notes = ids.mapNotNull { d.note(it) }
        if (notes.size < 2) return null
        val first = notes.first()
        val body = notes.joinToString("\n\n---\n\n") { n -> if (n === first) n.body.trimEnd() else "## ${n.title.ifBlank { "Untitled" }}\n\n${n.body.trimEnd()}" }
        val merged = first.copy(
            body = body,
            tags = notes.flatMap { it.tags }.distinctBy { it.lowercase() },
            attachments = notes.flatMap { it.attachments },
            updatedAt = System.currentTimeMillis(),
        )
        update { x ->
            val now = System.currentTimeMillis()
            x.copy(notes = x.notes.map { n ->
                when {
                    n.id == first.id -> first.edited(merged, now)
                    n.id in ids -> n.copy(trashedAt = now, pinned = false, attachments = emptyList(), body = n.body.replace(Regex("!\\[[^]]*]\\(attachment:[^)]+\\)\\n?"), ""))
                    else -> n
                }
            })
        }
        return first.id
    }

    fun restoreVersion(id: String, v: Version) = update { d ->
        d.withNote(id) { n -> n.edited(n.copy(title = v.title, body = v.body, updatedAt = System.currentTimeMillis()), Long.MAX_VALUE) }
    }

    fun addAttachments(id: String, atts: List<Attachment>, lines: String) = update { d ->
        d.withNote(id) { n -> n.copy(attachments = n.attachments + atts, body = (n.body.trimEnd() + "\n" + lines).trim(), updatedAt = System.currentTimeMillis()) }
    }

    fun removeAttachment(id: String, a: Attachment) {
        update { d ->
            d.withNote(id) { n ->
                n.copy(
                    attachments = n.attachments.filter { it.id != a.id },
                    body = n.body.lines().filterNot { it.trim().endsWith("(attachment:${a.id})") && it.trim().startsWith("![") }.joinToString("\n"),
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }
        Files.delete(app, a)
    }

    // Tags

    fun renameTag(old: String, new: String) = update { d ->
        val name = new.trim().removePrefix("#").replace(" ", "-")
        if (name.isEmpty()) return@update d
        d.copy(
            notes = d.notes.map { n -> n.copy(tags = n.tags.map { if (it.equals(old, true)) name else it }.distinctBy { it.lowercase() }) },
            tagInfo = d.tagInfo.map { t ->
                TagInfo(if (t.name.equals(old, true)) name else t.name, if (t.parent.equals(old, true)) name else t.parent)
            },
            shortcuts = d.shortcuts.map { if (it.kind == ShortcutKind.TAG && it.ref.equals(old, true)) it.copy(ref = name) else it },
        )
    }

    /** Takes a tag off every note; tags nested under it move up a level. */
    fun deleteTag(tag: String) = update { d ->
        val parent = d.parentOf(tag)
        d.copy(
            notes = d.notes.map { n -> n.copy(tags = n.tags.filterNot { it.equals(tag, true) }) },
            tagInfo = d.tagInfo.filterNot { it.name.equals(tag, true) }.map { if (it.parent.equals(tag, true)) it.copy(parent = parent) else it },
            shortcuts = d.shortcuts.filterNot { it.kind == ShortcutKind.TAG && it.ref.equals(tag, true) },
        )
    }

    /** Nests [tag] under [parent] (or moves it to the top for null). A tag can't go under itself or its own children. */
    fun setTagParent(tag: String, parent: String?) = update { d ->
        if (parent != null && parent.lowercase() in d.tagAndChildren(tag)) return@update d
        val rest = d.tagInfo.filterNot { it.name.equals(tag, true) }
        d.copy(tagInfo = rest + TagInfo(tag, parent))
    }

    fun addTag(tag: String, parent: String?) = update { d ->
        val name = tag.trim().removePrefix("#").replace(" ", "-")
        if (name.isEmpty() || d.tags.any { it.equals(name, true) }) d else d.copy(tagInfo = d.tagInfo + TagInfo(name, parent))
    }

    fun tagNotes(ids: Collection<String>, add: List<String>, remove: List<String> = emptyList()) = setNotes(ids) { n ->
        n.copy(tags = (n.tags.filterNot { t -> remove.any { it.equals(t, true) } } + add).distinctBy { it.lowercase() })
    }

    // Notebooks and stacks

    fun saveNotebook(nb: Notebook) = update { d ->
        val i = d.notebooks.indexOfFirst { it.id == nb.id }
        d.copy(notebooks = if (i < 0) d.notebooks + nb else d.notebooks.toMutableList().also { it[i] = nb })
    }

    /** Deletes a notebook; its notes move to the trash with it, as in Evernote. There's always at least one notebook. */
    fun deleteNotebook(id: String) = update { d ->
        val rest = d.notebooks.filter { it.id != id }
        if (rest.isEmpty()) return@update d
        val target = rest.firstOrNull { it.id == DEFAULT_NOTEBOOK_ID } ?: rest.first()
        val now = System.currentTimeMillis()
        d.copy(
            notebooks = rest,
            notes = d.notes.map { if (it.notebookId == id) it.copy(notebookId = target.id, trashedAt = if (it.trashed) it.trashedAt else now, pinned = false) else it },
            shortcuts = d.shortcuts.filterNot { it.kind == ShortcutKind.NOTEBOOK && it.ref == id },
            settings = if (d.settings.defaultNotebookId == id) d.settings.copy(defaultNotebookId = null) else d.settings,
        )
    }

    fun renameStack(old: String, new: String?) = update { d ->
        d.copy(
            notebooks = d.notebooks.map { if (it.stack.equals(old, true)) it.copy(stack = new?.trim()?.ifEmpty { null }) else it },
            shortcuts = d.shortcuts.mapNotNull { s ->
                if (s.kind == ShortcutKind.STACK && s.ref.equals(old, true)) new?.trim()?.ifEmpty { null }?.let { s.copy(ref = it) } else s
            },
        )
    }

    fun moveNotes(ids: Collection<String>, notebookId: String) = setNotes(ids) { it.copy(notebookId = notebookId) }

    // Searches, shortcuts and templates

    fun saveSearch(s: SavedSearch) = update { d ->
        val i = d.searches.indexOfFirst { it.id == s.id }
        d.copy(searches = if (i < 0) d.searches + s else d.searches.toMutableList().also { it[i] = s })
    }

    fun deleteSearch(id: String) = update { d ->
        d.copy(searches = d.searches.filter { it.id != id }, shortcuts = d.shortcuts.filterNot { it.kind == ShortcutKind.SEARCH && it.ref == id })
    }

    fun toggleShortcut(s: Shortcut) = update { d ->
        if (d.isShortcut(s)) d.copy(shortcuts = d.shortcuts.filterNot { it.kind == s.kind && it.ref.equals(s.ref, true) })
        else d.copy(shortcuts = d.shortcuts + s)
    }

    fun moveShortcut(from: Int, to: Int) = update { d ->
        if (from !in d.shortcuts.indices || to !in d.shortcuts.indices) return@update d
        d.copy(shortcuts = d.shortcuts.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun saveTemplate(t: Template) = update { d ->
        val i = d.templates.indexOfFirst { it.id == t.id }
        d.copy(templates = if (i < 0) d.templates + t else d.templates.toMutableList().also { it[i] = t })
    }

    fun deleteTemplate(id: String) = update { d -> d.copy(templates = d.templates.filter { it.id != id }) }

    fun updateSettings(transform: (Settings) -> Settings) = update { d -> d.copy(settings = transform(d.settings)) }
}
