package com.evsearch.app.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evsearch.app.data.model.ChargerStatus
import com.evsearch.app.presentation.theme.Apple

/**
 * Apple 계열 공통 컴포넌트 (dark-tile 표현).
 *
 * 규칙: 그림자 없음 · 인터랙션 색은 Accent 하나 · 액션은 pill, 카드는 18dp, 유틸은 8dp ·
 * 누름 상태는 scale(0.95) 하나로 통일.
 */

// ── Global nav ───────────────────────────────────────────────────────────────

/**
 * 얇은 순수 검정 글로벌 내비. 좌측에 카테고리명(태그라인), 우측에 유틸 액션.
 * 하단 헤어라인 하나로 캔버스와 구분한다.
 */
@Composable
fun AppleGlobalNav(
    category: String,
    detail: String? = null,
    modifier: Modifier = Modifier,
    /** 카테고리명 대신 놓을 컨트롤 (넓은 화면의 pane 전환 등). */
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth().background(Apple.C.Black)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 높이를 고정해 여러 pane을 나란히 놓아도 헤더 기준선이 어긋나지 않게 한다.
                .height(NAV_HEIGHT)
                .padding(horizontal = Apple.Sp.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                // 컨트롤이 들어오면 한 줄로 배치한다(두 줄이면 고정 높이 안에서 잘린다).
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    leading()
                    if (detail != null) {
                        Spacer(Modifier.width(Apple.Sp.xs))
                        Text(
                            text = detail,
                            style = Apple.T.FinePrint,
                            color = Apple.C.TextFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category,
                        style = Apple.T.Tagline,
                        color = Apple.C.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (detail != null) {
                        Text(
                            text = detail,
                            style = Apple.T.FinePrint,
                            color = Apple.C.TextFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.width(Apple.Sp.xs))
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        }
        AppleHairline()
    }
}

/** 글로벌 내비 고정 높이. pane 간 헤더 정렬의 기준. */
val NAV_HEIGHT = 60.dp

@Composable
fun AppleHairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Apple.C.Hairline)
    )
}

// ── Buttons ──────────────────────────────────────────────────────────────────

enum class PillStyle { Primary, Ghost }

/** 시그니처 액션: 풀 pill. 누르면 scale(0.95). */
@Composable
fun ApplePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Primary,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    compact: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val container = when {
        !enabled -> Apple.C.Tile2
        style == PillStyle.Primary -> Apple.C.Accent
        else -> Color.Transparent
    }
    val content = when {
        !enabled -> Apple.C.TextDisabled
        style == PillStyle.Primary -> Color.White
        else -> Apple.C.Accent
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = Apple.S.Pill,
        color = container,
        contentColor = content,
        border = if (style == PillStyle.Ghost)
            BorderStroke(1.dp, if (enabled) Apple.C.Accent else Apple.C.Hairline) else null,
        modifier = modifier.scale(if (pressed) 0.95f else 1f)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 14.dp else 22.dp,
                vertical = if (compact) 7.dp else 11.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(if (compact) 14.dp else 17.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = if (compact) Apple.T.Caption else Apple.T.Body,
                color = content,
                maxLines = 1
            )
        }
    }
}

/** 유틸리티 액션(8dp 사각). 내비/카드 안의 보조 조작. */
@Composable
fun AppleUtilityButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = Apple.C.Text,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = Apple.S.Sm,
        color = Apple.C.Tile2,
        modifier = modifier.scale(if (pressed) 0.95f else 1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = if (enabled) tint else Apple.C.TextDisabled, modifier = Modifier.size(15.dp))
                if (text.isNotEmpty()) Spacer(Modifier.width(6.dp))
            }
            if (text.isNotEmpty()) {
                Text(text, style = Apple.T.Caption, color = if (enabled) tint else Apple.C.TextDisabled, maxLines = 1)
            }
        }
    }
}

/** 사진/지도 위에 뜨는 44dp 원형 컨트롤. */
@Composable
fun AppleCircularIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = CircleShape,
        color = Apple.C.ChipTranslucent,
        modifier = modifier.size(44.dp).scale(if (pressed) 0.95f else 1f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = Apple.C.Canvas, modifier = Modifier.size(20.dp))
        }
    }
}

// ── Cards & sections ─────────────────────────────────────────────────────────

/** 18dp 라운드 타일 카드. 그림자 없이 표면색 + 헤어라인으로만 층을 만든다. */
@Composable
fun AppleTile(
    modifier: Modifier = Modifier,
    tone: Color = Apple.C.Tile1,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = Apple.S.Lg
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = tone,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = modifier.fillMaxWidth().border(1.dp, Apple.C.Hairline, shape)
        ) { content() }
    } else {
        Surface(
            shape = shape,
            color = tone,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = modifier.fillMaxWidth().border(1.dp, Apple.C.Hairline, shape)
        ) { content() }
    }
}

