package com.daylight.app

import android.app.Application
import androidx.work.*
import com.daylight.app.data.*
import com.daylight.app.notifications.*
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class DaylightApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { DaylightDatabase.create(this) }
    val repository by lazy { Repository(database) }
    val backup by lazy { BackupRepository(database) }
    val engine by lazy { NotificationEngine(this, database) }
    override fun onCreate() {
        super.onCreate()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("daylight-recovery", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RecoveryWorker>(12, TimeUnit.HOURS).build())
    }
}
