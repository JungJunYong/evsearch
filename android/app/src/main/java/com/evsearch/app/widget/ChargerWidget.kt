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
import com.evsearch.app.alert.AlertPrefs
import com.evsearch.app.data.api.BffApiService
import com.evsearch.app.data.local.AppDatabase
import com.evsearch.app.data.local.SavedChargerEntity
import com.evsearch.app.data.model.ChargerStatus
import com.evsearch.app.data.repository.ChargerRepository
import com.evsearch.app.presentation.common.StateDuration

/**
 * 홈 화면 위젯 (Apple dark-tile 표현).
 *
 * - 표시 대상은 '위젯 목록'(isWidget)뿐이다. 즐겨찾기와는 별개 목록.
 * - 캔버스 #1d1d1f, 내부 타일 #2a2a2c, 강조는 '충전 가능' 하나(system green 틴트).
 * - 인터랙션 색은 액센트(#2997ff) 하나. 그림자 없음, 표면색과 헤어라인으로만 층을 만든다.
 */

private object W {
    val Text = Color(0xFFFFFFFF)
    val Muted = Color(0xFFCCCCCC)
    val Faint = Color(0xFF98989D)
    val Accent = Color(0xFF2997FF)

    fun statusColor(s: ChargerStatus): Color = when (s) {
        ChargerStatus.AVAILABLE -> Color(0xFF30D158)
        ChargerStatus.CHARGING -> Color(0xFF64B5FF)
        ChargerStatus.COMM_ERROR -> Color(0xFFFF9F0A)
        ChargerStatus.MAINTENANCE -> Color(0xFFFFD60A)
        ChargerStatus.SUSPENDED -> Color(0xFFFF453A)
        ChargerStatus.RESERVED -> Color(0xFFBF5AF0)
        ChargerStatus.UNCONFIRMED, ChargerStatus.UNKNOWN -> Color(0xFF98989D)
    }

    fun cardBg(s: ChargerStatus): Int =
        if (s == ChargerStatus.AVAILABLE) R.drawable.bg_card_available else R.drawable.bg_card_default

    fun label(s: ChargerStatus): String = when (s) {
        ChargerStatus.AVAILABLE -> "가능"
        ChargerStatus.CHARGING -> "충전 중"
        ChargerStatus.COMM_ERROR -> "장애"
        ChargerStatus.MAINTENANCE -> "점검"
        ChargerStatus.SUSPENDED -> "중지"
        ChargerStatus.RESERVED -> "예약"
        ChargerStatus.UNCONFIRMED, ChargerStatus.UNKNOWN -> "확인 중"
    }

    fun provider(c: Color) = ColorProvider(day = c, night = c)
}

