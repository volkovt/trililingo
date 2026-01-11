package com.trililingo.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.trililingo.data.db.TrililingoDb
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val db: TrililingoDb
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val pending = db.syncEventDao().getPending(limit = 50)
        pending.forEach { ev ->
            db.syncEventDao().setState(ev.eventId, "SENT")
        }
        return Result.success()
    }
}
