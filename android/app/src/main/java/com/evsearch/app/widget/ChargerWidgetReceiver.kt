package com.evsearch.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll

object WidgetUpdateHelper {
    suspend fun updateAllWidgets(context: Context) {
        val widget4x1 = ChargerWidget4x1()
        val widget4x2 = ChargerWidget4x2()
        val widget4x3 = ChargerWidget4x3()

        try {
            widget4x1.updateAll(context)
            widget4x2.updateAll(context)
            widget4x3.updateAll(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Direct GlanceAppWidgetManager update for each active glance ID
        try {
            val glanceManager = GlanceAppWidgetManager(context)
            glanceManager.getGlanceIds(ChargerWidget4x1::class.java).forEach {
                try { widget4x1.update(context, it) } catch (e: Exception) {}
            }
            glanceManager.getGlanceIds(ChargerWidget4x2::class.java).forEach {
                try { widget4x2.update(context, it) } catch (e: Exception) {}
            }
            glanceManager.getGlanceIds(ChargerWidget4x3::class.java).forEach {
                try { widget4x3.update(context, it) } catch (e: Exception) {}
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

    override val glanceAppWidget: GlanceAppWidget = ChargerWidget4x2()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleBackgroundWork(context)
    }

    companion object {
        /** 위젯 즉시 동기화 + 사용자 주기 체인 + 15분 안전망 등록. */
        fun scheduleBackgroundWork(context: Context) {
            WidgetSyncScheduler.scheduleAll(context)
        }
    }
}

class ChargerWidget4x1Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChargerWidget4x1()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ChargerWidgetReceiver.scheduleBackgroundWork(context)
    }
}

class ChargerWidget4x2Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChargerWidget4x2()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ChargerWidgetReceiver.scheduleBackgroundWork(context)
    }
}

class ChargerWidget4x3Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChargerWidget4x3()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ChargerWidgetReceiver.scheduleBackgroundWork(context)
    }
}
