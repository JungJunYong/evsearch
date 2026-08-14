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

// =============================================================================
// 1️⃣ 4x1 위젯: 1x6 배열 (한 줄에 고정된 6개 슬롯)
// =============================================================================
class ChargerWidget4x1 : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getInstance(context)
        val dbSaved = db.savedChargerDao().getAllSavedChargers()
        provideContent {
            GlanceTheme {
                if (dbSaved.isEmpty()) {
                    WidgetCommonUi.EmptyContent(context)
                } else {
                    Widget1x6Content(context, dbSaved.take(6))
                }
            }
        }
    }

    @Composable
    private fun Widget1x6Content(context: Context, chargers: List<SavedChargerEntity>) {
        val componentName = ComponentName(context, MainActivity::class.java)
        val headerStationName = WidgetCommonUi.formatCleanName(chargers.firstOrNull()?.stationName ?: "EV 충전소", null)
        val availableCount = chargers.count { ChargerStatus.fromString(it.status) == ChargerStatus.AVAILABLE }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.bg_widget_rounded))
                .clickable(actionStartActivity(componentName))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                // Top Mini Header
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_bolt),
                        contentDescription = null,
                        modifier = GlanceModifier.size(12.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(3.dp))
                    Text(
                        text = headerStationName,
                        style = TextStyle(
                            color = ColorProvider(day = Color(0xFF00E599), night = Color(0xFF00E599)),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = "$availableCount/${chargers.size} 대기",
                        style = TextStyle(
                            color = ColorProvider(day = Color(0xFF00E599), night = Color(0xFF00E599)),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_refresh),
                        contentDescription = "새로고침",
                        modifier = GlanceModifier
                            .size(11.dp)
                            .clickable(actionRunCallback<RefreshActionCallback>())
                    )
                }

                Spacer(modifier = GlanceModifier.height(3.dp))

                // Render three two-slot groups. This keeps all six compact states visible
                // on launchers that clip the last child of a wide Glance Row.
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp)
                        .defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { groupIndex ->
                        if (groupIndex > 0) Spacer(modifier = GlanceModifier.width(2.dp))
                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .fillMaxHeight()
                        ) {
                            Row(modifier = GlanceModifier.fillMaxSize()) {
                                repeat(2) { slotIndex ->
                                    if (slotIndex > 0) Spacer(modifier = GlanceModifier.width(1.dp))
                                    val charger = chargers.getOrNull(groupIndex * 2 + slotIndex)
                                    Box(
                                        modifier = GlanceModifier
                                            .defaultWeight()
                                            .fillMaxHeight()
                                    ) {
                                        if (charger != null) {
                                            MiniCard1x6(
                                                charger = charger,
                                                modifier = GlanceModifier.fillMaxSize()
                                            )
                                        } else {
                                            EmptySlot1x6(
                                                modifier = GlanceModifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun EmptySlot1x6(modifier: GlanceModifier) {
        Box(
            modifier = modifier
                .background(ImageProvider(R.drawable.bg_widget_card_rounded))
                .padding(horizontal = 1.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "·",
                style = TextStyle(
                    color = ColorProvider(day = Color(0xFF64748B), night = Color(0xFF64748B)),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }

    @Composable
    private fun MiniCard1x6(charger: SavedChargerEntity, modifier: GlanceModifier) {
        val statusEnum = ChargerStatus.fromString(charger.status)
        val terminalCode = charger.chgerId.takeLast(2).padStart(2, '0')
        val displayName = charger.customName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.take(8)
            ?: "#$terminalCode"
        val statusLabel = when (statusEnum) {
            ChargerStatus.AVAILABLE -> "대기"
            ChargerStatus.CHARGING -> "충전"
            ChargerStatus.COMM_ERROR -> "장애"
            ChargerStatus.SUSPENDED -> "중지"
            ChargerStatus.MAINTENANCE -> "점검"
            ChargerStatus.RESERVED -> "예약"
            else -> "확인"
        }

        val statusColor = WidgetCommonUi.getPillTextColor(statusEnum)
        Column(
            modifier = modifier
                .background(ImageProvider(WidgetCommonUi.getCardBackground(statusEnum)))
                .padding(horizontal = 2.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayName,
                style = TextStyle(
                    color = ColorProvider(day = Color.White, night = Color.White),
                    fontSize = if (displayName.length > 5) 8.sp else 9.5.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(3.dp))
            Text(
                text = statusLabel,
                style = TextStyle(
                    color = ColorProvider(day = statusColor, night = statusColor),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
    }
}

// =============================================================================
// 2️⃣ 4x2 위젯: 2x3 배열 (2행 x 3열 = 3열씩 2줄 총 6대, 꽉 찬 폰트 & 안전 여백)
// =============================================================================
class ChargerWidget4x2 : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getInstance(context)
        val dbSaved = db.savedChargerDao().getAllSavedChargers()
        provideContent {
            GlanceTheme {
                if (dbSaved.isEmpty()) {
                    WidgetCommonUi.EmptyContent(context)
                } else {
                    Widget2x3Content(context, dbSaved.take(6))
                }
            }
        }
    }

    @Composable
    private fun Widget2x3Content(context: Context, chargers: List<SavedChargerEntity>) {
        val componentName = ComponentName(context, MainActivity::class.java)
        val headerStationName = WidgetCommonUi.formatCleanName(chargers.firstOrNull()?.stationName ?: "EV 충전소", null)
        val availableCount = chargers.count { ChargerStatus.fromString(it.status) == ChargerStatus.AVAILABLE }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.bg_widget_rounded))
                .clickable(actionStartActivity(componentName))
                .padding(horizontal = 11.dp, vertical = 9.dp)
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                // Header (With safe margin)
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_bolt),
                        contentDescription = null,
                        modifier = GlanceModifier.size(15.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = headerStationName,
                        style = TextStyle(
                            color = ColorProvider(day = Color(0xFF00E599), night = Color(0xFF00E599)),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())

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
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Row(
                        modifier = GlanceModifier
                            .clickable(actionRunCallback<RefreshActionCallback>())
                            .padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_refresh),
                            contentDescription = "새로고침",
                            modifier = GlanceModifier.size(12.dp)
                        )
                        Spacer(modifier = GlanceModifier.width(2.dp))
                        Text(
                            text = "새로고침",
                            style = TextStyle(
                                color = ColorProvider(day = Color(0xFF38BDF8), night = Color(0xFF38BDF8)),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(5.dp))

                // 2x3 Grid: 2 Rows x 3 Columns = 6 Cards (Larger Font & Solid Fill)
                val chunked = chargers.chunked(3)
                chunked.forEachIndexed { rowIndex, rowList ->
                    if (rowIndex > 0) Spacer(modifier = GlanceModifier.height(4.dp))
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowList.forEachIndexed { colIndex, charger ->
                            if (colIndex > 0) Spacer(modifier = GlanceModifier.width(4.dp))
                            Card2x3(
                                charger = charger,
                                modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                            )
                        }
                        val emptySlots = 3 - rowList.size
                        for (i in 0 until emptySlots) {
                            Spacer(modifier = GlanceModifier.width(4.dp))
                            Spacer(modifier = GlanceModifier.defaultWeight())
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.height(4.dp))

                // Footer (Protected from rounded corners)
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val lastFetched = chargers.firstOrNull()?.lastFetchedAt ?: ""
                    Text(
                        text = "${WidgetCommonUi.formatTimeOnly(lastFetched)} 동기화 (15분 자동)",
                        style = TextStyle(
                            color = ColorProvider(day = Color(0xFF64748B), night = Color(0xFF64748B)),
                            fontSize = 9.sp
                        )
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = "앱 열기 →",
                        style = TextStyle(
                            color = ColorProvider(day = Color(0xFF00E599), night = Color(0xFF00E599)),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }

    @Composable
    private fun Card2x3(charger: SavedChargerEntity, modifier: GlanceModifier) {
        val statusEnum = ChargerStatus.fromString(charger.status)
        val pillBgRes = WidgetCommonUi.getPillBackground(statusEnum)
        val pillTextColor = WidgetCommonUi.getPillTextColor(statusEnum)
        val hasCustomName = !charger.customName.isNullOrBlank()
        val terminalCode = if (charger.chgerId.length == 6) "${charger.chgerId.substring(0, 5)} ${charger.chgerId.substring(5)}" else "#${charger.chgerId}"
        val mainTitle = if (hasCustomName) charger.customName!! else terminalCode
        val subText = if (hasCustomName) "$terminalCode · ${charger.outputKw ?: "7"}kW" else "${WidgetCommonUi.formatCleanName(charger.stationName, null)} · ${charger.outputKw ?: "7"}kW"

        // 4x2는 카드 높이가 좁아 이름+상태 2줄로 (부가정보는 4x3/상세에서)
        Column(
            modifier = modifier
                .background(ImageProvider(WidgetCommonUi.getCardBackground(statusEnum)))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = mainTitle,
                style = TextStyle(
                    color = ColorProvider(day = Color.White, night = Color.White),
                    fontSize = if (mainTitle.length > 7) 12.sp else 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = WidgetCommonUi.getStatusText(statusEnum),
                style = TextStyle(
                    color = ColorProvider(day = pillTextColor, night = pillTextColor),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
    }
}

// =============================================================================
// 3️⃣ 4x3 위젯: 3x2 배열 (3행 x 2열 = 2열씩 3줄 와이드 대시보드 총 6대)
// =============================================================================
class ChargerWidget4x3 : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getInstance(context)
        val dbSaved = db.savedChargerDao().getAllSavedChargers()
        provideContent {
            GlanceTheme {
                if (dbSaved.isEmpty()) {
                    WidgetCommonUi.EmptyContent(context)
                } else {
                    Widget3x2Content(context, dbSaved.take(6))
                }
            }
        }
    }

    @Composable
    private fun Widget3x2Content(context: Context, chargers: List<SavedChargerEntity>) {
        val componentName = ComponentName(context, MainActivity::class.java)
        val headerStationName = WidgetCommonUi.formatCleanName(chargers.firstOrNull()?.stationName ?: "EV 충전소", null)
        val availableCount = chargers.count { ChargerStatus.fromString(it.status) == ChargerStatus.AVAILABLE }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.bg_widget_rounded))
                .clickable(actionStartActivity(componentName))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_bolt),
                        contentDescription = null,
                        modifier = GlanceModifier.size(17.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = headerStationName,
                        style = TextStyle(
                            color = ColorProvider(day = Color(0xFF00E599), night = Color(0xFF00E599)),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())

                    Row(
                        modifier = GlanceModifier
                            .background(ImageProvider(R.drawable.bg_pill_available))
                            .padding(horizontal = 6.dp, vertical = 2.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$availableCount/${chargers.size}대 대기",
                            style = TextStyle(
                                color = ColorProvider(day = Color(0xFF00E599), night = Color(0xFF00E599)),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Row(
                        modifier = GlanceModifier
                            .clickable(actionRunCallback<RefreshActionCallback>())
                            .padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_refresh),
                            contentDescription = "새로고침",
                            modifier = GlanceModifier.size(13.dp)
                        )
                        Spacer(modifier = GlanceModifier.width(2.dp))
                        Text(
                            text = "새로고침",
                            style = TextStyle(
                                color = ColorProvider(day = Color(0xFF38BDF8), night = Color(0xFF38BDF8)),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(6.dp))

                // 3x2 Grid (3 rows of 2 columns)
                val chunked = chargers.chunked(2)
                chunked.forEachIndexed { rowIndex, rowList ->
                    if (rowIndex > 0) Spacer(modifier = GlanceModifier.height(5.dp))
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowList.forEachIndexed { colIndex, charger ->
                            if (colIndex > 0) Spacer(modifier = GlanceModifier.width(5.dp))
                            WideCard3x2(
                                charger = charger,
                                modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                            )
                        }
                        if (rowList.size == 1) {
                            Spacer(modifier = GlanceModifier.width(5.dp))
                            Spacer(modifier = GlanceModifier.defaultWeight())
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.height(5.dp))

                // Footer
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val lastFetched = chargers.firstOrNull()?.lastFetchedAt ?: ""
                    Text(
                        text = "${WidgetCommonUi.formatTimeOnly(lastFetched)} 동기화 (15분 자동)",
                        style = TextStyle(
                            color = ColorProvider(day = Color(0xFF64748B), night = Color(0xFF64748B)),
                            fontSize = 9.sp
                        )
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = "앱 열기 →",
                        style = TextStyle(
                            color = ColorProvider(day = Color(0xFF00E599), night = Color(0xFF00E599)),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }

    @Composable
    private fun WideCard3x2(charger: SavedChargerEntity, modifier: GlanceModifier) {
        val statusEnum = ChargerStatus.fromString(charger.status)
        val pillBgRes = WidgetCommonUi.getPillBackground(statusEnum)
        val pillTextColor = WidgetCommonUi.getPillTextColor(statusEnum)
        val hasCustomName = !charger.customName.isNullOrBlank()
        val terminalCode = if (charger.chgerId.length == 6) "${charger.chgerId.substring(0, 5)} ${charger.chgerId.substring(5)}" else "#${charger.chgerId}"
        val mainTitle = if (hasCustomName) charger.customName!! else terminalCode
        val subText = if (hasCustomName) "$terminalCode · ${charger.outputKw ?: "7"}kW" else "${WidgetCommonUi.formatCleanName(charger.stationName, null)} · ${charger.outputKw ?: "7"}kW"

        Row(
            modifier = modifier
                .background(ImageProvider(WidgetCommonUi.getCardBackground(statusEnum)))
                .padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = mainTitle,
                    style = TextStyle(
                        color = ColorProvider(day = Color.White, night = Color.White),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = subText,
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFF94A3B8), night = Color(0xFF94A3B8)),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            // 상태 라벨 크게 (색상 강조)
            Text(
                text = WidgetCommonUi.getStatusText(statusEnum),
                style = TextStyle(
                    color = ColorProvider(day = pillTextColor, night = pillTextColor),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
    }
}

// =============================================================================
// 4️⃣ 기본 위젯 호환용 alias
// =============================================================================
class ChargerWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        ChargerWidget4x2().provideGlance(context, id)
    }
}

// =============================================================================
// 🎨 공통 위젯 UI 유틸리티
// =============================================================================
object WidgetCommonUi {
    @Composable
    fun EmptyContent(context: Context) {
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
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_bolt),
                    contentDescription = null,
                    modifier = GlanceModifier.size(24.dp)
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "등록된 위젯 충전기가 없습니다",
                    style = TextStyle(
                        color = ColorProvider(day = Color.White, night = Color.White),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = "앱에서 충전소 [⭐ 6대 일괄 위젯 등록]을 눌러보세요",
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFF00E599), night = Color(0xFF00E599)),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }

    @Composable
    fun StatusDot(status: ChargerStatus, size: androidx.compose.ui.unit.Dp = 6.dp) {
        val iconRes = when (status) {
            ChargerStatus.AVAILABLE -> R.drawable.ic_dot_green
            ChargerStatus.CHARGING -> R.drawable.ic_dot_blue
            ChargerStatus.COMM_ERROR, ChargerStatus.MAINTENANCE, ChargerStatus.SUSPENDED -> R.drawable.ic_dot_orange
            else -> R.drawable.ic_dot_gray
        }
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            modifier = GlanceModifier.size(size)
        )
    }

    fun getPillBackground(status: ChargerStatus): Int {
        return when (status) {
            ChargerStatus.AVAILABLE -> R.drawable.bg_pill_available
            ChargerStatus.CHARGING -> R.drawable.bg_pill_charging
            ChargerStatus.COMM_ERROR, ChargerStatus.MAINTENANCE, ChargerStatus.SUSPENDED -> R.drawable.bg_pill_error
            else -> R.drawable.bg_pill_default
        }
    }

    /** 상태색 카드 배경 (rounded 틴트 + stroke). */
    fun getCardBackground(status: ChargerStatus): Int {
        return when (status) {
            ChargerStatus.AVAILABLE -> R.drawable.bg_card_available
            ChargerStatus.CHARGING -> R.drawable.bg_card_charging
            ChargerStatus.COMM_ERROR, ChargerStatus.MAINTENANCE, ChargerStatus.SUSPENDED -> R.drawable.bg_card_error
            else -> R.drawable.bg_card_default
        }
    }

    fun getPillTextColor(status: ChargerStatus): Color {
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

    fun getStatusText(status: ChargerStatus): String {
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

    fun formatCleanName(rawName: String, customName: String?): String {
        if (!customName.isNullOrBlank()) return customName
        return rawName
            .replace(Regex("^(서울특별시|경기도|강원특별자치도|충청북도|충청남도|전라북도|전라남도|경상북도|경상남도|제주특별자치도|인천광역시|대전광역시|대구광역시|광주광역시|울산광역시|부산광역시|세종특별자치시|서울|경기|인천|대전|대구|광주|울산|부산|세종|강원|충북|충남|전북|전남|경북|경남|제주)\\s*"), "")
            .replace(Regex("^(남양주시|고양시|성남시|용인시|수원시|안양시|부천시|의정부시|화성시|평택시|파주시|김포시|광명시|군포시|이천시|양주시|오산시|구리시|안성시|포천시|의왕시|하남시|여주시|양평군|동두천시|과천시|가평군|연천군|노원구|강남구|서초구|송파구|강동구|마포구|영등포구|용산구|종로구|중구|성동구|광진구|동대문구|중랑구|성북구|강북구|도봉구|은평구|서대문구|양천구|강서구|구로구|금천구|동작구|관악구)\\s*"), "")
            .trim()
    }

    fun formatTimeOnly(isoString: String): String {
        return try {
            if (isoString.isBlank()) return "최근"
            val timePart = if (isoString.contains("T")) {
                isoString.split("T")[1].substring(0, 5)
            } else if (isoString.contains(" ")) {
                isoString.split(" ")[1].substring(0, 5)
            } else if (isoString.length >= 5 && isoString.contains(":")) {
                isoString.substring(0, 5)
            } else {
                return "최근"
            }

            val parts = timePart.split(":")
            val hour = parts[0].toIntOrNull() ?: return timePart
            val minute = parts[1].toIntOrNull() ?: return timePart

            val amPm = if (hour < 12) "오전" else "오후"
            val hour12 = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            val minuteFormatted = String.format("%02d", minute)
            "$amPm $hour12:$minuteFormatted"
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
