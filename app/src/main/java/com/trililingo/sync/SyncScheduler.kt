package com.trililingo.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class SyncScheduler(private val ctx: Context) {

    fun ensureScheduled() {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "trililingo_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            req
        )
    }
}
