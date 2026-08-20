package com.evsearch.app.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evsearch.app.data.model.Charger
import com.evsearch.app.data.model.ChargerStation
import com.evsearch.app.data.model.ChargerStatus
import com.evsearch.app.presentation.common.AppleGlobalNav
import com.evsearch.app.presentation.common.AppleHairline
import com.evsearch.app.presentation.common.ApplePillButton
import com.evsearch.app.presentation.common.AppleSectionHeader
import com.evsearch.app.presentation.common.AppleStatusLabel
import com.evsearch.app.presentation.common.AppleTile
import com.evsearch.app.presentation.common.AppleUtilityButton
import com.evsearch.app.presentation.common.StateDuration
import com.evsearch.app.presentation.common.PillStyle
import com.evsearch.app.presentation.theme.Apple

/**
 * 충전소 상세 — Apple dark-tile 표현.
 * 단말기마다 두 개의 독립된 액션을 준다: 즐겨찾기(알림 대상) / 위젯(홈 화면 표시).
 */
@Composable
fun StationDetailScreen(
    viewModel: StationDetailViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.widgetSavedSuccessMessage) {
        uiState.widgetSavedSuccessMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSuccessMessage()
        }
    }

    Scaffold(
        containerColor = Apple.C.Canvas,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppleGlobalNav(
                category = uiState.station?.name ?: "충전소",
                detail = uiState.station?.address,
                actions = {
                    AppleUtilityButton(
                        text = "",
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        tint = Apple.C.Accent,
                        onClick = onBackClick
                    )
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Apple.C.Canvas)
        ) {
            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Apple.C.Accent)
                    }
                }

                uiState.errorMessage != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = uiState.errorMessage ?: "오류가 발생했습니다.",
                            style = Apple.T.Body,
                            color = Apple.C.StatusSuspended
                        )
                    }
                }

                uiState.station != null -> {
                    val station = uiState.station!!
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Apple.Sp.md, end = Apple.Sp.md,
                            top = Apple.Sp.lg, bottom = Apple.Sp.xl
                        ),
                        verticalArrangement = Arrangement.spacedBy(Apple.Sp.sm)
                    ) {
                        item { StationHeroTile(station) }

                        item {
                            AppleSectionHeader(
                                title = "단말기 ${station.chargers.size}대",
                                caption = "충전 가능 ${station.summary.available}대",
                                trailing = {
                                    if (station.chargers.size > 1) {
                                        ApplePillButton(
                                            text = "6대 위젯 추가",
                                            compact = true,
                                            style = PillStyle.Ghost,
                                            onClick = { viewModel.registerFirst6Chargers() }
                                        )
                                    }
                                }
                            )
                        }

                        items(station.chargers) { charger ->
                            val key = "${station.statId}:${charger.chgerId}"
                            ChargerTile(
                                charger = charger,
                                isFavorite = uiState.favoriteKeys.contains(key),
                                isWidget = uiState.widgetKeys.contains(key),
                                onFavoriteClick = { viewModel.toggleFavorite(charger) },
                                onWidgetClick = { viewModel.toggleWidgetRegistration(charger) }
                            )
                        }

                        item {
                            Spacer(Modifier.height(Apple.Sp.sm))
                            Text(
                                text = "상태는 사업자 서버에서 받아온 값입니다. ‘충전 가능’이 자리를 예약하지는 않습니다.",
                                style = Apple.T.MicroLegal,
                                color = Apple.C.TextFaint
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 충전소 히어로 타일: 이름 · 주소 · 요약 · 메타. */
@Composable
private fun StationHeroTile(station: ChargerStation) {
    AppleTile(tone = Apple.C.Tile1) {
        Column(modifier = Modifier.padding(Apple.Sp.md)) {
            Text(
                text = station.name,
                style = Apple.T.DisplayLg,
                color = Apple.C.Text
            )
            Spacer(Modifier.height(Apple.Sp.xxs))
            Text(
                text = station.address,
                style = Apple.T.Caption,
                color = Apple.C.TextMuted
            )

            Spacer(Modifier.height(Apple.Sp.md))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${station.summary.available}",
                    style = Apple.T.HeroDisplay,
                    color = if (station.summary.available > 0) Apple.C.StatusAvailable else Apple.C.TextFaint
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "/ ${station.summary.total}대 충전 가능",
                    style = Apple.T.Caption,
                    color = Apple.C.TextMuted,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Spacer(Modifier.height(Apple.Sp.md))
            AppleHairline()
            Spacer(Modifier.height(Apple.Sp.sm))

            MetaLine("운영", station.operatorName)
            station.useTime?.let { MetaLine("이용시간", it) }
            MetaLine("주차", if (station.parkingFree == true) "무료" else "유료 또는 확인 필요")
            if (station.rapidCnt != null || station.slowCnt != null) {
                MetaLine("구성", "급속 ${station.rapidCnt ?: 0} · 완속 ${station.slowCnt ?: 0}")
            }
            station.observedAt?.let { MetaLine("관측", it.take(19).replace("T", " ")) }
        }
    }
}

@Composable
private fun MetaLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = Apple.T.FinePrint,
            color = Apple.C.TextFaint,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = value,
            style = Apple.T.FinePrint,
            color = Apple.C.TextMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 단말기 타일: 상태 + 즐겨찾기/위젯 두 액션. */
@Composable
private fun ChargerTile(
    charger: Charger,
    isFavorite: Boolean,
    isWidget: Boolean,
    onFavoriteClick: () -> Unit,
    onWidgetClick: () -> Unit
) {
    val status = ChargerStatus.fromString(charger.status)
    val title = if (!charger.chargerCode.isNullOrBlank()) "충전기 ${charger.chargerCode}" else "단말기 ${charger.chgerId}"

    AppleTile(tone = Apple.C.Tile1) {
        Column(modifier = Modifier.padding(Apple.Sp.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = Apple.T.BodyStrong,
                        color = Apple.C.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            append("${charger.outputKw ?: "7"}kW · ${charger.kepcoTypeText}")
                            charger.price?.takeIf { it.isNotBlank() }?.let { append(" · ${it}원/kWh") }
                        },
                        style = Apple.T.FinePrint,
                        color = Apple.C.TextFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    charger.location?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = Apple.T.MicroLegal,
                            color = Apple.C.TextFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(Apple.Sp.xs))
                Column(horizontalAlignment = Alignment.End) {
                    AppleStatusLabel(status = status)
                    StateDuration.label(status, charger.lastChargeStartedAt, charger.statusUpdatedAt)?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(it, style = Apple.T.MicroLegal, color = Apple.C.TextFaint, maxLines = 1)
                    }
                }
            }

            Spacer(Modifier.height(Apple.Sp.sm))
            AppleHairline()
            Spacer(Modifier.height(Apple.Sp.sm))

            Row(horizontalArrangement = Arrangement.spacedBy(Apple.Sp.xs)) {
                ApplePillButton(
                    text = if (isFavorite) "즐겨찾기 해제" else "즐겨찾기",
                    compact = true,
                    leadingIcon = Icons.Default.Star,
                    style = if (isFavorite) PillStyle.Primary else PillStyle.Ghost,
                    onClick = onFavoriteClick
                )
                ApplePillButton(
                    text = if (isWidget) "위젯 해제" else "위젯 추가",
                    compact = true,
                    leadingIcon = Icons.AutoMirrored.Filled.List,
                    style = if (isWidget) PillStyle.Primary else PillStyle.Ghost,
                    onClick = onWidgetClick
                )
            }
        }
    }
}
