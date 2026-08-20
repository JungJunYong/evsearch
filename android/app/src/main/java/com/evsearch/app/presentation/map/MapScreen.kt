package com.evsearch.app.presentation.map

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evsearch.app.data.model.ChargerStation
import com.evsearch.app.presentation.common.AppleGlobalNav
import com.evsearch.app.presentation.common.AppleHairline
import com.evsearch.app.presentation.common.ApplePillButton
import com.evsearch.app.presentation.common.AppleTile
import com.evsearch.app.presentation.common.PillStyle
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import com.evsearch.app.presentation.theme.Apple
import com.evsearch.app.presentation.map.components.KakaoMapView

/**
 * Modern Clean Full-Screen Map Experience (No clunky region chips, auto-camera sync)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onStationClick: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var userLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var hasAutoFetchedLocation by remember { mutableStateOf(false) }

    // Map Camera Focus Coordinate
    var focusLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    // Selected Station Bottom Card
    var selectedStation by remember { mutableStateOf<ChargerStation?>(null) }
    val detailScope = rememberCoroutineScope()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            com.evsearch.app.utils.LocationHelper.getCurrentLocation(context) { loc ->
                if (loc != null) {
                    val pos = Pair(loc.latitude, loc.longitude)
                    userLocation = pos
                    focusLocation = pos
                }
            }
        }
    }

    // 앱 시작 시 자동으로 내 위치 활성화 (최초 1회)
    LaunchedEffect(Unit) {
        if (!hasAutoFetchedLocation) {
            hasAutoFetchedLocation = true
            if (com.evsearch.app.utils.LocationHelper.hasLocationPermission(context)) {
                com.evsearch.app.utils.LocationHelper.getCurrentLocation(context) { loc ->
                    if (loc != null) {
                        val pos = Pair(loc.latitude, loc.longitude)
                        userLocation = pos
                        focusLocation = pos
                    }
                }
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    val fetchMyLocation = {
        if (com.evsearch.app.utils.LocationHelper.hasLocationPermission(context)) {
            com.evsearch.app.utils.LocationHelper.getCurrentLocation(context) { loc ->
                if (loc != null) {
                    val pos = Pair(loc.latitude, loc.longitude)
                    userLocation = pos
                    focusLocation = pos
                }
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val filteredStations = remember(uiState.stations, searchQuery) {
        if (searchQuery.isBlank()) {
            uiState.stations
        } else {
            uiState.stations.filter { s ->
                s.name.contains(searchQuery, ignoreCase = true) ||
                s.address.contains(searchQuery, ignoreCase = true) ||
                (s.operatorName ?: "").contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // 🎯 검색 결과가 있을 때 첫 번째 해당 위치로 지도 카메라 자동 동기화
    LaunchedEffect(filteredStations, searchQuery) {
        if (searchQuery.isNotBlank() && filteredStations.isNotEmpty()) {
            val topStation = filteredStations.first()
            if (topStation.lat > 0 && topStation.lng > 0) {
                focusLocation = Pair(topStation.lat, topStation.lng)
            }
        }
    }

    // 지도 뷰포트 (카메라 이동 시 갱신): minLat, minLng, maxLat, maxLng
    data class ViewportBounds(val minLat: Double, val minLng: Double, val maxLat: Double, val maxLng: Double)
    var viewport by remember { mutableStateOf<ViewportBounds?>(null) }

    // 두 좌표 간 거리 계산 (Haversine, km 단위)
    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6371.0 // 지구 반경 (km)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    val visibleStations = remember(filteredStations, viewport, userLocation, searchQuery) {
        val vp = viewport
        val baseLat: Double? = userLocation?.first ?: vp?.let { (it.minLat + it.maxLat) / 2 } ?: 37.5665
        val baseLng: Double? = userLocation?.second ?: vp?.let { (it.minLng + it.maxLng) / 2 } ?: 126.9780

        val filtered = if (vp == null || searchQuery.isNotBlank()) {
            filteredStations
        } else {
            filteredStations.filter { s ->
                s.lat > 0 && s.lng > 0 &&
                s.lat >= vp.minLat && s.lat <= vp.maxLat &&
                s.lng >= vp.minLng && s.lng <= vp.maxLng
            }
        }

        // 거리 계산 후 가까운 순 정렬
        if (baseLat != null && baseLng != null) {
            filtered
                .map { s ->
                    val dist = distanceKm(baseLat, baseLng, s.lat, s.lng)
                    s.copy(distanceKm = dist) to dist
                }
                .sortedBy { it.second }
                .map { it.first }
        } else {
            filtered
        }
    }

    Scaffold(
        containerColor = Apple.C.Canvas,
        topBar = {
            AppleGlobalNav(
                category = "충전소",
                detail = if (searchQuery.isNotBlank()) "검색 결과 ${filteredStations.size}개소"
                    else "주변 ${visibleStations.size}개소"
            )
        }
    ) { paddingValues ->
        val bottomInset = androidx.compose.foundation.layout.WindowInsets.navigationBars
            .asPaddingValues().calculateBottomPadding()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Apple.C.Canvas)
        ) {
            val focusManager = LocalFocusManager.current

            // 검색 입력: CTA와 같은 pill 문법
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    if (it.isBlank()) {
                        viewModel.clearSearch()
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        viewModel.performSearch(searchQuery)
                    }
                ),
                placeholder = {
                    Text(
                        text = "충전소·아파트·단말기 번호",
                        style = Apple.T.Body,
                        color = Apple.C.TextDisabled,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    IconButton(onClick = {
                        focusManager.clearFocus()
                        viewModel.performSearch(searchQuery)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "검색 실행",
                            tint = Apple.C.Accent
                        )
                    }
                },
                trailingIcon = {
                    when {
                        uiState.isSearching -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Apple.C.Accent
                            )
                        }
                        searchQuery.isNotBlank() -> {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "검색어 지우기",
                                tint = Apple.C.TextFaint,
                                modifier = Modifier.clickable {
                                    searchQuery = ""
                                    viewModel.clearSearch()
                                    selectedStation = null
                                }
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = Apple.S.Pill,
                singleLine = true,
                textStyle = Apple.T.Body.copy(color = Apple.C.Text),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Apple.C.Tile1,
                    unfocusedContainerColor = Apple.C.Tile1,
                    focusedBorderColor = Apple.C.AccentFocus,
                    unfocusedBorderColor = Apple.C.Hairline,
                    focusedTextColor = Apple.C.Text,
                    unfocusedTextColor = Apple.C.Text,
                    cursorColor = Apple.C.Accent
                )
            )

            // Samsung One UI 9.0 Squircle Interactive Vector Map Container (26dp radius)
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val mapHeight = (configuration.screenHeightDp * 0.42f).dp.coerceAtLeast(320.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(mapHeight)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(Apple.S.Lg)
                    .border(1.dp, Apple.C.Hairline, Apple.S.Lg)
                    .background(Apple.C.Black)
            ) {
                KakaoMapView(
                    stations = filteredStations,
                    centerLat = userLocation?.first ?: 37.5665,
                    centerLng = userLocation?.second ?: 126.9780,
                    userLocation = userLocation,
                    focusLocation = focusLocation,
                    onPinClick = { station ->
                        // 즉시 경량 표시 후, 상세(실제 이름·대수)를 로드해 갱신
                        selectedStation = station
                        detailScope.launch {
                            val detail = viewModel.loadStationDetail(station.statId)
                            if (detail != null && selectedStation?.statId == station.statId) {
                                selectedStation = detail
                            }
                        }
                    },
                    onMapClick = { selectedStation = null },
                    onViewportChanged = { minLat, minLng, maxLat, maxLng ->
                        viewport = ViewportBounds(minLat, minLng, maxLat, maxLng)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 내 위치 + 줌 컨트롤 (우측 상단 플로팅)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 내 위치
                    Surface(
                        onClick = { fetchMyLocation() },
                        shape = CircleShape,
                        color = Apple.C.ChipTranslucent,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "내 위치로 이동",
                                tint = Apple.C.Canvas,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // 줌 인
                    Surface(
                        onClick = { com.evsearch.app.presentation.map.components.MapZoomController.zoomIn() },
                        shape = CircleShape,
                        color = Apple.C.ChipTranslucent,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "+",
                                color = Apple.C.Canvas,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // 줌 아웃
                    Surface(
                        onClick = { com.evsearch.app.presentation.map.components.MapZoomController.zoomOut() },
                        shape = CircleShape,
                        color = Apple.C.ChipTranslucent,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "−",
                                color = Apple.C.Canvas,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (uiState.isLoading && uiState.stations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Apple.C.Black.copy(alpha = 0.72f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Apple.C.Accent)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "충전소를 불러오는 중",
                                style = Apple.T.Caption,
                                color = Apple.C.TextMuted
                            )
                        }
                    }
                }

                // 핀 선택 시 하단 요약 카드 (One UI 9.0 스타일)
                selectedStation?.let { station ->
                    SelectedStationSummaryCard(
                        station = station,
                        onDetailClick = { onStationClick(station.statId) },
                        onClose = { selectedStation = null },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                    )
                }
            }

            // Section Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) "검색 결과 ${visibleStations.size}개소"
                        else "주변 충전소 ${visibleStations.size}개소",
                    style = Apple.T.DisplayMd,
                    color = Apple.C.Text
                )

                Text(
                    text = if (userLocation != null) "현재 위치 기준" else "전국 기준",
                    style = Apple.T.FinePrint,
                    color = Apple.C.TextFaint
                )
            }

            // Station Card List
            when {
                uiState.errorMessage != null -> {
                    EmptyMapState(
                        title = "불러오지 못했습니다",
                        description = uiState.errorMessage ?: "네트워크 상태를 확인한 뒤 다시 시도해 주세요."
                    )
                }
                visibleStations.isEmpty() && !uiState.isSearching -> {
                    EmptyMapState(
                        title = "결과가 없습니다",
                        description = "충전소 이름이나 아파트 이름을 바꿔 다시 검색해 보세요."
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // 마지막 카드가 하단 탭바 경계에서 잘린 채 멈추지 않도록 여백을 준다.
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp, end = 16.dp, bottom = Apple.Sp.xl + bottomInset
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(visibleStations) { station ->
                            StationRowTile(
                                station = station,
                                onClick = {
                                    focusLocation = Pair(station.lat, station.lng)
                                    selectedStation = station
                                    onStationClick(station.statId)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 빈 상태: 헤드라인 + 리드 문단만. 장식 없음. */
