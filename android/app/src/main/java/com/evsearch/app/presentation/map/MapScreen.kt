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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evsearch.app.data.model.ChargerStation
import com.evsearch.app.presentation.common.EvSearchTopBar
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
        topBar = {
            EvSearchTopBar(
                title = "EV 충전소",
                subtitle = if (searchQuery.isNotBlank()) "검색 결과 ${filteredStations.size}개소" else "주변 ${visibleStations.size}개소"
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val focusManager = LocalFocusManager.current

            // Samsung One UI Continuous Curved Squircle Search Bar (28dp radius)
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
                        text = "충전소명, 아파트명, 단말기 번호 검색 (엔터로 검색)",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                trailingIcon = {
                    when {
                        uiState.isSearching -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        searchQuery.isNotBlank() -> {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "검색어 지우기",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                shape = RoundedCornerShape(28.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
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
                    .clip(RoundedCornerShape(26.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(26.dp))
                    .background(Color(0xFF0B0D14))
            ) {
                KakaoMapView(
                    stations = filteredStations,
                    centerLat = userLocation?.first ?: 37.5665,
                    centerLng = userLocation?.second ?: 126.9780,
                    userLocation = userLocation,
                    focusLocation = focusLocation,
                    onPinClick = { station -> selectedStation = station },
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
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "내 위치로 이동",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // 줌 인
                    Surface(
                        onClick = { com.evsearch.app.presentation.map.components.MapZoomController.zoomIn() },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "+",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 줌 아웃
                    Surface(
                        onClick = { com.evsearch.app.presentation.map.components.MapZoomController.zoomOut() },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "−",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (uiState.isLoading && uiState.stations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0B0D14).copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "전국 충전소 동기화 중...",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
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
                    text = if (searchQuery.isNotBlank()) "🔍 검색 결과 (${visibleStations.size}개소)" else "🗺️ 주변 충전소 (${visibleStations.size}개소)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (userLocation != null) "📍 GPS 연결됨" else "전국 모니터링",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Station Card List
            when {
                uiState.errorMessage != null -> {
                    EmptyMapState(
                        icon = "⚠️",
                        title = "데이터를 불러오지 못했습니다",
                        description = uiState.errorMessage ?: "네트워크 상태를 확인한 후 다시 시도해주세요."
                    )
                }
                filteredStations.isEmpty() && !uiState.isSearching -> {
                    EmptyMapState(
                        icon = "🔌",
                        title = "검색된 충전소가 없습니다",
                        description = "충전소 명칭이나 아파트명을 변경하여 다시 검색해보세요."
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(visibleStations) { station ->
                            SamsungOneUIStationCard(
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

/**
 * One UI 9.0 스타일 빈 상태 화면
 */
@Composable
private fun EmptyMapState(
    icon: String,
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = icon, fontSize = 36.sp)
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/**
 * One UI 9.0 스타일 선택 충전소 요약 카드 (지도 하단 플로팅)
 */
@Composable
private fun SelectedStationSummaryCard(
    station: ChargerStation,
    onDetailClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = if (station.summary.available > 0) Color(0xFF00C896) else Color(0xFF64748B)
    val statusText = if (station.summary.available > 0) "사용가능" else "이용불가"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 상태 도트
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station.name,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = station.address,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "닫기",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onClose() }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "$statusText ${station.summary.available}/${station.summary.total}대",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "상세정보 / 위젯 등록 →",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onDetailClick() }
                )
            }
        }
    }
}

/**
 * Samsung One UI 9.0 Styled Station Card (26dp Squircle)
 */
@Composable
fun SamsungOneUIStationCard(
    station: ChargerStation,
    onClick: () -> Unit
) {
    val isAvailable = station.summary.available > 0
    val operator = station.operatorName ?: "충전소"
    val isChargeV = operator.contains("차지비") || operator.contains("ChargEV")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isChargeV) Color(0xFF3B82F6).copy(alpha = 0.15f) else (if (isAvailable) Color(0xFF00C896).copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (isChargeV) Color(0xFF3B82F6) else (if (isAvailable) Color(0xFF00C896) else Color.Gray),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = station.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = operator,
                            fontSize = 11.sp,
                            fontWeight = if (isChargeV) FontWeight.Bold else FontWeight.Normal,
                            color = if (isChargeV) Color(0xFF3B82F6) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    // 거리 표시
                    station.distanceKm?.let { dist ->
                        if (dist > 0 && dist < 1000) {
                            Text(
                                text = if (dist < 1.0) "${(dist * 1000).toInt()}m" else String.format("%.1fkm", dist),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF3B82F6),
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                    }

                    // 사용가능 표시 (⚡ N/N)
                    Surface(
                        color = if (isAvailable) Color(0xFF00C896).copy(alpha = 0.15f) else Color(0xFF64748B).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (isAvailable) "⚡ ${station.summary.available}/${station.summary.total} 가용" else "✕ 이용불가",
                            color = if (isAvailable) Color(0xFF00C896) else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = station.address,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
