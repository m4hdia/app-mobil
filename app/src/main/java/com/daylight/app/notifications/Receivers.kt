package com.daylight.app.notifications

import android.content.*
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.daylight.app.DaylightApplication
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as DaylightApplication
        app.scope.launch {
            try { app.engine.deliverDue() }
            catch (_: Exception) { RecoveryWorker.enqueue(context) }
            finally { pending.finish() }
        }
    }
}

class RecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { RecoveryWorker.enqueue(context) }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("contentId") ?: return
        val pending = goAsync()
        val app = context.applicationContext as DaylightApplication
        app.scope.launch {
            try {
                if (intent.action == "favorite") app.repository.dao.favorite(id)
                if (intent.action == "read") app.repository.dao.read(id, System.currentTimeMillis())
                intent.getStringExtra("notificationTag")?.let { NotificationManagerCompat.from(context).cancel(it, 1) }
            } catch (_: Exception) {
                // Keep the notification available so the user can open the entry and retry.
            } finally { pending.finish() }
        }
    }
}

class RecoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        (applicationContext as DaylightApplication).engine.reschedule()
        Result.success()
    } catch (_: Exception) { Result.retry() }
    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("daylight-rebuild", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<RecoveryWorker>().build())
        }
    }
}