/** 섹션 헤드 + 우측 액션. */
@Composable
fun AppleSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Apple.Sp.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = Apple.T.DisplayMd, color = Apple.C.Text)
            if (caption != null) {
                Text(caption, style = Apple.T.FinePrint, color = Apple.C.TextFaint)
            }
        }
        trailing()
    }
}

/** 전면 히어로 문구 (빈 상태 등). */
@Composable
fun AppleEmptyState(
    headline: String,
    body: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Apple.Sp.lg, vertical = Apple.Sp.section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(headline, style = Apple.T.HeroDisplay, color = Apple.C.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Apple.Sp.sm))
        Text(body, style = Apple.T.LeadAiry, color = Apple.C.TextMuted, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(Apple.Sp.lg))
            action()
        }
    }
}

// ── Status ───────────────────────────────────────────────────────────────────

fun statusColor(status: ChargerStatus): Color = when (status) {
    ChargerStatus.AVAILABLE -> Apple.C.StatusAvailable
    ChargerStatus.CHARGING -> Apple.C.StatusCharging
    ChargerStatus.COMM_ERROR -> Apple.C.StatusCommError
    ChargerStatus.MAINTENANCE -> Apple.C.StatusMaintenance
    ChargerStatus.SUSPENDED -> Apple.C.StatusSuspended
    ChargerStatus.RESERVED -> Apple.C.StatusReserved
    ChargerStatus.UNCONFIRMED, ChargerStatus.UNKNOWN -> Apple.C.StatusUnknown
}

fun statusLabel(status: ChargerStatus): String = when (status) {
    ChargerStatus.AVAILABLE -> "충전 가능"
    ChargerStatus.CHARGING -> "충전 중"
    ChargerStatus.COMM_ERROR -> "통신 장애"
    ChargerStatus.MAINTENANCE -> "점검 중"
    ChargerStatus.SUSPENDED -> "운영 중지"
    ChargerStatus.RESERVED -> "예약 중"
    ChargerStatus.UNCONFIRMED, ChargerStatus.UNKNOWN -> "상태 확인 중"
}

/** 상태 도트 + 라벨. 의미색은 여기서만 쓴다. */
@Composable
fun AppleStatusLabel(
    status: ChargerStatus,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val color = statusColor(status)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(if (compact) 6.dp else 8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            text = statusLabel(status),
            style = if (compact) Apple.T.FinePrint else Apple.T.CaptionStrong,
            color = color,
            maxLines = 1
        )
    }
}

// ── Controls ─────────────────────────────────────────────────────────────────

/** pill 선택 칩 그룹 (주기 선택 등). 선택 시 2px 액센트 보더. */
@Composable
fun AppleChipRow(
    options: List<Pair<String, Int>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Apple.Sp.xs)
    ) {
        options.forEach { (label, value) ->
            val isSelected = value == selected
            Surface(
                onClick = { onSelect(value) },
                shape = Apple.S.Pill,
                color = if (isSelected) Apple.C.Accent.copy(alpha = 0.16f) else Apple.C.Tile2,
                border = BorderStroke(
                    if (isSelected) 2.dp else 1.dp,
                    if (isSelected) Apple.C.AccentFocus else Apple.C.Hairline
                ),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = Apple.T.Caption,
                        color = if (isSelected) Apple.C.Accent else Apple.C.TextMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** 스위치 한 줄 (제목 + 설명 + 스위치). */
@Composable
fun AppleSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = Apple.T.BodyStrong, color = Apple.C.Text)
            if (subtitle != null) {
                Text(subtitle, style = Apple.T.FinePrint, color = Apple.C.TextFaint)
            }
        }
        Spacer(Modifier.width(Apple.Sp.sm))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Apple.C.Accent,
                checkedBorderColor = Apple.C.Accent,
                uncheckedThumbColor = Apple.C.TextFaint,
                uncheckedTrackColor = Apple.C.Tile2,
                uncheckedBorderColor = Apple.C.Hairline
            )
        )
    }
}

/** 값 표시용 pill (시간 등). 탭하면 편집. */
@Composable
fun AppleValuePill(
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = Apple.S.Pill,
        color = Apple.C.Tile2,
        border = BorderStroke(1.dp, Apple.C.Hairline),
        modifier = modifier.scale(if (pressed) 0.95f else 1f)
    ) {
        Text(
            text = value,
            style = Apple.T.CaptionStrong,
            color = Apple.C.Accent,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}
