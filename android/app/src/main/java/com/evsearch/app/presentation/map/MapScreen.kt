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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evsearch.app.data.model.ChargerStation
import com.evsearch.app.presentation.common.EvSearchTopBar
import com.evsearch.app.presentation.map.components.KakaoMapView

data class RegionCode(val code: String, val name: String, val lat: Double, val lng: Double)

val REGION_CODES = listOf(
    RegionCode("all", "전국", 37.5665, 126.9780),
    RegionCode("11", "서울", 37.5665, 126.9780),
    RegionCode("41", "경기", 37.2750, 127.0094),
    RegionCode("28", "인천", 37.4563, 126.7052),
    RegionCode("51", "강원", 37.8853, 127.7298),
    RegionCode("36", "세종", 36.4800, 127.2890),
    RegionCode("30", "대전", 36.3504, 127.3845),
    RegionCode("43", "충북", 36.6372, 127.4897),
    RegionCode("44", "충남", 36.6588, 126.6728),
    RegionCode("26", "부산", 35.1796, 129.0756),
    RegionCode("27", "대구", 35.8714, 128.6014),
    RegionCode("31", "울산", 35.5384, 129.3114),
    RegionCode("47", "경북", 36.5760, 128.5056),
    RegionCode("48", "경남", 35.2383, 128.6924),
    RegionCode("29", "광주", 35.1595, 126.8526),
    RegionCode("45", "전북", 35.8203, 127.1088),
    RegionCode("46", "전남", 34.8161, 126.4629),
    RegionCode("50", "제주", 33.4996, 126.5312)
)

