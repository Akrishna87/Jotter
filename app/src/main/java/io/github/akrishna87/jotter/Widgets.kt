package io.github.akrishna87.jotter

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import io.github.akrishna87.jotter.model.Dates
import io.github.akrishna87.jotter.model.Markdown
import java.time.LocalDate

/**
 * Home screen widgets: "Jotter: Quick note" (new note, checklist, photo, recording, sketch) and
 * "Jotter: Recent notes" (the latest notes, pinned first).
 */
object Widgets {
    fun refresh(context: Context) {
        val m = AppWidgetManager.getInstance(context) ?: return
        val recent = m.getAppWidgetIds(ComponentName(context, RecentNotesWidget::class.java))
        if (recent.isNotEmpty()) m.updateAppWidget(recent, renderRecent(context))
    }

    fun launch(context: Context, code: Int, build: Intent.() -> Unit): PendingIntent = PendingIntent.getActivity(
        context, code,
        Intent(context, MainActivity::class.java).apply(build).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun newNote(context: Context, kind: String, code: Int) = launch(context, code) {
        data = Uri.parse("jotter://new/$kind")
        putExtra(MainActivity.EXTRA_NEW, kind)
    }

    fun renderQuick(context: Context): RemoteViews = RemoteViews(context.packageName, R.layout.widget_quick).apply {
        setOnClickPendingIntent(R.id.quick_note, newNote(context, "note", 11))
        setOnClickPendingIntent(R.id.quick_checklist, newNote(context, "checklist", 12))
        setOnClickPendingIntent(R.id.quick_photo, newNote(context, "photo", 13))
        setOnClickPendingIntent(R.id.quick_audio, newNote(context, "audio", 14))
        setOnClickPendingIntent(R.id.quick_sketch, newNote(context, "sketch", 15))
    }

    fun renderRecent(context: Context): RemoteViews {
        Store.init(context)
        val d = Store.data.value
        val views = RemoteViews(context.packageName, R.layout.widget_recent)
        views.setOnClickPendingIntent(R.id.widget_title, launch(context, 20) { data = Uri.parse("jotter://notes") })
        views.setOnClickPendingIntent(R.id.widget_add, newNote(context, "note", 21))
        views.removeAllViews(R.id.widget_list)
        // A locked Jotter shows no note text on the home screen.
        val locked = d.settings.passcode != null
        val notes = d.liveNotes.sortedWith(compareByDescending<io.github.akrishna87.jotter.model.Note> { it.pinned }.thenByDescending { it.updatedAt }).take(6)
        views.setViewVisibility(R.id.widget_empty, if (notes.isEmpty() || locked) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.widget_empty, if (locked) "Jotter is locked" else "No notes yet. Tap + to write one.")
        if (!locked) {
            val today = LocalDate.now()
            for (n in notes) {
                val row = RemoteViews(context.packageName, R.layout.widget_note)
                row.setTextViewText(R.id.note_title, (if (n.pinned) "📌 " else "") + n.title.ifBlank { "Untitled" })
                row.setTextViewText(R.id.note_text, Dates.shortDate(n.updatedAt, today) + "  " + Markdown.plain(n.body, 120).replace('\n', ' '))
                row.setOnClickPendingIntent(R.id.note_row, launch(context, n.id.hashCode()) {
                    data = Uri.parse("jotter://note/${n.id}")
                    putExtra(MainActivity.EXTRA_OPEN_NOTE, n.id)
                })
                views.addView(R.id.widget_list, row)
            }
        }
        return views
    }
}

class QuickNoteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, Widgets.renderQuick(context))
    }
}

class RecentNotesWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, Widgets.renderRecent(context))
    }
}
