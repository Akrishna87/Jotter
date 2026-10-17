package io.github.akrishna87.jotter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.jotter.Store
import io.github.akrishna87.jotter.model.Data
import io.github.akrishna87.jotter.model.Dates
import io.github.akrishna87.jotter.model.Note
import io.github.akrishna87.jotter.model.Notebook
import java.time.LocalDate

/** Cover colours for notebooks, picked by id so a notebook keeps its colour. */
private val Covers = listOf(0xFFD9861C, 0xFF2F6BFF, 0xFF0F9D76, 0xFF7C3AED, 0xFFE5484D, 0xFF0E8A8A, 0xFFC2410C, 0xFFDB2777).map { Color(it) }

private fun coverOf(nb: Notebook): Color = Covers[Math.floorMod(nb.id.hashCode(), Covers.size)]

/**
 * The Notebooks tab: every notebook as a card, grouped by stack, with how many notes it holds and
 * its latest ones. Tapping a card opens the notebook; + there writes a note into it.
 */
@Composable
fun NotebooksScreen(data: Data, padding: PaddingValues) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    var newNotebook by remember { mutableStateOf(false) }
    val live = data.liveNotes
    val byBook = live.groupBy { data.notebook(it.notebookId).id }
    val defaultId = data.settings.defaultNotebookId ?: data.notebooks.minByOrNull { it.createdAt }?.id

    // Stacks first, then the notebooks in no stack; the "New notebook" card ends the last group.
    val groups = data.stacks.map { it to data.notebooksIn(it) } + listOf(null to data.notebooksIn(null))

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
    ) {
        item(key = "header") {
            val books = data.notebooks.size
            ScreenHeader("Notebooks", "${if (books == 1) "1 notebook" else "$books notebooks"} · ${if (live.size == 1) "1 note" else "${live.size} notes"}") {
                SearchAction { nav.push(Page.Search()) }
                CircleAction(Icons.Rounded.Add, "New notebook") { newNotebook = true }
            }
        }
        groups.forEachIndexed { gi, (stack, books) ->
            val cells: List<Notebook?> = if (gi == groups.lastIndex) books + null else books
            if (cells.isEmpty()) return@forEachIndexed
            if (stack != null || data.stacks.isNotEmpty()) {
                item(key = "stack-$gi") { SectionHeader(stack ?: "Other notebooks") }
            }
            items(cells.chunked(2), key = { row -> "row-$gi-" + row.joinToString { it?.id ?: "new" } }) { row ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    for (nb in row) {
                        if (nb == null) {
                            NewNotebookCard(Modifier.weight(1f)) { newNotebook = true }
                        } else {
                            NotebookCard(nb, byBook[nb.id].orEmpty(), nb.id == defaultId, today, Modifier.weight(1f)) {
                                nav.push(Page.NoteList(NoteFilter.InNotebook(nb.id)))
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    if (newNotebook) {
        NameDialog("New notebook", confirm = "Create", onSave = { name, _ ->
            val nb = Notebook(name = name)
            Store.saveNotebook(nb)
            nav.push(Page.NoteList(NoteFilter.InNotebook(nb.id)))
        }, onDismiss = { newNotebook = false })
    }
}

@Composable
private fun NotebookCard(nb: Notebook, notes: List<Note>, isDefault: Boolean, today: LocalDate, modifier: Modifier, onClick: () -> Unit) {
    val k = LocalKit.current
    val (coverBg, coverFg) = tileColors(coverOf(nb))
    val shape = RoundedCornerShape(20.dp)
    val latest = notes.sortedByDescending { it.updatedAt }
    val count = if (notes.size == 1) "1 note" else "${notes.size} notes"
    Column(
        modifier
            .softShadow(shape)
            .clip(shape)
            .background(k.card)
            .clickable(onClickLabel = "Open notebook", onClick = onClick)
            .semantics { contentDescription = "Notebook ${nb.name}, $count" },
    ) {
        Box(Modifier.fillMaxWidth().height(72.dp).background(coverBg)) {
            // The spine of the book.
            Box(Modifier.padding(start = 14.dp).size(width = 4.dp, height = 72.dp).background(coverFg.copy(alpha = 0.35f)))
            Icon(Icons.Rounded.Book, null, Modifier.align(Alignment.BottomEnd).padding(12.dp).size(28.dp), tint = coverFg)
            if (isDefault) {
                Text(
                    "Default",
                    Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(8.dp)).background(k.card.copy(alpha = 0.85f)).padding(horizontal = 7.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = coverFg,
                )
            }
        }
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp).heightIn(min = 92.dp)) {
            Text(nb.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(count, style = MaterialTheme.typography.bodySmall, color = k.sub)
            Spacer(Modifier.height(8.dp))
            if (latest.isEmpty()) {
                Text("No notes yet", style = MaterialTheme.typography.bodySmall, color = k.sub)
            } else {
                for (n in latest.take(2)) {
                    Text("· " + n.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                Text("Updated " + Dates.shortDate(latest.first().updatedAt, today), style = MaterialTheme.typography.labelSmall, color = k.sub)
            }
        }
    }
}

@Composable
private fun NewNotebookCard(modifier: Modifier, onClick: () -> Unit) {
    val k = LocalKit.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .heightIn(min = 196.dp)
            .clip(shape)
            .border(1.5.dp, k.line, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconTile(Icons.Rounded.Add, MaterialTheme.colorScheme.primary, size = 44.dp)
        Spacer(Modifier.height(10.dp))
        Text("Create notebook", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = k.sub)
    }
}