// =============================================================================
// 4x1 — 한 줄 6슬롯
// =============================================================================
class ChargerWidget4x1 : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val chargers = AppDatabase.getInstance(context).savedChargerDao().getWidgetChargers()
        provideContent {
            GlanceTheme {
                if (chargers.isEmpty()) WidgetCommonUi.EmptyContent(context)
                else Content(context, chargers.take(6))
            }
        }
    }

    @Composable
    private fun Content(context: Context, chargers: List<SavedChargerEntity>) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.bg_widget_rounded))
                .clickable(actionStartActivity(ComponentName(context, MainActivity::class.java)))
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                WidgetCommonUi.Header(chargers, compact = true)
                Spacer(modifier = GlanceModifier.height(5.dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { group ->
                        if (group > 0) Spacer(modifier = GlanceModifier.width(3.dp))
                        Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                            Row(modifier = GlanceModifier.fillMaxSize()) {
                                repeat(2) { slot ->
                                    if (slot > 0) Spacer(modifier = GlanceModifier.width(3.dp))
                                    val charger = chargers.getOrNull(group * 2 + slot)
                                    Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                                        if (charger != null) MiniSlot(charger) else EmptySlot()
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
    private fun MiniSlot(charger: SavedChargerEntity) {
        val status = ChargerStatus.fromString(charger.status)
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(W.cardBg(status)))
                .padding(horizontal = 3.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = WidgetCommonUi.shortTitle(charger),
                style = TextStyle(color = W.provider(W.Text), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = W.label(status),
                style = TextStyle(
                    color = W.provider(W.statusColor(status)),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
    }

    @Composable
    private fun EmptySlot() {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.bg_widget_card_rounded)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "–",
                style = TextStyle(color = W.provider(W.Faint), fontSize = 10.sp)
            )
        }
    }
}

// =============================================================================
// 4x2 — 2행 x 3열
// =============================================================================
class ChargerWidget4x2 : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val chargers = AppDatabase.getInstance(context).savedChargerDao().getWidgetChargers()
        val intervalSec = AlertPrefs.getWidgetIntervalSec(context)
        provideContent {
            GlanceTheme {
                if (chargers.isEmpty()) WidgetCommonUi.EmptyContent(context)
                else Content(context, chargers.take(6), intervalSec)
            }
        }
    }

    @Composable
    private fun Content(context: Context, chargers: List<SavedChargerEntity>, intervalSec: Int) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.bg_widget_rounded))
                .clickable(actionStartActivity(ComponentName(context, MainActivity::class.java)))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                WidgetCommonUi.Header(chargers, compact = false)
                Spacer(modifier = GlanceModifier.height(7.dp))

                chargers.chunked(3).forEachIndexed { rowIndex, rowList ->
                    if (rowIndex > 0) Spacer(modifier = GlanceModifier.height(5.dp))
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowList.forEachIndexed { colIndex, charger ->
                            if (colIndex > 0) Spacer(modifier = GlanceModifier.width(5.dp))
                            Card(charger, GlanceModifier.defaultWeight().fillMaxHeight())
                        }
                        repeat(3 - rowList.size) {
                            Spacer(modifier = GlanceModifier.width(5.dp))
                            Spacer(modifier = GlanceModifier.defaultWeight())
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.height(6.dp))
                WidgetCommonUi.Footer(chargers, intervalSec)
            }
        }
    }

    @Composable
    private fun Card(charger: SavedChargerEntity, modifier: GlanceModifier) {
        val status = ChargerStatus.fromString(charger.status)
        Column(
            modifier = modifier
                .background(ImageProvider(W.cardBg(status)))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = WidgetCommonUi.title(charger),
                style = TextStyle(color = W.provider(W.Text), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(3.dp))
            Text(
                text = W.label(status),
                style = TextStyle(
                    color = W.provider(W.statusColor(status)),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
    }
}

// =============================================================================
// 4x3 — 3행 x 2열 (와이드 대시보드)
// =============================================================================
class ChargerWidget4x3 : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val chargers = AppDatabase.getInstance(context).savedChargerDao().getWidgetChargers()
        val intervalSec = AlertPrefs.getWidgetIntervalSec(context)
        provideContent {
            GlanceTheme {
                if (chargers.isEmpty()) WidgetCommonUi.EmptyContent(context)
                else Content(context, chargers.take(6), intervalSec)
            }
        }
    }

    @Composable
    private fun Content(context: Context, chargers: List<SavedChargerEntity>, intervalSec: Int) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.bg_widget_rounded))
                .clickable(actionStartActivity(ComponentName(context, MainActivity::class.java)))
                .padding(horizontal = 13.dp, vertical = 11.dp)
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                WidgetCommonUi.Header(chargers, compact = false)
                Spacer(modifier = GlanceModifier.height(8.dp))

                chargers.chunked(2).forEachIndexed { rowIndex, rowList ->
                    if (rowIndex > 0) Spacer(modifier = GlanceModifier.height(6.dp))
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowList.forEachIndexed { colIndex, charger ->
                            if (colIndex > 0) Spacer(modifier = GlanceModifier.width(6.dp))
                            WideCard(charger, GlanceModifier.defaultWeight().fillMaxHeight())
                        }
                        if (rowList.size == 1) {
                            Spacer(modifier = GlanceModifier.width(6.dp))
                            Spacer(modifier = GlanceModifier.defaultWeight())
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.height(7.dp))
                WidgetCommonUi.Footer(chargers, intervalSec)
            }
        }
    }

    @Composable
    private fun WideCard(charger: SavedChargerEntity, modifier: GlanceModifier) {
        val status = ChargerStatus.fromString(charger.status)
        Row(
            modifier = modifier
                .background(ImageProvider(W.cardBg(status)))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = WidgetCommonUi.title(charger),
                    style = TextStyle(color = W.provider(W.Text), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = WidgetCommonUi.subtitle(charger),
                    style = TextStyle(color = W.provider(W.Faint), fontSize = 10.sp),
                    maxLines = 1
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = W.label(status),
                style = TextStyle(
                    color = W.provider(W.statusColor(status)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
    }
}

// =============================================================================
// 기본 위젯 호환용 alias
// =============================================================================
class ChargerWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        ChargerWidget4x2().provideGlance(context, id)
    }
}

// =============================================================================
// 공통 UI
// =============================================================================
object WidgetCommonUi {

    /** 조용한 헤더: 요약 pill + 마지막 동기화 시각 + 새로고침. */
    @Composable
    fun Header(chargers: List<SavedChargerEntity>, compact: Boolean) {
        val available = chargers.count { ChargerStatus.fromString(it.status) == ChargerStatus.AVAILABLE }
        val lastFetched = chargers.firstOrNull()?.lastFetchedAt ?: ""
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "충전 가능 $available",
                style = TextStyle(
                    color = W.provider(if (available > 0) W.statusColor(ChargerStatus.AVAILABLE) else W.Faint),
                    fontSize = if (compact) 11.sp else 14.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = "/ ${chargers.size}대",
                style = TextStyle(
                    color = W.provider(W.Faint),
                    fontSize = if (compact) 10.sp else 12.sp
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = formatTimeOnly(lastFetched),
                style = TextStyle(color = W.provider(W.Faint), fontSize = if (compact) 9.sp else 10.sp),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Image(
                provider = ImageProvider(R.drawable.ic_widget_refresh),
                contentDescription = "새로고침",
                modifier = GlanceModifier
                    .size(if (compact) 12.dp else 15.dp)
                    .clickable(actionRunCallback<RefreshActionCallback>())
            )
        }
    }

    /** 미세 활자 푸터: 갱신 주기와 앱 진입 안내. */
    @Composable
    fun Footer(chargers: List<SavedChargerEntity>, intervalSec: Int) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${formatTimeOnly(chargers.firstOrNull()?.lastFetchedAt ?: "")} 동기화 · ${formatInterval(intervalSec)} 자동",
                style = TextStyle(color = W.provider(W.Faint), fontSize = 10.sp),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "앱 열기",
                style = TextStyle(color = W.provider(W.Accent), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
    }

    @Composable
    fun EmptyContent(context: Context) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.bg_widget_rounded))
                .clickable(actionStartActivity(ComponentName(context, MainActivity::class.java)))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "위젯이 비어 있습니다",
                    style = TextStyle(color = W.provider(W.Text), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "앱에서 단말기의 ‘위젯 추가’를 눌러 주세요",
                    style = TextStyle(color = W.provider(W.Muted), fontSize = 11.sp),
                    maxLines = 2
                )
                Spacer(modifier = GlanceModifier.height(8.dp))
                Text(
                    text = "앱 열기",
                    style = TextStyle(color = W.provider(W.Accent), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                )
            }
        }
    }

    /** 카드 제목: 별칭이 있으면 별칭, 없으면 단말기 번호. */
    fun title(c: SavedChargerEntity): String {
        val custom = c.customName?.trim()?.takeIf { it.isNotEmpty() }
        if (custom != null) return custom
        return if (c.chgerId.length == 6) "${c.chgerId.substring(0, 5)} ${c.chgerId.substring(5)}" else "#${c.chgerId}"
    }

    fun shortTitle(c: SavedChargerEntity): String {
        val custom = c.customName?.trim()?.takeIf { it.isNotEmpty() }
        return custom?.take(6) ?: "#${c.chgerId.takeLast(2).padStart(2, '0')}"
    }

    fun subtitle(c: SavedChargerEntity): String {
        val hasCustom = !c.customName.isNullOrBlank()
        val code = if (c.chgerId.length == 6) "${c.chgerId.substring(0, 5)} ${c.chgerId.substring(5)}" else "#${c.chgerId}"
        val head = if (hasCustom) code else formatCleanName(c.stationName, null)
        // 상태가 얼마나 지속됐는지(충전 시작 후 경과)를 함께 보여준다.
        val since = StateDuration.shortLabel(c.stateSinceAt, c.statusUpdatedAt)
        return if (since != null) "$head · $since" else "$head · ${c.outputKw ?: "7"}kW"
    }

    fun formatInterval(sec: Int): String = when {
        sec < 60 -> "${sec}초"
        sec % 3600 == 0 -> "${sec / 3600}시간"
        else -> "${sec / 60}분"
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
            if (isoString.isBlank()) return "—"
            val timePart = when {
                isoString.contains("T") -> isoString.split("T")[1].substring(0, 5)
                isoString.contains(" ") -> isoString.split(" ")[1].substring(0, 5)
                isoString.length >= 5 && isoString.contains(":") -> isoString.substring(0, 5)
                else -> return "—"
            }
            timePart
        } catch (e: Exception) {
            "—"
        }
    }
}

/**
 * 위젯 새로고침 탭 → 캐시를 건너뛰고 곧바로 최신 상태를 읽어온다.
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
        repository.refreshTrackedChargersStatus(maxAgeMs = 0L)
        AlertPrefs.setLastSyncAt(context, System.currentTimeMillis())
        WidgetUpdateHelper.updateAllWidgets(context)
    }
}
