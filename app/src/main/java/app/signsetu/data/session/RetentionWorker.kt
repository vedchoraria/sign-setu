package app.signsetu.data.session

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.signsetu.SignSetuApp
import app.signsetu.allSessionsExpired
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * The database key can only be used after the officer authenticates, so a background job cannot delete single rows.
 * When even the newest session is past the retention period, every session is, and the job destroys the key and the
 * database file instead. Older sessions inside a still-current set are purged at the next unlock.
 */
class RetentionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as SignSetuApp).container
        val settings = container.settings.current()
        if (allSessionsExpired(System.currentTimeMillis(), settings.newestSessionAt, settings.retentionHours)) {
            // lock() touches the link and the UI's state, which live on the main thread.
            withContext(Dispatchers.Main) { container.clearAllData() }
            container.settings.setNewestSessionAt(0L)
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "retention",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.HOURS).build(),
            )
        }
    }
}
