package com.evsearch.app.presentation.map.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.evsearch.app.data.model.ChargerStation
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles

private const val TAG = "KakaoMapView"

/**
 * Kakao Maps v2 MapView를 Compose로 호스팅하는 컴포저블.
 * - 충전소를 원형 상태 핀(마커)으로 표시 (텍스트 없음)
 * - 핀 탭 시 요약 카드 콜백
 * - 줌 인/아웃 플로팅 버튼 지원
 */
@Composable
fun KakaoMapView(
    stations: List<ChargerStation>,
    centerLat: Double,
    centerLng: Double,
    regionName: String = "",
    userLocation: Pair<Double, Double>?,
    focusLocation: Pair<Double, Double>? = null,
    onPinClick: (ChargerStation) -> Unit,
    onMapClick: () -> Unit,
    onViewportChanged: (Double, Double, Double, Double) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var isInitialCameraSet by remember { mutableStateOf(false) }

    // Compose 생명주기 -> MapView pause/resume 연동
    DisposableEffect(lifecycleOwner, mapViewRef) {
        val mv = mapViewRef ?: return@DisposableEffect onDispose {}
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) = mv.resume()
            override fun onPause(owner: LifecycleOwner) = mv.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 외부 데이터 변경 시 라벨 갱신 (카메라 위치는 불필요하게 초기화하지 않음)
    DisposableEffect(stations, userLocation, kakaoMap) {
        val map = kakaoMap
        if (map != null) {
            MapStationsHolder.stations = stations
            renderStations(map, stations, userLocation)

            if (!isInitialCameraSet) {
                isInitialCameraSet = true
                val initialLat = userLocation?.first ?: centerLat
                val initialLng = userLocation?.second ?: centerLng
                map.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(initialLat, initialLng), 14))
            }
        }
        onDispose {}
    }

    // 검색 결과 선택 또는 첫 번째 검색 매칭 위치로 부드럽게 카메라 이동
    DisposableEffect(focusLocation, kakaoMap) {
        val map = kakaoMap
        if (map != null && focusLocation != null) {
            val (lat, lng) = focusLocation
            if (lat > 0.0 && lng > 0.0) {
                try {
                    map.moveCamera(
                        CameraUpdateFactory.newCenterPosition(LatLng.from(lat, lng), 15),
                        com.kakao.vectormap.camera.CameraAnimation.from(400, true, true)
                    )
                } catch (e: Exception) {
                    map.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(lat, lng), 15))
                }
            }
        }
        onDispose {}
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        // AndroidView 내부에서 터치 이벤트가 MapView로 전달되도록 함
        update = { },
        factory = { context ->
            MapView(context).also { mv ->
                mv.start(
                    object : MapLifeCycleCallback() {
                        override fun onMapDestroy() {
                            Log.d(TAG, "onMapDestroy")
                        }

                        override fun onMapError(error: Exception) {
                            Log.e(TAG, "Kakao Map error: ${error.message}", error)
                        }
                    },
                    object : KakaoMapReadyCallback() {
                        override fun onMapReady(map: KakaoMap) {
                            Log.d(TAG, "onMapReady")
                            kakaoMap = map
                            map.moveCamera(
                                CameraUpdateFactory.newCenterPosition(
                                    LatLng.from(centerLat, centerLng), 12
                                )
                            )
                            // 초기 viewport 설정 (첫 렌더부터 화면 밖 마커 제외)
                            val initPad = 0.052
                            MapStationsHolder.minLat = centerLat - initPad
                            MapStationsHolder.maxLat = centerLat + initPad
                            MapStationsHolder.minLng = centerLng - initPad
                            MapStationsHolder.maxLng = centerLng + initPad
                            MapStationsHolder.boundsSet = true

                            // 핀(라벨) 클릭: tag에 ChargerStation 인덱스 저장
                            map.setOnLabelClickListener(
                                object : KakaoMap.OnLabelClickListener {
                                    override fun onLabelClicked(
                                        kakaoMap: KakaoMap,
                                        layer: com.kakao.vectormap.label.LabelLayer,
                                        label: com.kakao.vectormap.label.Label
                                    ) {
                                        val tag = label.tag as? Int
                                        when {
                                            // 클러스터 버블: 해당 중심으로 줌인
                                            tag == -1 -> {
                                                val pos = MapStationsHolder.clusterPositions[label.labelId]
                                                if (pos != null) {
                                                    val nz = (MapStationsHolder.lastZoomBucket + 2).coerceIn(3, 16)
                                                    kakaoMap.moveCamera(
                                                        CameraUpdateFactory.newCenterPosition(pos, nz)
                                                    )
                                                }
                                            }
                                            // 개별 핀: 상세 카드
                                            tag != null && tag in MapStationsHolder.stations.indices ->
                                                onPinClick(MapStationsHolder.stations[tag])
                                        }
                                    }
                                }
                            )

                            // 맵 빈 영역 탭: 가장 가까운 충전소 핀 선택 (fallback)
                            map.setOnMapClickListener(
                                object : KakaoMap.OnMapClickListener {
                                    override fun onMapClicked(
                                        map: KakaoMap,
                                        latLng: LatLng,
                                        screenPoint: android.graphics.PointF,
                                        poi: com.kakao.vectormap.Poi
                                    ) {
                                        // OnLabelClickListener가 정확히 핀을 탭음 → 카드 열기
                                        // 여기서는 "핀 근처"를 탭했는지 확인해서 카드 열기/닫기 결정
                                        val NEARBY_THRESHOLD = 0.002 // 약 200m
                                        val tappedStation = MapStationsHolder.stations
                                            .firstOrNull { s ->
                                                s.lat > 0 && s.lng > 0 &&
                                                Math.abs(s.lat - latLng.latitude) < NEARBY_THRESHOLD &&
                                                Math.abs(s.lng - latLng.longitude) < NEARBY_THRESHOLD
                                            }
                                        if (tappedStation != null) {
                                            onPinClick(tappedStation)
                                        } else {
                                            onMapClick()
                                        }
                                    }
                                }
                            )

                            renderStations(map, stations, userLocation)

                            // 카메라 이동 완료 시 뷰포트 좌표 전달
                            map.setOnCameraMoveEndListener(
                                object : KakaoMap.OnCameraMoveEndListener {
                                    override fun onCameraMoveEnd(
                                        kakaoMap: KakaoMap,
                                        position: com.kakao.vectormap.camera.CameraPosition,
                                        gestureType: com.kakao.vectormap.GestureType
                                    ) {
                                        // 줌 레벨에 따른 대략적인 뷰포트 반경 계산
                                        // 줌 12 ≈ 0.05도, 줌 13 ≈ 0.025도 (반경 기준)
                                        val zoom = position.zoomLevel
                                        val radiusDeg = when {
                                            zoom >= 15 -> 0.005
                                            zoom >= 14 -> 0.01
                                            zoom >= 13 -> 0.02
                                            zoom >= 12 -> 0.04
                                            zoom >= 11 -> 0.08
                                            else -> 0.16
                                        }
                                        val center = position.position
                                        // 렌더 필터용 viewport 저장 (약간 여유를 둬 경계 마커도 포함)
                                        val pad = radiusDeg * 1.3
                                        MapStationsHolder.minLat = center.latitude - pad
                                        MapStationsHolder.maxLat = center.latitude + pad
                                        MapStationsHolder.minLng = center.longitude - pad
                                        MapStationsHolder.maxLng = center.longitude + pad
                                        MapStationsHolder.boundsSet = true

                                        // debounce: 카메라가 멈춘 뒤 한 번만 다시 그린다(이동 중 매 프레임 렌더 방지)
                                        MapStationsHolder.pendingRender?.let { MapStationsHolder.mainHandler.removeCallbacks(it) }
                                        val r = Runnable {
                                            renderStations(
                                                kakaoMap,
                                                MapStationsHolder.stations,
                                                MapStationsHolder.userLocation
                                            )
                                        }
                                        MapStationsHolder.pendingRender = r
                                        MapStationsHolder.mainHandler.postDelayed(r, 140)

                                        onViewportChanged(
                                            center.latitude - radiusDeg,
                                            center.longitude - radiusDeg,
                                            center.latitude + radiusDeg,
                                            center.longitude + radiusDeg
                                        )
                                    }
                                }
                            )
                        }
                    }
                )
                mapViewRef = mv
            }
        },
        onRelease = { mv ->
            Log.d(TAG, "onRelease MapView")
            mv.finish()
            mapViewRef = null
            kakaoMap = null
        }
    )

    // 줌 컨트롤 콜백을 외부에서 사용할 수 있도록 kakaoMap 노출
    MapZoomController.kakaoMap = kakaoMap
}