@Composable
private fun EmptyMapState(
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Apple.Sp.lg, vertical = Apple.Sp.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = Apple.T.HeroDisplay,
            color = Apple.C.Text,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Apple.Sp.sm))
        Text(
            text = description,
            style = Apple.T.LeadAiry,
            color = Apple.C.TextMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/** 지도 위에 떠 있는 선택 충전소 요약 — 반투명 없이 타일 하나. */
@Composable
private fun SelectedStationSummaryCard(
    station: ChargerStation,
    onDetailClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val available = station.summary.available
    val accentColor = if (available > 0) Apple.C.StatusAvailable else Apple.C.TextFaint

    AppleTile(modifier = modifier, tone = Apple.C.Tile1) {
        Column(modifier = Modifier.padding(Apple.Sp.md)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station.name,
                        style = Apple.T.BodyStrong,
                        color = Apple.C.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = station.address,
                        style = Apple.T.FinePrint,
                        color = Apple.C.TextFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "닫기",
                    tint = Apple.C.TextFaint,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onClose() }
                )
            }

            Spacer(modifier = Modifier.height(Apple.Sp.sm))
            AppleHairline()
            Spacer(modifier = Modifier.height(Apple.Sp.sm))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (station.summary.total > 0) "충전 가능 $available / ${station.summary.total}대"
                        else if (available > 0) "충전 가능" else "이용 불가",
                    style = Apple.T.CaptionStrong,
                    color = accentColor,
                    modifier = Modifier.weight(1f)
                )
                ApplePillButton(
                    text = "상세",
                    compact = true,
                    style = PillStyle.Primary,
                    onClick = onDetailClick
                )
            }
        }
    }
}

