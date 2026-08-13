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
        val dbSaved = db.savedChargerDao().getAllSavedChargers()
        val chargers = getDefaultChargersIfEmpty(dbSaved)

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                ChargerWidgetContent(
                    context = context,
                    chargers = chargers,
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
                    ColorProvider(day = Color(0xFF0F172A), night = Color(0xFF0F172A))
                )
                .clickable(actionStartActivity(componentName))
        ) {
            when {
                // 4x1 (Small) - height < 120dp
                size.height < 120.dp -> Widget4x1(chargers.take(3))
                // 4x2 (Medium) - 120 ~ 210dp
                size.height < 210.dp -> Widget4x2(chargers.take(4))
                // 4x3 (Large) - 210dp+
                else -> Widget4x3(chargers.take(6))
            }
        }
    }

    // ==========================================
    // 🎨 4x1 WIDGET (Compact Horizontal 3 Items)
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

            Spacer(modifier = GlanceModifier.width(10.dp))

            chargers.forEachIndexed { index, charger ->
                if (index > 0) {
                    Spacer(modifier = GlanceModifier.width(8.dp))
                }

                val statusEnum = ChargerStatus.fromString(charger.status)
                val statusColor = getStatusColor(statusEnum)
                val shortName = charger.customName
                    ?: charger.stationName.replace("서울특별시", "서울").replace("강원특별자치도", "강원").take(5)

                Row(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .background(ColorProvider(day = Color(0xFF1E293B), night = Color(0xFF1E293B)))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusDot(color = statusColor, sizeDp = 8)
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Column {
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
    // 🎨 4x2 WIDGET (Medium Grid 3~4 Items)
    // ==========================================
    @Composable
    private fun Widget4x2(chargers: List<SavedChargerEntity>) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // --- Top Header ---
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚡ 실시간 전기차 충전소",
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
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // --- 4-Card Horizontal Row ---
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                chargers.forEachIndexed { index, charger ->
                    if (index > 0) {
                        Spacer(modifier = GlanceModifier.width(8.dp))
                    }

                    val statusEnum = ChargerStatus.fromString(charger.status)
                    val statusColor = getStatusColor(statusEnum)
                    val displayName = charger.customName
                        ?: charger.stationName.replace("서울특별시", "서울").replace("특별자치도", "").take(6)
                    val spec = "#${charger.chgerId} · ${charger.outputKw ?: "7"}kW"

                    Column(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxSize()
                            .background(ColorProvider(day = Color(0xFF1E293B), night = Color(0xFF1E293B)))
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = displayName,
                            style = TextStyle(
                                color = ColorProvider(day = Color.White, night = Color.White),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )

                        Spacer(modifier = GlanceModifier.height(2.dp))

                        Text(
                            text = spec,
                            style = TextStyle(
                                color = ColorProvider(day = Color(0xFF94A3B8), night = Color(0xFF94A3B8)),
                                fontSize = 9.sp
                            ),
                            maxLines = 1
                        )

                        Spacer(modifier = GlanceModifier.height(6.dp))

                        // Status Badge Pill
                        Row(
                            modifier = GlanceModifier
                                .background(ColorProvider(day = statusColor.copy(alpha = 0.2f), night = statusColor.copy(alpha = 0.2f)))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusDot(color = statusColor, sizeDp = 6)
                            Spacer(modifier = GlanceModifier.width(4.dp))
                            Text(
                                text = getStatusText(statusEnum),
                                style = TextStyle(
                                    color = ColorProvider(day = statusColor, night = statusColor),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // --- Footer ---
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔄 실시간 동기화",
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
    // 🎨 4x3 WIDGET (Large 2x3 Grid 5~6 Items)
    // ==========================================
    @Composable
    private fun Widget4x3(chargers: List<SavedChargerEntity>) {
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
                    text = "⚡ 실시간 EV 충전소 모니터링",
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
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            // --- 2x3 Grid Rows ---
            val rows: List<List<SavedChargerEntity>> = chargers.chunked(3)
            rows.forEachIndexed { rowIndex, rowItems ->
                if (rowIndex > 0) {
                    Spacer(modifier = GlanceModifier.height(8.dp))
                }

                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowItems.forEachIndexed { index, charger ->
                        if (index > 0) {
                            Spacer(modifier = GlanceModifier.width(8.dp))
                        }

                        val statusEnum = ChargerStatus.fromString(charger.status)
                        val statusColor = getStatusColor(statusEnum)
                        val displayName = charger.customName
                            ?: charger.stationName.replace("서울특별시", "서울").replace("특별자치도", "").take(7)
                        val spec = "#${charger.chgerId} · ${charger.chargerTypeName.take(4)} ${charger.outputKw ?: "7"}kW"

                        Column(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .fillMaxSize()
                                .background(ColorProvider(day = Color(0xFF1E293B), night = Color(0xFF1E293B)))
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = displayName,
                                style = TextStyle(
                                    color = ColorProvider(day = Color.White, night = Color.White),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )

                            Spacer(modifier = GlanceModifier.height(2.dp))

                            Text(
                                text = spec,
                                style = TextStyle(
                                    color = ColorProvider(day = Color(0xFF94A3B8), night = Color(0xFF94A3B8)),
                                    fontSize = 9.sp
                                ),
                                maxLines = 1
                            )

                            Spacer(modifier = GlanceModifier.height(6.dp))

                            // Status Badge Pill
                            Row(
                                modifier = GlanceModifier
                                    .background(ColorProvider(day = statusColor.copy(alpha = 0.2f), night = statusColor.copy(alpha = 0.2f)))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusDot(color = statusColor, sizeDp = 6)
                                Spacer(modifier = GlanceModifier.width(4.dp))
                                Text(
                                    text = getStatusText(statusEnum),
                                    style = TextStyle(
                                        color = ColorProvider(day = statusColor, night = statusColor),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    // Fill remaining slots in row to keep layout balanced
                    val remaining = 3 - rowItems.size
                    if (remaining > 0) {
                        repeat(remaining) {
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            Spacer(modifier = GlanceModifier.defaultWeight())
                        }
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // --- Footer ---
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val lastFetched = chargers.firstOrNull()?.lastFetchedAt ?: ""
                Text(
                    text = "🔄 자동 실시간 동기화 ${formatTimeOnly(lastFetched)}",
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

    // ==========================================
    // 🎨 UI Helpers & Components
    // ==========================================

    @Composable
    private fun StatusDot(color: Color, sizeDp: Int) {
        Box(
            modifier = GlanceModifier
                .size(sizeDp.dp)
                .background(ColorProvider(day = color, night = color))
        ) {}
    }

    private fun getDefaultChargersIfEmpty(chargers: List<SavedChargerEntity>): List<SavedChargerEntity> {
        if (chargers.isNotEmpty()) return chargers

        // Real-time default nationwide chargers (3 ~ 6 representative chargers)
        return listOf(
            SavedChargerEntity(
                key = "ME20A199:01",
                statId = "ME20A199",
                chgerId = "01",
                stationName = "서울 노원문화원",
                chargerTypeName = "DC콤보",
                outputKw = "100",
                status = "AVAILABLE",
                statusCode = 2,
                statusUpdatedAt = "2026-08-13T17:00:00",
                lastFetchedAt = "2026-08-13T17:00:00"
            ),
            SavedChargerEntity(
                key = "CU530005:01",
                statId = "CU530005",
                chgerId = "01",
                stationName = "강원 춘천시청",
                chargerTypeName = "AC완속",
                outputKw = "7",
                status = "AVAILABLE",
                statusCode = 2,
                statusUpdatedAt = "2026-08-13T17:00:00",
                lastFetchedAt = "2026-08-13T17:00:00"
            ),
            SavedChargerEntity(
                key = "ME20A1a0:01",
                statId = "ME20A1a0",
                chgerId = "01",
                stationName = "부산역 공영주차장",
                chargerTypeName = "DC콤보",
                outputKw = "100",
                status = "CHARGING",
                statusCode = 3,
                statusUpdatedAt = "2026-08-13T17:00:00",
                lastFetchedAt = "2026-08-13T17:00:00"
            ),
            SavedChargerEntity(
                key = "ME20A1a1:01",
                statId = "ME20A1a1",
                chgerId = "01",
                stationName = "제주공항 충전소",
                chargerTypeName = "DC콤보",
                outputKw = "200",
                status = "AVAILABLE",
                statusCode = 2,
                statusUpdatedAt = "2026-08-13T17:00:00",
                lastFetchedAt = "2026-08-13T17:00:00"
            ),
            SavedChargerEntity(
                key = "ME20A1a2:01",
                statId = "ME20A1a2",
                chgerId = "01",
                stationName = "대전시청 주차장",
                chargerTypeName = "DC콤보",
                outputKw = "100",
                status = "AVAILABLE",
                statusCode = 2,
                statusUpdatedAt = "2026-08-13T17:00:00",
                lastFetchedAt = "2026-08-13T17:00:00"
            ),
            SavedChargerEntity(
                key = "ME20A1a3:01",
                statId = "ME20A1a3",
                chgerId = "01",
                stationName = "인천T1 공영주차장",
                chargerTypeName = "DC콤보",
                outputKw = "100",
                status = "CHARGING",
                statusCode = 3,
                statusUpdatedAt = "2026-08-13T17:00:00",
                lastFetchedAt = "2026-08-13T17:00:00"
            )
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
}
