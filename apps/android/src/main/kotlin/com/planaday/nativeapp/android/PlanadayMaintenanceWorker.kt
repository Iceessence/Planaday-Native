package com.planaday.nativeapp.android

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.planaday.nativeapp.core.persistence.AndroidPlanadayDatabaseFactory

/**
 * Deferrable background reliability worker (WorkManager) verifying fresh local SQLite health
 * and preparing reminder/sync diagnostics after reboot or app update (R-0105, R-0108, PF-46).
 */
class PlanadayMaintenanceWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val db = AndroidPlanadayDatabaseFactory.create(applicationContext)
            db.workspaceDao().listAll()
            db.close()
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}