/**
 * Samsung One UI 9.0 Styled EV Search Map Screen (Clean Minimal Top Bar)
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

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            com.evsearch.app.utils.LocationHelper.getCurrentLocation(context) { loc ->
                if (loc != null) {
                    userLocation = Pair(loc.latitude, loc.longitude)
                    val matchingZcode = com.evsearch.app.utils.LocationHelper.getMatchingRegionCode(loc.latitude, loc.longitude)
                    val region = REGION_CODES.find { it.code == matchingZcode } ?: REGION_CODES[0]
                    viewModel.loadStations(region.code, region.name)
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
                        userLocation = Pair(loc.latitude, loc.longitude)
                        val matchingZcode = com.evsearch.app.utils.LocationHelper.getMatchingRegionCode(loc.latitude, loc.longitude)
                        val region = REGION_CODES.find { it.code == matchingZcode } ?: REGION_CODES[0]
                        viewModel.loadStations(region.code, region.name)
                    }
                }
            } else {
                // 권한이 없으면 자동으로 권한 요청
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
                    userLocation = Pair(loc.latitude, loc.longitude)
                    val matchingZcode = com.evsearch.app.utils.LocationHelper.getMatchingRegionCode(loc.latitude, loc.longitude)
                    val region = REGION_CODES.find { it.code == matchingZcode } ?: REGION_CODES[0]
                    viewModel.loadStations(region.code, region.name)
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
                s.address.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // 지도에서 핀으로 선택된 충전소
    var selectedStation by remember { mutableStateOf<ChargerStation?>(null) }

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

    val visibleStations = remember(filteredStations, viewport, userLocation, uiState.selectedZcode) {
        val vp = viewport
        // 거리 기준점: 내 위치 > 뷰포트 중심 > 지역 중심 > null
        val baseLat: Double? = userLocation?.first
            ?: vp?.let { (it.minLat + it.maxLat) / 2 }
            ?: REGION_CODES.find { it.code == uiState.selectedZcode }?.lat
        val baseLng: Double? = userLocation?.second
            ?: vp?.let { (it.minLng + it.maxLng) / 2 }
            ?: REGION_CODES.find { it.code == uiState.selectedZcode }?.lng

        val filtered = if (vp == null) {
            filteredStations
        } else {
            filteredStations.filter { s ->
                s.lat > 0 && s.lng > 0 &&
                s.lat >= vp.minLat && s.lat <= vp.maxLat &&
                s.lng >= vp.minLng && s.lng <= vp.maxLng
            }
        }

        // 거리 계산 후 가까운 순 정렬 (distanceKm 필드에 거리 저장)
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
                subtitle = "내 주변 ${visibleStations.size}개소"
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Samsung One UI Continuous Curved Squircle Search Bar (28dp radius)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("충전소 명칭 또는 주소 검색", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
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

            // Samsung One UI Region Selector Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(REGION_CODES) { region ->
                    val isSelected = uiState.selectedZcode == region.code
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            searchQuery = ""
                            viewModel.loadStations(region.code, region.name)
                        },
                        label = {
                            Text(
                                text = region.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color(0xFF121621),
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // Samsung One UI 9.0 Squircle Interactive Vector Map Container (26dp radius)
            val currentRegion = REGION_CODES.find { it.code == uiState.selectedZcode } ?: REGION_CODES[0]
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
                    centerLat = currentRegion.lat,
                    centerLng = currentRegion.lng,
                    regionName = uiState.selectedZcodeName,
                    userLocation = userLocation,
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

            // Samsung One UI Section Summary Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🗺️ 지도에 보이는 충전소 (${visibleStations.size}개소)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (userLocation != null) "📍 GPS 수신 완료" else "Samsung Knox Protected",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Samsung One UI Station Card List
            when {
                uiState.errorMessage != null -> {
                    EmptyMapState(
                        icon = "⚠️",
                        title = "데이터를 불러오지 못했습니다",
                        description = uiState.errorMessage ?: "네트워크 상태를 확인한 후 다시 시도해주세요."
                    )
                }
                filteredStations.isEmpty() -> {
                    EmptyMapState(
                        icon = "🔌",
                        title = "충전소가 없습니다",
                        description = "선택하신 지역에 등록된 충전소가 없습니다.\n다른 지역을 선택하거나 검색어를 변경해보세요."
                    )
                }
                visibleStations.isEmpty() -> {
                    EmptyMapState(
                        icon = "🗺️",
                        title = "이 지도 영역에는 충전소가 없습니다",
                        description = "지도를 이동하거나 줌아웃하면 더 많은 충전소를 찾을 수 있어요."
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(visibleStations) { station ->
                            SamsungOneUIStationCard(station = station, onClick = { onStationClick(station.statId) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * One UI 9.0 스타일 빈 상태/에러 화면 (아이콘 + 안내 문구)
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
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = icon, fontSize = 42.sp)
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = title,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            fontSize = 13.sp,
            lineHeight = 19.sp,
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
            .clip(RoundedCornerShape(26.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(26.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                        fontSize = 16.sp,
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
                        .size(22.dp)
                        .clickable { onClose() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "상세정보 보기 →",
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
    val maxOutput = station.chargers.maxOfOrNull { it.outputKw?.toIntOrNull() ?: 0 } ?: 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .clip(RoundedCornerShape(26.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(26.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = if (isAvailable) Color(0xFF00C896).copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (isAvailable) Color(0xFF00C896) else Color.Gray,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = station.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = station.operatorName ?: "공공 충전소",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 거리 표시 (간결하게: 숫자만)
                station.distanceKm?.let { dist ->
                    Text(
                        text = if (dist < 1.0) "${(dist * 1000).toInt()}m" else String.format("%.1f", dist),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3B82F6),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                // 사용가능 표시 (간결하게: ⚡ N/N)
                Surface(
                    color = if (isAvailable) Color(0xFF00C896).copy(alpha = 0.15f) else Color(0xFF64748B).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isAvailable) "⚡${station.summary.available}/${station.summary.total}" else "✕",
                        color = if (isAvailable) Color(0xFF00C896) else Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = station.address,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    OneUIStatusPill(count = station.summary.available, label = "대기", color = Color(0xFF00C896))
                    OneUIStatusPill(count = station.summary.charging, label = "충전중", color = Color(0xFF2067F9))
                    OneUIStatusPill(count = station.summary.maintenance, label = "점검", color = Color(0xFFFF4757))
                }

                if (maxOutput > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "⚡ 최대 ${maxOutput}kW",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OneUIStatusPill(count: Int, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = "$label $count",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
