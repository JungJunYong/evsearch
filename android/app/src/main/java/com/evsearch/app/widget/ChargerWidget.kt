package com.evsearch.app.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
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

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getInstance(context)
        val dbSaved = db.savedChargerDao().getAllSavedChargers()

        provideContent {
            GlanceTheme {
                if (dbSaved.isEmpty()) {
                    EmptyWidgetContent(context = context)
                } else {
                    ChargerWidgetContent(
                        context = context,
                        chargers = dbSaved.take(6)
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
                .padding(14.dp),
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
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "앱에서 충전소 [⭐ 6대 일괄 위젯 등록]을 눌러보세요",
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFF00E599), night = Color(0xFF00E599)),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }

    // =========================================================================
    // 🎨 1x6 WIDGET (1 Row x 6 Columns Side-by-Side Flagship Dashboard)
    // =========================================================================
    @Composable
    private fun ChargerWidgetContent(
        context: Context,
        chargers: List<SavedChargerEntity>
    ) {
        val componentName = ComponentName(context, MainActivity::class.java)
        val headerStationName = formatCleanName(chargers.firstOrNull()?.stationName ?: "EV 충전소", null)
        val availableCount = chargers.count { ChargerStatus.fromString(it.status) == ChargerStatus.AVAILABLE }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.bg_widget_rounded))
                .clickable(actionStartActivity(componentName))
                .padding(horizontal = 9.dp, vertical = 7.dp)
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                // --- 1. Luxury Header ---
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚡ $headerStationName",
                        style = TextStyle(
                            color = ColorProvider(day = Color(0xFF00E599), night = Color(0xFF00E599)),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())

                    // Available Count Pill
                    Row(
                        modifier = GlanceModifier
                            .background(ImageProvider(R.drawable.bg_pill_available))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$availableCount/${chargers.size}대 대기",
                            style = TextStyle(
                                color = ColorProvider(day = Color(0xFF00E599), night = Color(0xFF00E599)),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(6.dp))

                    // Refresh Button
                    Text(
                        text = "🔄 새로고침",
                        style = TextStyle(
                            color = ColorProvider(day = Color(0xFF38BDF8), night = Color(0xFF38BDF8)),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.clickable(actionRunCallback<RefreshActionCallback>())
                    )
                }

                Spacer(modifier = GlanceModifier.height(5.dp))

                // --- 2. 1x6 Horizontal Row (6 Mini-Cards Side-by-Side) ---
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    chargers.forEachIndexed { index, charger ->
                        if (index > 0) {
                            Spacer(modifier = GlanceModifier.width(3.5.dp))
                        }

                        SingleRowMiniCard(
                            charger = charger,
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }

                    // Fill remaining slots if fewer than 6
                    val emptySlots = 6 - chargers.size
                    for (i in 0 until emptySlots) {
                        Spacer(modifier = GlanceModifier.width(3.5.dp))
                        Spacer(modifier = GlanceModifier.defaultWeight())
                    }
                }

                Spacer(modifier = GlanceModifier.height(4.dp))

                // --- 3. Refined Footer ---
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val lastFetched = chargers.firstOrNull()?.lastFetchedAt ?: ""
                    Text(
                        text = "${formatTimeOnly(lastFetched)} 동기화 (15분 자동)",
                        style = TextStyle(
                            color = ColorProvider(day = Color(0xFF64748B), night = Color(0xFF64748B)),
                            fontSize = 8.5.sp
                        )
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = "앱 열기 →",
                        style = TextStyle(
                            color = ColorProvider(day = Color(0xFF00E599), night = Color(0xFF00E599)),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }

    // =========================================================================
    // 🎨 1x6 전용 수직 미니 카드 (상단 번호, 중간 스펙, 하단 네온 뱃지)
    // =========================================================================
    @Composable
    private fun SingleRowMiniCard(charger: SavedChargerEntity, modifier: GlanceModifier) {
        val statusEnum = ChargerStatus.fromString(charger.status)
        val terminalCode = if (charger.chgerId.length == 6) "${charger.chgerId.substring(0, 5)} ${charger.chgerId.substring(5)}" else "#${charger.chgerId}"
        val pillBgRes = getStatusPillBackground(statusEnum)
        val pillTextColor = getStatusTextColor(statusEnum)

        Column(
            modifier = modifier
                .fillMaxHeight()
                .background(ImageProvider(R.drawable.bg_widget_card_rounded))
                .padding(horizontal = 3.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Row 1: 단말기 번호 (11050 8)
            Text(
                text = terminalCode,
                style = TextStyle(
                    color = ColorProvider(day = Color.White, night = Color.White),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )

            Spacer(modifier = GlanceModifier.height(2.dp))

            // Row 2: 7kW 스펙
            Text(
                text = "${charger.outputKw ?: "7"}kW",
                style = TextStyle(
                    color = ColorProvider(day = Color(0xFF64748B), night = Color(0xFF64748B)),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )

            Spacer(modifier = GlanceModifier.height(4.dp))

            // Row 3: 네온 상태 뱃지 ([🟢 대기] / [🔵 충전])
            Row(
                modifier = GlanceModifier
                    .background(ImageProvider(pillBgRes))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StatusDot(status = statusEnum)
                Spacer(modifier = GlanceModifier.width(2.5.dp))
                Text(
                    text = getStatusText(statusEnum),
                    style = TextStyle(
                        color = ColorProvider(day = pillTextColor, night = pillTextColor),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
        }
    }

    // ==========================================
    // 🎨 UI Helpers & Color Resolvers
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
            modifier = GlanceModifier.size(5.5.dp)
        )
    }

    private fun getStatusPillBackground(status: ChargerStatus): Int {
        return when (status) {
            ChargerStatus.AVAILABLE -> R.drawable.bg_pill_available
            ChargerStatus.CHARGING -> R.drawable.bg_pill_charging
            ChargerStatus.COMM_ERROR, ChargerStatus.MAINTENANCE, ChargerStatus.SUSPENDED -> R.drawable.bg_pill_error
            else -> R.drawable.bg_pill_default
        }
    }

    private fun getStatusTextColor(status: ChargerStatus): Color {
        return when (status) {
            ChargerStatus.AVAILABLE -> Color(0xFF00E599)   // Electric Neon Mint
            ChargerStatus.CHARGING -> Color(0xFF60A5FA)    // Electric Blue
            ChargerStatus.COMM_ERROR -> Color(0xFFFB923C)  // Amber Orange
            ChargerStatus.MAINTENANCE -> Color(0xFFFBBF24) // Gold Yellow
            ChargerStatus.SUSPENDED -> Color(0xFFF87171)   // Bright Red
            ChargerStatus.RESERVED -> Color(0xFFC084FC)    // Violet
            ChargerStatus.UNCONFIRMED, ChargerStatus.UNKNOWN -> Color(0xFF94A3B8)
        }
    }

    private fun getStatusText(status: ChargerStatus): String {
        return when (status) {
            ChargerStatus.AVAILABLE -> "대기"
            ChargerStatus.CHARGING -> "충전"
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
        return rawName
            .replace(Regex("^(서울특별시|경기도|강원특별자치도|충청북도|충청남도|전라북도|전라남도|경상북도|경상남도|제주특별자치도|인천광역시|대전광역시|대구광역시|광주광역시|울산광역시|부산광역시|세종특별자치시|서울|경기|인천|대전|대구|광주|울산|부산|세종|강원|충북|충남|전북|전남|경북|경남|제주)\\s*"), "")
            .replace(Regex("^(남양주시|고양시|성남시|용인시|수원시|안양시|부천시|의정부시|화성시|평택시|파주시|김포시|광명시|군포시|이천시|양주시|오산시|구리시|안성시|포천시|의왕시|하남시|여주시|양평군|동두천시|과천시|가평군|연천군|노원구|강남구|서초구|송파구|강동구|마포구|영등포구|용산구|종로구|중구|성동구|광진구|동대문구|중랑구|성북구|강북구|도봉구|은평구|서대문구|양천구|강서구|구로구|금천구|동작구|관악구)\\s*"), "")
            .trim()
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
        WidgetUpdateHelper.updateAllWidgets(context)
    }
}