/** 줌 인/아웃 버튼에서 사용할 수 있도록 현재 맵 인스턴스를 보관하는 싱글톤 */
object MapZoomController {
    var kakaoMap: KakaoMap? = null
    fun zoomIn() = kakaoMap?.moveCamera(CameraUpdateFactory.zoomIn())
    fun zoomOut() = kakaoMap?.moveCamera(CameraUpdateFactory.zoomOut())
}

/** 클릭 리스너가 최신 stations/클러스터 상태를 참조하도록 유지하는 홀더 */
object MapStationsHolder {
    var stations: List<ChargerStation> = emptyList()
    var userLocation: Pair<Double, Double>? = null
    var lastZoomBucket: Int = -1
    val clusterPositions = HashMap<String, LatLng>()
    // 현재 화면(viewport) 범위. 이 안의 마커만 렌더해 대량 마커 랙을 없앤다.
    var minLat = -90.0; var maxLat = 90.0; var minLng = -180.0; var maxLng = 180.0
    var boundsSet = false
    // 카메라 이동 debounce
    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    var pendingRender: Runnable? = null
}

// 비트맵 캐시: 매 렌더마다 비트맵을 다시 그리지 않는다.
private val pinBitmapCache = HashMap<Int, Bitmap>()
private val clusterBitmapCache = HashMap<String, Bitmap>()

