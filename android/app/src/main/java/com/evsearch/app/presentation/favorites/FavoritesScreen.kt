package com.evsearch.app.presentation.favorites

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.evsearch.app.alert.AlertPrefs
import com.evsearch.app.data.local.SavedChargerEntity
import com.evsearch.app.data.model.ChargerStatus
import com.evsearch.app.presentation.common.AppleGlobalNav
import com.evsearch.app.presentation.common.AppleHairline
import com.evsearch.app.presentation.common.ApplePillButton
import com.evsearch.app.presentation.common.AppleChipRow
import com.evsearch.app.presentation.common.AppleEmptyState
import com.evsearch.app.presentation.common.AppleStatusLabel
import com.evsearch.app.presentation.common.AppleSwitchRow
import com.evsearch.app.presentation.common.AppleTile
import com.evsearch.app.presentation.common.AppleUtilityButton
import com.evsearch.app.presentation.common.AppleValuePill
import com.evsearch.app.presentation.common.StateDuration
import com.evsearch.app.presentation.common.PillStyle
import com.evsearch.app.presentation.theme.Apple

/**
 * 즐겨찾기 탭 — 알림 대상 목록과 알림 설정(시간 범위·확인 주기)을 담당한다.
 * 위젯 목록과는 완전히 분리된 목록이다.
 */
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onStationClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingTarget by remember { mutableStateOf<SavedChargerEntity?>(null) }
    var editingName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.reloadSettings() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = Apple.C.Canvas,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppleGlobalNav(
                category = "즐겨찾기",
                detail = buildString {
                    append("${uiState.favorites.size}개 단말기")
                    append(" · 알림 ")
                    append(if (uiState.settings.enabled) "켜짐" else "꺼짐")
                },
                actions = {
                    AppleUtilityButton(
                        text = "",
                        icon = Icons.Default.Refresh,
                        tint = Apple.C.Accent,
                        enabled = !uiState.isRefreshing && uiState.favorites.isNotEmpty(),
                        onClick = { viewModel.refreshStatus() }
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
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Apple.C.Accent)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = Apple.Sp.md, end = Apple.Sp.md,
                        top = Apple.Sp.lg, bottom = Apple.Sp.xl
                    ),
                    verticalArrangement = Arrangement.spacedBy(Apple.Sp.sm)
                ) {
                    item {
                        AlertSettingsTile(
                            state = uiState,
                            onMasterChange = viewModel::setAlertEnabled,
                            onWindowChange = viewModel::setWindow,
                            onAllDayChange = viewModel::setAllDay,
                            onIntervalChange = viewModel::setIntervalSec
                        )
                        Spacer(Modifier.height(Apple.Sp.xs))
                    }

                    if (uiState.favorites.isEmpty()) {
                        item {
                            AppleEmptyState(
                                headline = "즐겨찾기가 비어 있습니다",
                                body = "지도에서 충전소를 열고 단말기의 ‘즐겨찾기’를 누르면\n빈자리가 생기는 순간 알려드립니다."
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = "알림 대상",
                                style = Apple.T.CaptionStrong,
                                color = Apple.C.TextFaint,
                                modifier = Modifier.padding(top = Apple.Sp.xs, bottom = Apple.Sp.xxs)
                            )
                        }
                        items(uiState.favorites, key = { it.key }) { item ->
                            FavoriteTile(
                                charger = item,
                                onClick = { onStationClick(item.statId) },
                                onAlertToggle = { viewModel.setItemAlertEnabled(item.key, it) },
                                onEdit = {
                                    editingTarget = item
                                    editingName = item.customName ?: ""
                                },
                                onDelete = { viewModel.removeFavorite(item.key) }
                            )
                        }
                    }

                    item {
                        Spacer(Modifier.height(Apple.Sp.md))
                        FooterNote(uiState)
                    }
                }
            }
        }

        editingTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { editingTarget = null },
                shape = Apple.S.Lg,
                containerColor = Apple.C.Tile1,
                title = { Text("별칭", style = Apple.T.DisplayMd, color = Apple.C.Text) },
                text = {
                    Column {
                        Text(
                            "${target.stationName} · 단말기 ${target.chgerId}",
                            style = Apple.T.FinePrint,
                            color = Apple.C.TextFaint
                        )
                        Spacer(Modifier.height(Apple.Sp.sm))
                        OutlinedTextField(
                            value = editingName,
                            onValueChange = { editingName = it.take(20) },
                            placeholder = { Text("예: 우리집 앞 급속", style = Apple.T.Caption, color = Apple.C.TextDisabled) },
                            singleLine = true,
                            shape = Apple.S.Md,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Apple.C.AccentFocus,
                                unfocusedBorderColor = Apple.C.Hairline,
                                focusedTextColor = Apple.C.Text,
                                unfocusedTextColor = Apple.C.Text
                            )
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateCustomName(target.key, editingName)
                        editingTarget = null
                    }) { Text("저장", style = Apple.T.BodyStrong, color = Apple.C.Accent) }
                },
                dismissButton = {
                    TextButton(onClick = { editingTarget = null }) {
                        Text("취소", style = Apple.T.Body, color = Apple.C.TextMuted)
                    }
                }
            )
        }
    }
}

