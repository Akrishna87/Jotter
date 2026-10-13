package io.github.akrishna87.jotter

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.akrishna87.jotter.model.Data
import io.github.akrishna87.jotter.model.Markdown
import io.github.akrishna87.jotter.model.Note

/** Notifications for note reminders. Alarms are kept in step with the notes every time they change. */
object Reminders {
    private const val CHANNEL = "reminders"
    private const val PREFS = "reminders"
    private const val KEY_SCHEDULED = "scheduled"
    const val ACTION_REMIND = "io.github.akrishna87.jotter.REMIND"
    const val ACTION_DONE = "io.github.akrishna87.jotter.DONE"
    const val ACTION_SNOOZE = "io.github.akrishna87.jotter.SNOOZE"
    const val EXTRA_NOTE = "note"

    fun sync(context: Context, data: Data) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val before = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        val now = System.currentTimeMillis()
        val wanted = data.liveNotes.mapNotNull { n ->
            val at = n.reminderAt
            if (at != null && n.reminderDone == 0L && at > now) n.id to at else null
        }.toMap()
        for (id in before - wanted.keys) alarms.cancel(alarm(context, id))
        for ((id, at) in wanted) {
            runCatching {
                if (canScheduleExact(context)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm(context, id))
                else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm(context, id))
            }
        }
        prefs.edit().putStringSet(KEY_SCHEDULED, wanted.keys).apply()
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun alarm(context: Context, id: String): PendingIntent = PendingIntent.getBroadcast(
        context, id.hashCode(),
        Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            .setData(Uri.parse("jotter://reminder/$id"))
            .putExtra(EXTRA_NOTE, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun show(context: Context, note: Note) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Note reminders", NotificationManager.IMPORTANCE_HIGH).apply { description = "A notification when a note's reminder is due" })
        }
        fun action(act: String) = PendingIntent.getBroadcast(
            context, (note.id + act).hashCode(),
            Intent(context, ReminderReceiver::class.java).setAction(act).setData(Uri.parse("jotter://note/${note.id}/$act")).putExtra(EXTRA_NOTE, note.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            context, note.id.hashCode(),
            Intent(context, MainActivity::class.java).setData(Uri.parse("jotter://note/${note.id}")).putExtra(MainActivity.EXTRA_OPEN_NOTE, note.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = Markdown.plain(note.body, 300)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_jotter)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle(note.title.ifBlank { "Untitled note" })
            .setContentText(text.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "Mark as done", action(ACTION_DONE))
            .addAction(0, "Snooze 1 hour", action(ACTION_SNOOZE))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(note.id.hashCode(), n) }
    }
}

/** Shows due reminders, marks them done or snoozes them from the notification, and puts alarms back after a restart. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val id = intent.getStringExtra(Reminders.EXTRA_NOTE)
        when (intent.action) {
            Reminders.ACTION_REMIND -> {
                val n = Store.data.value.note(id) ?: return
                if (!n.trashed && n.reminderDone == 0L) Reminders.show(context, n)
            }
            Reminders.ACTION_DONE -> {
                if (id == null) return
                Store.reminderDone(id, true)
                NotificationManagerCompat.from(context).cancel(id.hashCode())
            }
            Reminders.ACTION_SNOOZE -> {
                if (id == null) return
                Store.setReminder(id, System.currentTimeMillis() + 60 * 60_000L)
                NotificationManagerCompat.from(context).cancel(id.hashCode())
            }
            else -> Reminders.sync(context, Store.data.value)
        }
    }
}