private fun cachedPinBitmap(color: Int): Bitmap =
    pinBitmapCache.getOrPut(color) { createCirclePinBitmap(color) }

private fun cachedClusterBitmap(count: Int, hasAvailable: Boolean): Bitmap {
    // 개수는 정확히 표시하되 같은 (개수,색) 조합은 캐시 재사용
    val key = "${count}_$hasAvailable"
    return clusterBitmapCache.getOrPut(key) { createClusterBitmap(count, hasAvailable) }
}

/** 이 줌 레벨 이상에서는 개별 마커, 미만에서는 격자 클러스터링 */
private const val CLUSTER_ZOOM_MAX = 15

/** 줌 레벨별 격자 셀 크기(도). 줌이 낮을수록 넓게 묶는다. */
private fun cellSizeForZoom(zoom: Int): Double = when {
    zoom >= 14 -> 0.012
    zoom >= 13 -> 0.025
    zoom >= 12 -> 0.05
    zoom >= 11 -> 0.1
    zoom >= 9 -> 0.25
    else -> 0.6
}

/**
 * 충전소를 원형 상태 핀만으로 렌더링 (텍스트 라벨 없음).
 * - 사용 가능: 틸(녹색), 불가: 회색
 * - 내 위치: 파란색 핀
 */
private fun renderStations(
    map: KakaoMap,
    stations: List<ChargerStation>,
    userLocation: Pair<Double, Double>?
) {
    // 전체 목록은 클릭 조회용으로 보관하되, 렌더는 화면(viewport) 안 마커로 한정한다.
    val allValid = stations.filter { it.lat > 0.0 && it.lng > 0.0 }
    MapStationsHolder.stations = allValid
    MapStationsHolder.userLocation = userLocation

    val labelManager = map.labelManager ?: run {
        Log.e(TAG, "labelManager is null!")
        return
    }
    val layer = labelManager.layer ?: run {
        Log.e(TAG, "labelManager.layer is null!")
        return
    }
    layer.removeAll()
    MapStationsHolder.clusterPositions.clear()

    // viewport 필터: 화면 밖 마커는 렌더하지 않는다(대량 마커 랙 방지).
    val visible = if (MapStationsHolder.boundsSet) {
        allValid.withIndex().filter { (_, s) ->
            s.lat in MapStationsHolder.minLat..MapStationsHolder.maxLat &&
                s.lng in MapStationsHolder.minLng..MapStationsHolder.maxLng
        }
    } else {
        allValid.withIndex().toList()
    }

    // 핀 스타일 등록 (비트맵은 캐시 재사용)
    val styleId = System.currentTimeMillis()
    val availableStyle = labelManager.addLabelStyles(
        LabelStyles.from("pin_avail_$styleId", LabelStyle.from(cachedPinBitmap(0xFF00C896.toInt())))
    ) ?: return
    val chargevStyle = labelManager.addLabelStyles(
        LabelStyles.from("pin_chargev_$styleId", LabelStyle.from(cachedPinBitmap(0xFF3B82F6.toInt())))
    ) ?: return
    val unavailableStyle = labelManager.addLabelStyles(
        LabelStyles.from("pin_unavail_$styleId", LabelStyle.from(cachedPinBitmap(0xFF64748B.toInt())))
    ) ?: return
    val meStyle = labelManager.addLabelStyles(
        LabelStyles.from("pin_me_$styleId", LabelStyle.from(createMeLocationBitmap()))
    ) ?: return

    fun styleFor(s: ChargerStation) = when {
        s.isChargeV -> chargevStyle
        s.summary.available > 0 -> availableStyle
        else -> unavailableStyle
    }
    fun addIndividual(index: Int, s: ChargerStation) {
        val options = LabelOptions.from("station_${s.statId}_$index", LatLng.from(s.lat, s.lng))
            .setStyles(styleFor(s))
            .setTag(index) // 개별 핀: 인덱스로 ChargerStation 조회 (allValid 기준 인덱스)
            .setClickable(true)
        layer.addLabel(options)
    }

    val zoom = try { map.cameraPosition?.zoomLevel ?: 12 } catch (e: Exception) { 12 }
    MapStationsHolder.lastZoomBucket = zoom

    if (zoom >= CLUSTER_ZOOM_MAX || visible.size <= 1) {
        // 개별 마커 (화면 안만)
        visible.forEach { (index, s) -> addIndividual(index, s) }
    } else {
        // 격자 클러스터링: 셀당 1개면 개별, 2개 이상이면 개수 버블 (화면 안만)
        val cell = cellSizeForZoom(zoom)
        val groups = HashMap<Long, MutableList<Int>>()
        visible.forEach { (i, s) ->
            val gx = Math.floor(s.lat / cell).toInt()
            val gy = Math.floor(s.lng / cell).toInt()
            val key = gx.toLong() * 1_000_000L + gy
            groups.getOrPut(key) { mutableListOf() }.add(i)
        }
        var clusterSeq = 0
        groups.values.forEach { idxs ->
            if (idxs.size == 1) {
                addIndividual(idxs[0], allValid[idxs[0]])
            } else {
                val avgLat = idxs.sumOf { allValid[it].lat } / idxs.size
                val avgLng = idxs.sumOf { allValid[it].lng } / idxs.size
                val hasAvail = idxs.any { allValid[it].summary.available > 0 }
                val clusterId = "cluster_${styleId}_${clusterSeq++}"
                val cStyle = labelManager.addLabelStyles(
                    LabelStyles.from(clusterId, LabelStyle.from(cachedClusterBitmap(idxs.size, hasAvail)))
                ) ?: return@forEach
                val opt = LabelOptions.from(clusterId, LatLng.from(avgLat, avgLng))
                    .setStyles(cStyle)
                    .setTag(-1) // 클러스터 표시
                    .setClickable(true)
                layer.addLabel(opt)
                MapStationsHolder.clusterPositions[clusterId] = LatLng.from(avgLat, avgLng)
            }
        }
    }

    // 내 위치 핀
    userLocation?.let { (lat, lng) ->
        val meOptions = LabelOptions.from("me_location", LatLng.from(lat, lng))
            .setStyles(meStyle)
        layer.addLabel(meOptions)
    }
}

