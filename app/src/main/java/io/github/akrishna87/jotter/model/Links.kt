package io.github.akrishna87.jotter.model

/** Links between notes: `[text](note:<id>)`, which survives renaming, and `[[Note title]]`, which is quicker to type. */
object Links {
    private val idLink = Regex("\\]\\(note:([^)\\s]+)\\)")
    private val titleLink = Regex("\\[\\[([^\\]]+)]]")

    /** Ids of the notes [body] links to. */
    fun targets(body: String): Set<String> = idLink.findAll(body).map { it.groupValues[1] }.toSet()

    /** Titles (in lower case) that [body] links to with `[[…]]`. */
    fun titles(body: String): Set<String> = titleLink.findAll(body).map { it.groupValues[1].trim().lowercase() }.toSet()

    /** The text that links to [note]. */
    fun to(note: Note): String = "[${note.title.ifBlank { "Untitled" }.replace("[", "(").replace("]", ")")}](note:${note.id})"
}
