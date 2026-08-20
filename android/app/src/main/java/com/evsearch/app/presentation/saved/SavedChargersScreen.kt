package com.evsearch.app.presentation.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evsearch.app.data.local.SavedChargerEntity
import com.evsearch.app.data.model.ChargerStatus
import com.evsearch.app.presentation.common.AppleEmptyState
import com.evsearch.app.presentation.common.AppleGlobalNav
import com.evsearch.app.presentation.common.AppleHairline
import com.evsearch.app.presentation.common.AppleStatusLabel
import com.evsearch.app.presentation.common.AppleTile
import com.evsearch.app.presentation.common.ApplePillButton
import com.evsearch.app.presentation.common.PillStyle
import com.evsearch.app.presentation.common.AppleUtilityButton
import com.evsearch.app.presentation.common.StateDuration
import com.evsearch.app.presentation.favorites.relativeTime
import com.evsearch.app.presentation.theme.Apple

/**
 * 위젯 탭 — 홈 화면 위젯에 올릴 단말기 목록과 자동 갱신 주기를 관리한다.
 * 즐겨찾기(알림) 목록과는 독립적이다.
 */
@Composable
fun SavedChargersScreen(
    viewModel: SavedChargersViewModel,
    onStationClick: (String) -> Unit,
    /** 넓은 화면에서 카테고리명 자리에 놓는 pane 전환 컨트롤 (없으면 카테고리명 표시). */
    navLeading: (@Composable () -> Unit)? = null
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
                leading = navLeading,
                category = "위젯",
                detail = "${uiState.savedChargers.size}개 단말기 · 15분 주기 + 변화 시 즉시",
                actions = {
                    if (uiState.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(20.dp).height(20.dp),
                            strokeWidth = 2.dp,
                            color = Apple.C.Accent
                        )
                    } else {
                        AppleUtilityButton(
                            text = "",
                            icon = Icons.Default.Refresh,
                            tint = Apple.C.Accent,
                            enabled = uiState.savedChargers.isNotEmpty(),
                            onClick = { viewModel.refreshStatus() }
                        )
                    }
                }
            )
        }
    ) { padding ->
        // 하단 제스처 바/내비게이션 바에 마지막 줄이 가리지 않도록 인셋만큼 더 띄운다.
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
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
                    contentPadding = PaddingValues(
                        start = Apple.Sp.md, end = Apple.Sp.md,
                        top = Apple.Sp.lg, bottom = Apple.Sp.xl + bottomInset
                    ),
                    verticalArrangement = Arrangement.spacedBy(Apple.Sp.sm)
                ) {
                    item {
                        WidgetSyncTile(
                            lastSyncAt = uiState.lastSyncAt,
                            lastPushAt = uiState.lastPushAt,
                            onRefresh = { viewModel.refreshStatus() },
                            isRefreshing = uiState.isRefreshing
                        )
                        Spacer(Modifier.height(Apple.Sp.xs))
                    }

                    if (uiState.savedChargers.isEmpty()) {
                        item {
                            AppleEmptyState(
                                headline = "위젯이 비어 있습니다",
                                body = "지도에서 충전소를 열고 단말기의 ‘위젯 추가’를 누르면\n홈 화면 위젯에 최대 6대까지 표시됩니다."
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = "위젯 표시 순서",
                                style = Apple.T.CaptionStrong,
                                color = Apple.C.TextFaint,
                                modifier = Modifier.padding(top = Apple.Sp.xs, bottom = Apple.Sp.xxs)
                            )
                        }
                        items(uiState.savedChargers, key = { it.key }) { charger ->
                            WidgetChargerTile(
                                charger = charger,
                                onClick = { onStationClick(charger.statId) },
                                onEdit = {
                                    editingTarget = charger
                                    editingName = charger.customName ?: ""
                                },
                                onDelete = { viewModel.removeCharger(charger.key) }
                            )
                        }
                        if (uiState.savedChargers.size > 6) {
                            item {
                                Spacer(Modifier.height(Apple.Sp.xxs))
                                Text(
                                    "홈 위젯에는 앞의 6대까지 표시됩니다.",
                                    style = Apple.T.MicroLegal,
                                    color = Apple.C.TextFaint
                                )
                            }
                        }
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
                            placeholder = { Text("예: 지하 2층 3번", style = Apple.T.Caption, color = Apple.C.TextDisabled) },
                            singleLine = true,
                            shape = Apple.S.Md,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Apple.C.AccentFocus,
                                unfocusedBorderColor = Apple.C.Hairline,
                                focusedTextColor = Apple.C.Text,
                                unfocusedTextColor = Apple.C.Text
                            ),
                            supportingText = {
                                Text(
                                    "비워두면 충전소 이름이 표시됩니다.",
                                    style = Apple.T.MicroLegal,
                                    color = Apple.C.TextFaint
                                )
                            }
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

/** 갱신 방식 안내 타일 — 주기는 15분 고정, 즉시 갱신은 서버 푸시와 새로고침. */
@Composable
private fun WidgetSyncTile(
    lastSyncAt: Long,
    lastPushAt: Long,
    onRefresh: () -> Unit,
    isRefreshing: Boolean
) {
    AppleTile(tone = Apple.C.Tile1) {
        Column(modifier = Modifier.padding(Apple.Sp.md)) {
            Text("갱신 방식", style = Apple.T.BodyStrong, color = Apple.C.Text)
            Spacer(Modifier.height(Apple.Sp.xxs))
            Text(
                "빈자리 알림이 켜져 있으면 서버가 상태 변화를 감지한 순간 위젯을 즉시 갱신합니다. " +
                    "그 밖에는 15분 주기와 새로고침으로만 갱신합니다.",
                style = Apple.T.FinePrint,
                color = Apple.C.TextFaint
            )
            Spacer(Modifier.height(Apple.Sp.sm))
            ApplePillButton(
                text = if (isRefreshing) "새로고침 중" else "지금 새로고침",
                compact = true,
                enabled = !isRefreshing,
                style = PillStyle.Ghost,
                onClick = onRefresh
            )
            Spacer(Modifier.height(Apple.Sp.sm))
            AppleHairline()
            Spacer(Modifier.height(Apple.Sp.sm))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("마지막 동기화", style = Apple.T.MicroLegal, color = Apple.C.TextFaint)
                    Text(
                        if (lastSyncAt > 0) relativeTime(lastSyncAt) else "대기 중",
                        style = Apple.T.CaptionStrong,
                        color = Apple.C.TextMuted
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("마지막 서버 푸시", style = Apple.T.MicroLegal, color = Apple.C.TextFaint)
                    Text(
                        if (lastPushAt > 0) relativeTime(lastPushAt) else "대기 중",
                        style = Apple.T.CaptionStrong,
                        color = Apple.C.TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetChargerTile(
    charger: SavedChargerEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val status = ChargerStatus.fromString(charger.status)
    val title = charger.customName ?: charger.stationName

    AppleTile(tone = Apple.C.Tile1, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Apple.Sp.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    text = "단말기 ${charger.chgerId} · ${specText(charger)}",
                    style = Apple.T.FinePrint,
                    color = Apple.C.TextFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Apple.Sp.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppleStatusLabel(status = status, compact = true)
                    StateDuration.label(status, charger.stateSinceAt, charger.statusUpdatedAt)?.let {
                        Text(
                            text = " · $it",
                            style = Apple.T.MicroLegal,
                            color = Apple.C.TextFaint,
                            maxLines = 1
                        )
                    }
                }
            }
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

private fun specText(c: SavedChargerEntity): String {
    val type = c.chargerTypeName.replace(Regex(" \\(.*\\)"), "")
    return if (!type.contains("kW") && c.outputKw != null) "$type ${c.outputKw}kW" else type
}