/** 클러스터 개수 버블 비트맵 (개수·가용여부에 따라 크기/색상). */
private fun createClusterBitmap(count: Int, hasAvailable: Boolean): Bitmap {
    val radius = when {
        count < 10 -> 34
        count < 50 -> 42
        count < 100 -> 50
        else -> 58
    }
    val size = radius * 2 + 10
    val center = size / 2f
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val main = if (hasAvailable) 0xFF00C896.toInt() else 0xFF64748B.toInt()

    // 반투명 외곽 헤일로
    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (main and 0x00FFFFFF) or 0x40000000 }
    canvas.drawCircle(center, center, radius + 4f, halo)
    // 흰색 링
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    canvas.drawCircle(center, center, radius + 1.5f, ring)
    // 메인 원
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = main }
    canvas.drawCircle(center, center, radius.toFloat(), fill)
    // 개수 텍스트
    val text = if (count >= 100) "99+" else count.toString()
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = radius * 0.85f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val ty = center - (tp.descent() + tp.ascent()) / 2f
    canvas.drawText(text, center, ty, tp)
    return bitmap
}

/** 깔끔하고 선명한 고해상도 원형 마커 비트맵 생성 (외부 흰색 테두리 + 내부 고대비 컬러) */
private fun createCirclePinBitmap(fillColor: Int, radiusPx: Int = 30): Bitmap {
    val size = (radiusPx * 2) + 8
    val center = size / 2f
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // 1. 외부 그림자/흰색 링
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, radiusPx + 3f, borderPaint)

    // 2. 메인 컬러 원
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, radiusPx.toFloat(), fillPaint)

    // 3. 내부 미니 도트
    val innerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, radiusPx * 0.35f, innerDotPaint)

    return bitmap
}

/** 내 위치 전용 블루 펄스 마커 */
private fun createMeLocationBitmap(radiusPx: Int = 32): Bitmap {
    val size = (radiusPx * 2) + 8
    val center = size / 2f
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // 반투명 외곽 링
    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x403B82F6.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, center, haloPaint)

    // 흰색 테두리
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, radiusPx * 0.7f + 2f, borderPaint)

    // 중앙 파란 점
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2563EB.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, radiusPx * 0.7f, fillPaint)

    return bitmap
}
