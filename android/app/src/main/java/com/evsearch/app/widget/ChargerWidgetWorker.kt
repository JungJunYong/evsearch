package com.evsearch.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.evsearch.app.data.api.BffApiService
import com.evsearch.app.data.local.AppDatabase
import com.evsearch.app.data.repository.ChargerRepository

class ChargerWidgetWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getInstance(context)
            val apiService = BffApiService.create()
            val repository = ChargerRepository(apiService, db.savedChargerDao(), context)

            // Fetch fresh status from BFF
            val result = repository.refreshSavedChargersStatus()

            // Update Glance Widget UI
            ChargerWidget().updateAll(context)

            if (result.isSuccess) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