/** 알림 설정 타일: 마스터 스위치 + 감시 시간 범위 + 확인 주기. */
@Composable
private fun AlertSettingsTile(
    state: FavoritesUiState,
    onMasterChange: (Boolean) -> Unit,
    onWindowChange: (Int, Int) -> Unit,
    onAllDayChange: (Boolean) -> Unit,
    onIntervalChange: (Int) -> Unit
) {
    val ctx = LocalContext.current
    val s = state.settings

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) onMasterChange(true) }

    fun requestEnable(enable: Boolean) {
        if (!enable) {
            onMasterChange(false)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onMasterChange(true)
        }
    }

    fun pick(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(ctx, { _, h, m -> onPicked(h * 60 + m) }, initial / 60, initial % 60, true).show()
    }

    AppleTile(tone = Apple.C.Tile1) {
        Column(modifier = Modifier.padding(Apple.Sp.md)) {
            AppleSwitchRow(
                title = "빈자리 알림",
                subtitle = "즐겨찾기 단말기가 비는 순간 푸시로 알려드립니다",
                checked = s.enabled,
                onCheckedChange = { requestEnable(it) }
            )

            if (s.enabled) {
                Spacer(Modifier.height(Apple.Sp.md))
                AppleHairline()
                Spacer(Modifier.height(Apple.Sp.md))

                // 감시 시간 범위
                AppleSwitchRow(
                    title = "종일 감시",
                    subtitle = if (s.isAllDay) "하루 24시간 감시합니다" else "지정한 시간 범위에만 감시합니다",
                    checked = s.isAllDay,
                    onCheckedChange = { onAllDayChange(it) }
                )

                if (!s.isAllDay) {
                    Spacer(Modifier.height(Apple.Sp.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("시간 범위", style = Apple.T.Caption, color = Apple.C.TextMuted, modifier = Modifier.weight(1f))
                        AppleValuePill(
                            value = fmtTime(s.startMin),
                            onClick = { pick(s.startMin) { onWindowChange(it, s.endMin) } }
                        )
                        Text("  –  ", style = Apple.T.Caption, color = Apple.C.TextFaint)
                        AppleValuePill(
                            value = fmtTime(s.endMin),
                            onClick = { pick(s.endMin) { onWindowChange(s.startMin, it) } }
                        )
                    }
                    if (s.startMin > s.endMin) {
                        Spacer(Modifier.height(Apple.Sp.xxs))
                        Text(
                            "자정을 넘겨 ${fmtTime(s.startMin)}부터 다음 날 ${fmtTime(s.endMin)}까지 감시합니다.",
                            style = Apple.T.MicroLegal,
                            color = Apple.C.TextFaint
                        )
                    }
                }

                Spacer(Modifier.height(Apple.Sp.md))
                AppleHairline()
                Spacer(Modifier.height(Apple.Sp.md))

                // 확인 주기
                Text("확인 주기", style = Apple.T.BodyStrong, color = Apple.C.Text)
                Spacer(Modifier.height(Apple.Sp.xxs))
                Text(
                    "짧게 두면 더 실시간에 가깝게 받고, 길게 두면 데이터·배터리를 아낍니다.",
                    style = Apple.T.FinePrint,
                    color = Apple.C.TextFaint
                )
                Spacer(Modifier.height(Apple.Sp.sm))
                AppleChipRow(
                    options = AlertPrefs.INTERVAL_OPTIONS.map { fmtDuration(it) to it },
                    selected = s.intervalSec,
                    onSelect = onIntervalChange
                )

                Spacer(Modifier.height(Apple.Sp.sm))
                Text(
                    "서버가 이 주기로 상태를 확인하고, ‘충전 중 → 충전 가능’으로 바뀌는 순간에만 알립니다.",
                    style = Apple.T.MicroLegal,
                    color = Apple.C.TextFaint
                )
            }
        }
    }
}

