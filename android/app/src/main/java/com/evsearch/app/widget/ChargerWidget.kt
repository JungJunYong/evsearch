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
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
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
import com.evsearch.app.R
import com.evsearch.app.data.api.BffApiService
import com.evsearch.app.data.local.AppDatabase
import com.evsearch.app.data.local.SavedChargerEntity
import com.evsearch.app.data.model.ChargerStatus
import com.evsearch.app.data.repository.ChargerRepository

class ChargerWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getInstance(context)
        val dbSaved = db.savedChargerDao().getAllSavedChargers()

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                if (dbSaved.isEmpty()) {
                    EmptyWidgetContent(context = context)
                } else {
                    ChargerWidgetContent(
                        context = context,
                        chargers = dbSaved,
                        size = size
                    )
                }
            }
        }
    }

    @Composable
    private fun EmptyWidgetContent(context: Context) {
        val componentName = ComponentName(context, MainActivity::class.java)

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.bg_widget_rounded))
                .clickable(actionStartActivity(componentName))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚡ 등록된 위젯 충전기가 없습니다",
                    style = TextStyle(
                        color = ColorProvider(day = Color.White, night = Color.White),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
                Text(
                    text = "앱에서 충전기 [위젯 추가 ⭐]를 눌러 등록해보세요 (터치하여 열기)",
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFF00C896), night = Color(0xFF00C896)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
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
                .background(ImageProvider(R.drawable.bg_widget_rounded))
                .clickable(actionStartActivity(componentName))
        ) {
            when {
                // 4x1 (Small) - height < 120dp
                size.height < 120.dp -> Widget4x1(chargers.take(2))
                // 4x2 (Medium) - 120 ~ 210dp
                size.height < 210.dp -> Widget4x2(chargers.take(4))
                // 4x3 (Large) - 210dp+
                else -> Widget4x3(chargers.take(6))
            }
        }
    }

    // ==========================================
    // 🎨 4x1 WIDGET (Compact Bar 2 Wide Items)
    // ==========================================
    @Composable
    private fun Widget4x1(chargers: List<SavedChargerEntity>) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brand Logo Badge
            Row(
                modifier = GlanceModifier
                    .background(ColorProvider(day = Color(0xFF00C896).copy(alpha = 0.2f), night = Color(0xFF00C896).copy(alpha = 0.2f)))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚡ EV",
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFF00C896), night = Color(0xFF00C896)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            chargers.forEachIndexed { index, charger ->
                if (index > 0) {
                    Spacer(modifier = GlanceModifier.width(8.dp))
                }

                val statusEnum = ChargerStatus.fromString(charger.status)
                val statusColor = getStatusColor(statusEnum)
                val shortName = formatCleanName(charger.stationName, charger.customName)
                val terminalText = if (charger.chgerId.length == 6) "${charger.chgerId.substring(0, 5)} ${charger.chgerId.substring(5)}" else "#${charger.chgerId}"

                Row(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .background(ImageProvider(R.drawable.bg_widget_card_rounded))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusDot(status = statusEnum)
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = shortName,
                            style = TextStyle(
                                color = ColorProvider(day = Color.White, night = Color.White),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = terminalText,
                            style = TextStyle(
                                color = ColorProvider(day = Color(0xFF94A3B8), night = Color(0xFF94A3B8)),
                                fontSize = 9.sp
                            ),
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(4.dp))

                    Row(
                        modifier = GlanceModifier
                            .background(ImageProvider(R.drawable.bg_widget_pill_rounded))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
    }

    // ==========================================
    // 🎨 4x2 WIDGET (Samsung One UI 2x2 Grid)
    // ==========================================
    @Composable
    private fun Widget4x2(chargers: List<SavedChargerEntity>) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // --- Top Header ---
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚡ 실시간 EV 충전소",
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFF00C896), night = Color(0xFF00C896)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())

                val availableCount = chargers.count { ChargerStatus.fromString(it.status) == ChargerStatus.AVAILABLE }
                Text(
                    text = "대기 $availableCount/${chargers.size}",
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFF94A3B8), night = Color(0xFF94A3B8)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )

                Spacer(modifier = GlanceModifier.width(8.dp))

                Text(
                    text = "🔄 새로고침",
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFF3B82F6), night = Color(0xFF3B82F6)),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.clickable(actionRunCallback<RefreshActionCallback>())
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // --- 2x2 Grid (2 Columns x 2 Rows) ---
            val chunked = chargers.chunked(2)
            chunked.forEachIndexed { rowIndex, rowList ->
                if (rowIndex > 0) {
                    Spacer(modifier = GlanceModifier.height(6.dp))
                }

                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowList.forEachIndexed { colIndex, charger ->
                        if (colIndex > 0) {
                            Spacer(modifier = GlanceModifier.width(8.dp))
                        }

                        WidgetCardItem(charger = charger, modifier = GlanceModifier.defaultWeight())
                    }

                    // Balance layout if odd count
                    if (rowList.size == 1) {
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Spacer(modifier = GlanceModifier.defaultWeight())
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // --- Footer ---
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val lastFetched = chargers.firstOrNull()?.lastFetchedAt ?: ""
                Text(
                    text = "${formatTimeOnly(lastFetched)} 동기화",
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFF64748B), night = Color(0xFF64748B)),
                        fontSize = 9.sp
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = "앱 열기 →",
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFF00C896), night = Color(0xFF00C896)),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }

    // ==========================================
    // 🎨 4x3 WIDGET (Samsung One UI 2x3 Grid)
    // ==========================================
    @Composable
    private fun Widget4x3(chargers: List<SavedChargerEntity>) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // --- Header ---
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚡ 실시간 EV 충전 모니터링",
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFF00C896), night = Color(0xFF00C896)),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())

                val availableCount = chargers.count { ChargerStatus.fromString(it.status) == ChargerStatus.AVAILABLE }
                Text(
                    text = "이용 가능 $availableCount/${chargers.size}",
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFF94A3B8), night = Color(0xFF94A3B8)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )

                Spacer(modifier = GlanceModifier.width(8.dp))

                Text(
                    text = "🔄 새로고침",
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFF3B82F6), night = Color(0xFF3B82F6)),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.clickable(actionRunCallback<RefreshActionCallback>())
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // --- 2x3 Grid (2 Columns x 3 Rows) ---
            val chunked = chargers.chunked(2)
            chunked.forEachIndexed { rowIndex, rowList ->
                if (rowIndex > 0) {
                    Spacer(modifier = GlanceModifier.height(6.dp))
                }

                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowList.forEachIndexed { colIndex, charger ->
                        if (colIndex > 0) {
                            Spacer(modifier = GlanceModifier.width(8.dp))
                        }

                        WidgetCardItem(charger = charger, modifier = GlanceModifier.defaultWeight())
                    }

                    if (rowList.size == 1) {
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Spacer(modifier = GlanceModifier.defaultWeight())
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // --- Footer ---
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val lastFetched = chargers.firstOrNull()?.lastFetchedAt ?: ""
                Text(
                    text = "${formatTimeOnly(lastFetched)} 동기화 (15분 자동 갱신)",
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFF64748B), night = Color(0xFF64748B)),
                        fontSize = 9.sp
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = "터치하여 앱 열기 →",
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFF00C896), night = Color(0xFF00C896)),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }

    // ==========================================
    // 🎨 Standard Widget Card Item Component
    // ==========================================
    @Composable
    private fun WidgetCardItem(charger: SavedChargerEntity, modifier: GlanceModifier) {
        val statusEnum = ChargerStatus.fromString(charger.status)
        val statusColor = getStatusColor(statusEnum)
        val displayName = formatCleanName(charger.stationName, charger.customName)
        val terminalCode = if (charger.chgerId.length == 6) "${charger.chgerId.substring(0, 5)} ${charger.chgerId.substring(5)}" else "#${charger.chgerId}"
        val spec = "$terminalCode · ${charger.outputKw ?: "7"}kW"

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.bg_widget_card_rounded))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Row 1: [Dot] [Station Name] ── [Status Pill]
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(status = statusEnum)
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = displayName,
                    style = TextStyle(
                        color = ColorProvider(day = Color.White, night = Color.White),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight()
                )

                Spacer(modifier = GlanceModifier.width(4.dp))

                Row(
                    modifier = GlanceModifier
                        .background(ImageProvider(R.drawable.bg_widget_pill_rounded))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

            Spacer(modifier = GlanceModifier.height(3.dp))

            // Row 2: [Terminal & kW Spec]
            Text(
                text = spec,
                style = TextStyle(
                    color = ColorProvider(day = Color(0xFF94A3B8), night = Color(0xFF94A3B8)),
                    fontSize = 9.sp
                ),
                maxLines = 1
            )
        }
    }

    // ==========================================
    // 🎨 UI Helpers & Components
    // ==========================================

    @Composable
    private fun StatusDot(status: ChargerStatus) {
        val iconRes = when (status) {
            ChargerStatus.AVAILABLE -> R.drawable.ic_dot_green
            ChargerStatus.CHARGING -> R.drawable.ic_dot_blue
            ChargerStatus.COMM_ERROR, ChargerStatus.MAINTENANCE, ChargerStatus.SUSPENDED -> R.drawable.ic_dot_orange
            else -> R.drawable.ic_dot_gray
        }
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            modifier = GlanceModifier.size(8.dp)
        )
    }

    private fun getStatusColor(status: ChargerStatus): Color {
        return when (status) {
            ChargerStatus.AVAILABLE -> Color(0xFF00C896)   // Teal Green
            ChargerStatus.CHARGING -> Color(0xFF3B82F6)    // Vivid Blue
            ChargerStatus.COMM_ERROR -> Color(0xFFF97316)  // Orange
            ChargerStatus.MAINTENANCE -> Color(0xFFEAB308) // Yellow
            ChargerStatus.SUSPENDED -> Color(0xFFEF4444)   // Red
            ChargerStatus.RESERVED -> Color(0xFFA855F7)    // Purple
            ChargerStatus.UNCONFIRMED -> Color(0xFF64748B) // Slate
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

    private fun formatCleanName(rawName: String, customName: String?): String {
        if (!customName.isNullOrBlank()) return customName
        return rawName.trim()
    }

    private fun formatTimeOnly(isoString: String): String {
        return try {
            if (isoString.contains("T")) {
                isoString.split("T")[1].substring(0, 5)
            } else if (isoString.isBlank()) {
                "최근"
            } else {
                isoString
            }
        } catch (e: Exception) {
            "최근"
        }
    }
}

/**
 * Glance ActionCallback for 1-Tap Manual Refresh on Home Widget
 */
class RefreshActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val db = AppDatabase.getInstance(context)
        val apiService = BffApiService.create()
        val repository = ChargerRepository(apiService, db.savedChargerDao(), context)
        repository.refreshSavedChargersStatus()
        ChargerWidget().updateAll(context)
    }
}
