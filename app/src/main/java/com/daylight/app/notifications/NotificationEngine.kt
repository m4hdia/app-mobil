package com.daylight.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.daylight.app.MainActivity
import com.daylight.app.R
import com.daylight.app.data.*
import com.daylight.app.domain.FormLogic
import com.daylight.app.domain.SchedulePlanner
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.room.withTransaction
import java.time.*

class NotificationEngine(private val context: Context, private val db: DaylightDatabase) {
    private val dao = db.dao()
    private val mutex = Mutex()
    private val alarms = context.getSystemService(AlarmManager::class.java)
    val exactAllowed: Boolean get() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    val notificationsAllowed: Boolean get() = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(context, 1, Intent(context, AlarmReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    suspend fun reschedule() = mutex.withLock { rebuild() }

    @SuppressLint("ScheduleExactAlarm") // exactAllowed checks access; a revocation race falls back safely.
    private suspend fun rebuild() {
        val now = Instant.now()
        val prefs = dao.preferences() ?: Preferences()
        val schedules = dao.schedules()
        // A change of quiet hours can make a random window impossible. Pause that reminder
        // visibly rather than breaking every other reminder or silently underfilling it.
        val usable = schedules.filter { s ->
            if (!s.enabled) false else runCatching {
                repeat(8) { SchedulePlanner.dayTimes(s, now.atZone(ZoneId.systemDefault()).toLocalDate().plusDays(it.toLong()), ZoneId.systemDefault(), prefs) }
            }.isSuccess.also { valid -> if (!valid) dao.put(s.copy(enabled = false)) }
        }
        val planned = SchedulePlanner.plan(usable, dao.responses(), prefs, now, ZoneId.systemDefault())
        db.withTransaction {
            dao.clearPending()
            dao.pruneOccurrences(now.minus(Duration.ofDays(30)).toEpochMilli())
            dao.insertOccurrences(planned)
        }
        val next = dao.occurrences().firstOrNull { it.state == "pending" }
        alarms.cancel(alarmIntent())
        if (next != null) {
            try {
                if (exactAllowed) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at, alarmIntent())
                else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at, alarmIntent())
            } catch (_: SecurityException) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at, alarmIntent())
            }
        }
    }

    suspend fun deliverDue() = mutex.withLock {
        val now = System.currentTimeMillis()
        val prefs = dao.preferences() ?: Preferences()
        val local = ZonedDateTime.now()
        val quiet = SchedulePlanner.quiet(local.hour * 60 + local.minute, prefs)
        val due = dao.occurrences().filter { it.state == "pending" && it.at <= now }
        // Never emit a burst after Doze or a long outage. Older than an hour is stale.
        val latest = due.lastOrNull { now - it.at <= 3_600_000 }
        due.forEach { event ->
            if (event != latest || quiet || !notificationsAllowed) dao.claim(event.id, "skipped")
            else if (dao.claim(event.id, "delivered") == 1) {
                runCatching { post(event, prefs, now) }.onFailure { dao.markOccurrence(event.id, "failed") }
            }
        }
        rebuild()
    }

    @SuppressLint("MissingPermission") // notificationsAllowed checks POST_NOTIFICATIONS immediately before notify.
    private suspend fun post(event: Occurrence, prefs: Preferences, now: Long) {
        if (event.target == "Review" && dao.responses().none { "response/${it.id}" == event.route }) return
        if (event.target == "Form" && dao.forms().none { "fill/${it.id}" == event.route }) return
        val item = if (event.target == "Content") dao.content().filter { it.enabled && it.category == event.category }.minWithOrNull(compareBy<Content> { it.lastShown }.thenBy { it.createdAt }) else null
        if (event.target == "Content" && item == null) return
        val body = when (event.target) {
            "Review" -> "Here is your morning plan:\n\n" + FormLogic.summary(dao.answers().filter { "response/${it.responseId}" == event.route })
            "Form" -> "Take a quiet moment. What would you like to accomplish today?"
            else -> listOfNotNull(item?.body, item?.translation?.takeIf { it.isNotBlank() }, item?.source?.takeIf { it.isNotBlank() }, item?.reference?.takeIf { it.isNotBlank() }).joinToString("\n\n")
        }
        val route = item?.let { "content/${it.id}" } ?: event.route
        val channel = "daylight_${prefs.sound}_${prefs.vibration}"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel, "Reminders · ${if (prefs.sound) "sound" else "silent"}${if (prefs.vibration) " · vibration" else ""}", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Your personal content and reflection reminders"
            enableVibration(prefs.vibration)
            if (!prefs.sound) setSound(null, null)
        })
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("daylight://notification/${Uri.encode(event.id)}")
            putExtra("route", route); flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = item?.let { when(it.category) { "Quran" -> "Quran reminder · ${it.title}"; "Hadith" -> "Hadith reminder · ${it.title}"; else -> it.title } } ?: event.title
        val builder = NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).addAction(0, "Open", open)
        if (item != null) {
            fun action(name: String): PendingIntent = PendingIntent.getBroadcast(context, 0, Intent(context, ActionReceiver::class.java).apply {
                data = Uri.parse("daylight://action/${Uri.encode(event.id)}/$name")
                action = name; putExtra("contentId", item.id); putExtra("notificationTag", event.id)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(0, "Favorite", action("favorite")).addAction(0, "Mark as read", action("read"))
        }
        if (notificationsAllowed) {
            NotificationManagerCompat.from(context).notify(event.id, 1, builder.build())
            item?.let { dao.shown(it.id, now) }
        }
    }
}
