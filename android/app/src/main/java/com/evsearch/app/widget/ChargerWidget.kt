package com.evsearch.app.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.evsearch.app.MainActivity
import com.evsearch.app.data.local.AppDatabase
import com.evsearch.app.data.local.SavedChargerEntity
import com.evsearch.app.data.model.ChargerStatus

class ChargerWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getInstance(context)
        val savedChargers = db.savedChargerDao().getAllSavedChargers()

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                ChargerWidgetContent(
                    context = context,
                    chargers = savedChargers,
                    size = size
                )
            }
        }
    }

    @Composable
    private fun ChargerWidgetContent(
        context: Context,
        chargers: List<SavedChargerEntity>,
        size: DpSize
    ) {
        val componentName = ComponentName(context, MainActivity::class.java)

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(
                    ColorProvider(day = Color(0xFF0B0D14), night = Color(0xFF0B0D14))
                )
                .clickable(actionStartActivity(componentName))
        ) {
            if (chargers.isEmpty()) {
                EmptyState()
            } else {
                when {
                    // 4x1 (Small) - height < 140dp
                    size.height < 140.dp -> SmallWidget(chargers.take(MAX_ITEMS))
                    // 4x2 (Medium) - 140 ~ 220dp
                    size.height < 220.dp -> MediumWidget(chargers.take(MAX_ITEMS))
                    // 4x3 (Large) - 220dp+
                    else -> LargeWidget(chargers.take(MAX_ITEMS))
                }
            }
        }
    }

    // ---------- SMALL: 4x1 — status dots only, no labels ----------
    @Composable
    private fun SmallWidget(chargers: List<SavedChargerEntity>) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            chargers.forEachIndexed { index, charger ->
                if (index > 0) {
                    Spacer(modifier = GlanceModifier.width(10.dp))
                }

                val statusColor = getStatusColor(ChargerStatus.fromString(charger.status))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status dot
                    StatusDot(color = statusColor, sizeDp = 26)
                }
            }
        }
    }

    // ---------- MEDIUM: 4x2 — dots + short row label ----------
    @Composable
    private fun MediumWidget(chargers: List<SavedChargerEntity>) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            chargers.forEachIndexed { index, charger ->
                if (index > 0) {
                    Spacer(modifier = GlanceModifier.width(12.dp))
                }

                val statusEnum = ChargerStatus.fromString(charger.status)
                val statusColor = getStatusColor(statusEnum)
                val shortName = charger.customName
                    ?: charger.stationName.take(4)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusDot(color = statusColor, sizeDp = 34)
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Text(
                        text = shortName,
                        style = TextStyle(
                            color = ColorProvider(day = Color(0xFFC7CEDD), night = Color(0xFFC7CEDD)),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = getStatusText(statusEnum),
                        style = TextStyle(
                            color = ColorProvider(day = statusColor, night = statusColor),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }

    // ---------- LARGE: 4x3 — detailed grid of up to 6 chargers ----------
    @Composable
    private fun LargeWidget(chargers: List<SavedChargerEntity>) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // --- Header ---
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚡ EV 충전소 알리미",
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFF00C896), night = Color(0xFF00C896)),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                val avCount = chargers.count { ChargerStatus.fromString(it.status) == ChargerStatus.AVAILABLE }
                Text(
                    text = "즉시 이용 가능 $avCount/${chargers.size}",
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFF94A3B8), night = Color(0xFF94A3B8)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            // --- Grid rows: split into up to 2 rows of 3 ----
            val rows: List<List<SavedChargerEntity>> = chargers.chunked(3)
            rows.forEachIndexed { rowIndex, rowItems ->
                if (rowIndex > 0) {
                    Spacer(modifier = GlanceModifier.height(10.dp))
                }

                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowItems.forEachIndexed { index, charger ->
                        if (index > 0) {
                            Spacer(modifier = GlanceModifier.width(10.dp))
                        }

                        val statusEnum = ChargerStatus.fromString(charger.status)
                        val statusColor = getStatusColor(statusEnum)
                        val displayName = charger.customName ?: charger.stationName
                        val spec = buildString {
                            append("#${charger.chgerId} · ${charger.chargerTypeName}")
                            if (!charger.outputKw.isNullOrBlank()) {
                                append(" ${charger.outputKw}kW")
                            }
                        }

                        Column(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .background(
                                    ColorProvider(day = Color(0xFF161A26), night = Color(0xFF161A26))
                                )
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = displayName,
                                style = TextStyle(
                                    color = ColorProvider(day = Color.White, night = Color.White),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )

                            Spacer(modifier = GlanceModifier.height(4.dp))

                            Text(
                                text = spec,
                                style = TextStyle(
                                    color = ColorProvider(day = Color(0xFF94A3B8), night = Color(0xFF94A3B8)),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                maxLines = 1
                            )

                            Spacer(modifier = GlanceModifier.height(6.dp))

                            // Status badge pill
                            Row(
                                modifier = GlanceModifier
                                    .background(
                                        ColorProvider(
                                            day = statusColor.copy(alpha = 0.16f),
                                            night = statusColor.copy(alpha = 0.16f)
                                        )
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = getStatusText(statusEnum),
                                    style = TextStyle(
                                        color = ColorProvider(day = statusColor, night = statusColor),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    // Fill empty slots in the row to keep widths balanced
                    val remaining = 3 - rowItems.size
                    if (remaining > 0) {
                        repeat(remaining) {
                            Spacer(modifier = GlanceModifier.width(10.dp))
                            Spacer(modifier = GlanceModifier.defaultWeight())
                        }
                    }
                }
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // --- Footer ---
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val lastFetched = chargers.firstOrNull()?.lastFetchedAt ?: ""
                Text(
                    text = "🔄 마지막 갱신 ${formatTimeOnly(lastFetched)}",
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFF64748B), night = Color(0xFF64748B)),
                        fontSize = 10.sp
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = "터치하여 앱 열기 →",
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFF00C896), night = Color(0xFF00C896)),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }

    // ---------- Shared UI parts ----------

    @Composable
    private fun EmptyState() {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⚡ 즐겨찾는 충전기가 없습니다",
                style = TextStyle(
                    color = ColorProvider(day = Color.White, night = Color.White),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = "앱에서 충전기를 즐겨찾기에 추가해주세요",
                style = TextStyle(
                    color = ColorProvider(day = Color(0xFF00C896), night = Color(0xFF00C896)),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }

    @Composable
    private fun StatusDot(color: Color, sizeDp: Int) {
        Box(
            modifier = GlanceModifier
                .size(sizeDp.dp)
                .background(
                    ColorProvider(day = color, night = color)
                )
        ) {}
    }

    // ---------- Helpers ----------

    private fun getStatusColor(status: ChargerStatus): Color {
        return when (status) {
            ChargerStatus.AVAILABLE -> Color(0xFF00C896)   // green teal
            ChargerStatus.CHARGING -> Color(0xFF3B82F6)    // blue
            ChargerStatus.COMM_ERROR -> Color(0xFFF97316)  // orange
            ChargerStatus.MAINTENANCE -> Color(0xFFEAB308) // yellow
            ChargerStatus.SUSPENDED -> Color(0xFFEF4444)   // red
            ChargerStatus.RESERVED -> Color(0xFFA855F7)    // purple
            ChargerStatus.UNCONFIRMED -> Color(0xFF64748B) // slate
            ChargerStatus.UNKNOWN -> Color(0xFF64748B)
        }
    }

    private fun getStatusText(status: ChargerStatus): String {
        return when (status) {
            ChargerStatus.AVAILABLE -> "대기"
            ChargerStatus.CHARGING -> "충전중"
            ChargerStatus.COMM_ERROR -> "장애"
            ChargerStatus.MAINTENANCE -> "점검"
            ChargerStatus.SUSPENDED -> "중지"
            ChargerStatus.RESERVED -> "예약"
            ChargerStatus.UNCONFIRMED -> "미확인"
            ChargerStatus.UNKNOWN -> "미확인"
        }
    }

    private fun formatTimeOnly(isoString: String): String {
        return try {
            if (isoString.contains("T")) {
                isoString.split("T")[1].substring(0, 5)
            } else {
                isoString
            }
        } catch (e: Exception) {
            isoString
        }
    }

    companion object {
        private const val MAX_ITEMS = 6
    }
}
