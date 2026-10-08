package com.daylight.app

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daylight.app.data.*
import com.daylight.app.notifications.NotificationEngine
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class NotificationEngineTest {
    private lateinit var db: DaylightDatabase
    private lateinit var context: Context
    @Before fun setup() { context = ApplicationProvider.getApplicationContext(); db = Room.inMemoryDatabaseBuilder(context, DaylightDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() { db.close() }
    @Test fun recoveryRebuildsPersistedSchedulesAndRegistersAlarm() = runBlocking {
        val s = Schedule(title = "Recover me", fixedMinutes = listOf(600), category = "Quotes")
        db.dao().put(s)
        db.dao().put(Preferences(quietEnabled = false))
        val engine = NotificationEngine(context, db)
        engine.reschedule()
        val before = db.dao().occurrences()
        assertTrue(before.isNotEmpty())
        assertNotNull(shadowOf(context.getSystemService(AlarmManager::class.java)).nextScheduledAlarm)
        // A new engine simulates losing all in-memory state after a process restart.
        NotificationEngine(context, db).reschedule()
        assertEquals(before, db.dao().occurrences())
        db.dao().put(s.copy(enabled = false))
        engine.reschedule()
        assertTrue(db.dao().occurrences().isEmpty())
        assertNull(shadowOf(context.getSystemService(AlarmManager::class.java)).nextScheduledAlarm)
    }
    @Test fun dueNotificationUsesUnseenContentAndMarksRotation() = runBlocking {
        db.dao().put(Preferences(quietEnabled = false))
        val older = Content(title = "Seen", body = "Seen before", category = "Motivation", lastShown = 1)
        val fresh = Content(title = "Unseen", body = "My exact words", category = "Motivation")
        db.dao().put(older); db.dao().put(fresh)
        db.dao().insertOccurrences(listOf(Occurrence("due", "test", System.currentTimeMillis() - 1000, "Content", title = "Reminder", category = "Motivation")))
        NotificationEngine(context, db).deliverDue()
        assertTrue(db.dao().content().first { it.id == fresh.id }.lastShown > 0)
        assertEquals(1L, db.dao().content().first { it.id == older.id }.lastShown)
        val notifications = shadowOf(context.getSystemService(android.app.NotificationManager::class.java)).allNotifications
        assertEquals(1, notifications.size)
        assertEquals("My exact words", notifications.first().extras.getCharSequence("android.text").toString())
    }
    @Test fun deletedResponseDoesNotProduceReviewNotification() = runBlocking {
        db.dao().put(Preferences(quietEnabled = false))
        db.dao().insertOccurrences(listOf(Occurrence("stale-review", "deleted", System.currentTimeMillis() - 1000, "Review", "response/deleted", "Evening review")))
        NotificationEngine(context, db).deliverDue()
        assertTrue(shadowOf(context.getSystemService(android.app.NotificationManager::class.java)).allNotifications.isEmpty())
    }
    @Test fun reminderIdsWithIdenticalHashCodesKeepDistinctNotificationsAndLinks() = runBlocking {
        assertEquals("FB".hashCode(), "Ea".hashCode())
        db.dao().put(Preferences(quietEnabled = false))
        db.dao().put(Content(title = "First", body = "First entry", category = "Motivation"))
        db.dao().put(Content(title = "Second", body = "Second entry", category = "Motivation"))
        val engine = NotificationEngine(context, db)
        listOf("FB", "Ea").forEach { id ->
            db.dao().insertOccurrences(listOf(Occurrence(id, "test", System.currentTimeMillis() - 1000, "Content", title = "Reminder", category = "Motivation")))
            engine.deliverDue()
        }
        val notifications = shadowOf(context.getSystemService(android.app.NotificationManager::class.java)).allNotifications
        assertEquals(2, notifications.size)
        assertNotEquals(notifications[0].contentIntent, notifications[1].contentIntent)
    }
}