@Composable
private fun FavoriteTile(
    charger: SavedChargerEntity,
    onClick: () -> Unit,
    onAlertToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val status = ChargerStatus.fromString(charger.status)
    val title = charger.customName ?: charger.stationName

    AppleTile(tone = Apple.C.Tile1, onClick = onClick) {
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
                    Text(
                        text = "단말기 ${charger.chgerId} · ${specText(charger)}",
                        style = Apple.T.FinePrint,
                        color = Apple.C.TextFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(Apple.Sp.xs))
                Column(horizontalAlignment = Alignment.End) {
                    AppleStatusLabel(status = status)
                    StateDuration.label(status, charger.stateSinceAt, charger.statusUpdatedAt)?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(it, style = Apple.T.MicroLegal, color = Apple.C.TextFaint, maxLines = 1)
                    }
                }
            }

            Spacer(Modifier.height(Apple.Sp.sm))
            AppleHairline()
            Spacer(Modifier.height(Apple.Sp.sm))

            Row(verticalAlignment = Alignment.CenterVertically) {
                AppleSwitchRow(
                    title = "이 단말기 알림",
                    subtitle = null,
                    checked = charger.alertEnabled,
                    onCheckedChange = onAlertToggle,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(Apple.Sp.xs))
                AppleUtilityButton(text = "", icon = Icons.Default.Edit, tint = Apple.C.Accent, onClick = onEdit)
                Spacer(Modifier.width(Apple.Sp.xxs))
                AppleUtilityButton(
                    text = "",
                    icon = Icons.Default.Delete,
                    tint = Apple.C.StatusSuspended,
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
private fun FooterNote(state: FavoritesUiState) {
    val lastPush = state.lastPushAt
    Column {
        AppleHairline()
        Spacer(Modifier.height(Apple.Sp.sm))
        Text(
            text = if (lastPush > 0)
                "마지막 서버 알림 수신 ${relativeTime(lastPush)}"
            else
                "서버 알림은 앱이 닫혀 있어도 도착합니다.",
            style = Apple.T.MicroLegal,
            color = Apple.C.TextFaint
        )
        Spacer(Modifier.height(Apple.Sp.xxs))
        Text(
            text = "‘충전 가능’은 자리를 예약하지 않습니다. 도착 시점의 현장 상태와 다를 수 있습니다.",
            style = Apple.T.MicroLegal,
            color = Apple.C.TextFaint
        )
    }
}

private fun specText(c: SavedChargerEntity): String {
    val type = c.chargerTypeName.replace(Regex(" \\(.*\\)"), "")
    return if (!type.contains("kW") && c.outputKw != null) "$type ${c.outputKw}kW" else type
}

internal fun fmtTime(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

internal fun fmtDuration(sec: Int): String = when {
    sec < 60 -> "${sec}초"
    sec % 3600 == 0 -> "${sec / 3600}시간"
    sec % 60 == 0 -> "${sec / 60}분"
    else -> "${sec / 60}분 ${sec % 60}초"
}

internal fun relativeTime(epochMs: Long): String {
    val diff = (System.currentTimeMillis() - epochMs) / 1000
    return when {
        diff < 60 -> "방금"
        diff < 3600 -> "${diff / 60}분 전"
        diff < 86_400 -> "${diff / 3600}시간 전"
        else -> "${diff / 86_400}일 전"
    }
}
