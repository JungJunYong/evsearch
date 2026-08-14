package com.evsearch.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WidgetUpdateHelper {
    suspend fun updateAllWidgets(context: Context) {
        val widget = ChargerWidget()
        try {
            widget.updateAll(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Direct GlanceAppWidgetManager update for each active glance ID
        try {
            val glanceManager = GlanceAppWidgetManager(context)
            val glanceIds = glanceManager.getGlanceIds(ChargerWidget::class.java)
            glanceIds.forEach { glanceId ->
                try {
                    widget.update(context, glanceId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Force native AppWidgetManager update broadcast to all 3 receiver components
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val receivers = listOf(
                ChargerWidget4x1Receiver::class.java,
                ChargerWidget4x2Receiver::class.java,
                ChargerWidget4x3Receiver::class.java
            )
            for (receiver in receivers) {
                val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, receiver))
                if (ids != null && ids.isNotEmpty()) {
                    val intent = Intent(context, receiver).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                    context.sendBroadcast(intent)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

open class ChargerWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = ChargerWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
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