/** 목록 행 타일: 이름 · 운영자 · 거리 · 가용 대수. 색은 상태 라벨에만. */
@Composable
fun StationRowTile(
    station: ChargerStation,
    onClick: () -> Unit
) {
    val available = station.summary.available
    val operator = station.operatorName ?: "충전소"
    val distanceText = station.distanceKm?.let { dist ->
        if (dist > 0 && dist < 1000) {
            if (dist < 1.0) "${(dist * 1000).toInt()}m" else String.format("%.1fkm", dist)
        } else null
    }

    AppleTile(tone = Apple.C.Tile1, onClick = onClick) {
        Column(modifier = Modifier.padding(Apple.Sp.md)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station.name,
                        style = Apple.T.BodyStrong,
                        color = Apple.C.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (distanceText != null) "$operator · $distanceText" else operator,
                        style = Apple.T.FinePrint,
                        color = Apple.C.TextFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(Apple.Sp.xs))
                Column(horizontalAlignment = Alignment.End) {
                    val hasCount = station.summary.total > 0
                    Text(
                        text = if (hasCount) "$available / ${station.summary.total}"
                            else if (available > 0) "충전 가능" else "이용 불가",
                        style = Apple.T.BodyStrong,
                        color = if (available > 0) Apple.C.StatusAvailable else Apple.C.TextFaint,
                        maxLines = 1
                    )
                    if (hasCount) {
                        Text(text = "충전 가능", style = Apple.T.MicroLegal, color = Apple.C.TextFaint)
                    }
                }
            }

            Spacer(modifier = Modifier.height(Apple.Sp.xs))
            Text(
                text = station.address,
                style = Apple.T.FinePrint,
                color = Apple.C.TextFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
