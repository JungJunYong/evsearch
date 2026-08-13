package com.evsearch.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.ExistingPeriodicWorkPolicy
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
            val workRequest = PeriodicWorkRequestBuilder<ChargerWidgetWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "ChargerWidgetUpdateWork",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
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
