package com.evsearch.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

open class ChargerWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = ChargerWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Schedule WorkManager 15-minute background refresh task
        scheduleBackgroundWork(context)
    }

    companion object {
        fun scheduleBackgroundWork(context: Context) {
            val workManager = WorkManager.getInstance(context)

            // 1) Trigger immediate one-time sync to populate latest status on home screen right away
            val immediateWork = OneTimeWorkRequestBuilder<ChargerWidgetWorker>().build()
            workManager.enqueueUniqueWork(
                "ChargerWidgetImmediateSync",
                ExistingWorkPolicy.REPLACE,
                immediateWork
            )

            // 2) Schedule recurring 15-minute periodic background refresh
            val periodicWork = PeriodicWorkRequestBuilder<ChargerWidgetWorker>(
                15, TimeUnit.MINUTES
            ).build()

            workManager.enqueueUniquePeriodicWork(
                "ChargerWidgetUpdateWork",
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicWork
            )
        }
    }
}

class ChargerWidget4x1Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChargerWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ChargerWidgetReceiver.scheduleBackgroundWork(context)
    }
}

class ChargerWidget4x2Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChargerWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ChargerWidgetReceiver.scheduleBackgroundWork(context)
    }
}

class ChargerWidget4x3Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChargerWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ChargerWidgetReceiver.scheduleBackgroundWork(context)
    }
}
