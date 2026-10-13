package io.github.akrishna87.jotter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SavedSearch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.akrishna87.jotter.model.Data
import io.github.akrishna87.jotter.model.Markdown
import io.github.akrishna87.jotter.model.Note
import io.github.akrishna87.jotter.model.Search

/** Searches notes in Evernote's syntax, with suggestions for its terms, and keeps searches you want again. */
@Composable
fun SearchPage(data: Data, initial: String) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    var query by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    var saving by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val q = query.text.trim()
    val notes = if (q.isEmpty()) emptyList() else sortNotes(Search.run(q, data, today), data.settings.noteSort)
    val words = remember(q) { Search.words(q) }
    fun add(term: String) {
        val t = (query.text.trimEnd() + " " + term).trimStart()
        query = TextFieldValue(t, TextRange(t.length))
        runCatching { focus.requestFocus() }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        PageBar(onBack = { nav.pop() }, leading = {
            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            PlainField(
                value = query,
                onChange = { query = it },
                placeholder = "Search notes",
                description = "Search",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(10f).padding(start = 12.dp).focusRequester(focus),
                singleLine = true,
            )
        }) {
            if (query.text.isNotEmpty()) {
                IconButton(onClick = { saving = true }, Modifier.semantics { contentDescription = "Save search" }) { Icon(Icons.Rounded.SavedSearch, null) }
                IconButton(onClick = { query = TextFieldValue("") }, Modifier.semantics { contentDescription = "Clear search" }) { Icon(Icons.Rounded.Close, null) }
            }
        }
        // The search terms, a tap away.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, term) in listOf(
                "Unticked boxes" to "todo:true", "Has photos" to "resource:image/*", "Has files" to "resource:*", "Reminders" to "reminder:true",
                "This week" to "updated:week", "Today" to "updated:day", "Pinned" to "pinned:true",
            )) {
                FilterPill(label, false, small = true) { add(term) }
            }
            for (nb in data.notebooks) FilterPill("notebook:${nb.name}", false, small = true) { add("notebook:" + quote(nb.name)) }
            for (t in data.tags.take(12)) FilterPill("tag:$t", false, small = true) { add("tag:$t") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (q.isEmpty()) {
                if (data.searches.isNotEmpty()) {
                    item { SectionHeader("Saved searches") }
                    items(data.searches, key = { "s-" + it.id }) { s ->
                        ListRow(s.name, detail = s.query, leading = { Icon(Icons.Rounded.SavedSearch, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }, onClick = {
                            query = TextFieldValue(s.query, TextRange(s.query.length))
                        })
                    }
                }
                item { SearchHelp() }
            } else if (notes.isEmpty()) {
                item { EmptyState(Icons.Rounded.Search, "No results", "Nothing matches “$q”.") }
            }
            if (notes.isNotEmpty()) {
                item { SectionHeader(if (notes.size == 1) "1 note" else "${notes.size} notes") }
                items(notes, key = { "n-" + it.id }) { n -> SearchResult(n, data, words) }
            }
        }
    }
    if (saving) SaveSearchDialog(null, query.text.trim(), onDismiss = { saving = false })
}

private fun quote(name: String) = if (name.contains(' ')) "\"$name\"" else name

/** A found note with the words that matched in bold. */
@Composable
private fun SearchResult(n: Note, data: Data, words: List<String>) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    val text = Markdown.plain(n.body, Int.MAX_VALUE).replace('\n', ' ')
    // Show the text around the first match.
    val first = words.mapNotNull { w -> text.indexOf(w, ignoreCase = true).takeIf { it >= 0 } }.minOrNull() ?: 0
    val from = (first - 40).coerceAtLeast(0)
    val snippet = (if (from > 0) "…" else "") + text.substring(from, (from + 200).coerceAtMost(text.length))
    Column(Modifier.fillMaxWidth()) {
        NoteRow(n, data, showNotebook = true, today = today, modifier = Modifier.clickable { nav.open(n) })
        if (words.isNotEmpty() && text.isNotEmpty()) {
            Text(
                highlight(snippet, words),
                Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun highlight(text: String, words: List<String>) = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val hit = words.mapNotNull { w -> text.indexOf(w, i, ignoreCase = true).takeIf { it >= 0 }?.let { it to w.length } }.minByOrNull { it.first }
        if (hit == null) { append(text.substring(i)); break }
        append(text.substring(i, hit.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(hit.first, hit.first + hit.second)) }
        i = hit.first + hit.second
    }
}

@Composable
private fun SearchHelp() {
    val rows = listOf(
        "budget review" to "notes with both words",
        "\"budget review\"" to "the exact words",
        "bud*" to "words starting with bud",
        "-draft" to "leave out notes with draft",
        "any: milk eggs" to "notes with either word",
        "intitle:plan" to "plan in the title",
        "notebook:Work  stack:Office" to "in a notebook or stack",
        "tag:work  -tag:*" to "tagged work (and tags under it); no tags",
        "todo:true  todo:false" to "unticked boxes; ticked boxes",
        "created:day-7  updated:week" to "in the last 7 days; this week",
        "resource:image/*  resource:application/pdf" to "with photos; with PDFs",
        "reminder:true  reminder:done" to "reminders to do; done",
        "source:example.com" to "clipped or shared from a site",
    )
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text("Search like Evernote", style = MaterialTheme.typography.titleSmall)
        for ((term, about) in rows) {
            Row(Modifier.padding(top = 8.dp)) {
                Text(term, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = MaterialTheme.colorScheme.primary)
                Text(about, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
