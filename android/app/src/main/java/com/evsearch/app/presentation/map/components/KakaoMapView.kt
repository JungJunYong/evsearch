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

                            // 핀(라벨) 클릭: tag에 ChargerStation 인덱스 저장
                            map.setOnLabelClickListener(
                                object : KakaoMap.OnLabelClickListener {
                                    override fun onLabelClicked(
                                        kakaoMap: KakaoMap,
                                        layer: com.kakao.vectormap.label.LabelLayer,
                                        label: com.kakao.vectormap.label.Label
                                    ) {
                                        val index = label.tag as? Int
                                        if (index != null && index in MapStationsHolder.stations.indices) {
                                            onPinClick(MapStationsHolder.stations[index])
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
                                        val minLat = center.latitude - radiusDeg
                                        val maxLat = center.latitude + radiusDeg
                                        val minLng = center.longitude - radiusDeg
                                        val maxLng = center.longitude + radiusDeg
                                        Log.d(TAG, "Viewport changed: center=(${center.latitude}, ${center.longitude}), zoom=$zoom, r=$radiusDeg")
                                        onViewportChanged(minLat, minLng, maxLat, maxLng)
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

/** 클릭 리스너가 최신 stations를 참조하도록 유지하는 홀더 */
object MapStationsHolder {
    var stations: List<ChargerStation> = emptyList()
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
    Log.d(TAG, "renderStations called: stations=${stations.size}, userLocation=$userLocation")
    val labelManager = map.labelManager ?: run {
        Log.e(TAG, "labelManager is null!")
        return
    }
    val layer = labelManager.layer ?: run {
        Log.e(TAG, "labelManager.layer is null!")
        return
    }
    layer.removeAll()

    // 핀 스타일 등록 (ID에 타임스탬프를 붙여서 항상 새로 등록 — 이전 캐시 무효화)
    val styleId = System.currentTimeMillis()
    val availableStyle = labelManager.addLabelStyles(
        LabelStyles.from("pin_avail_$styleId", LabelStyle.from(createCirclePinBitmap(0xFF00C896.toInt())))
    ) ?: run {
        Log.e(TAG, "availableStyle is null!")
        return
    }
    val unavailableStyle = labelManager.addLabelStyles(
        LabelStyles.from("pin_unavail_$styleId", LabelStyle.from(createCirclePinBitmap(0xFF64748B.toInt())))
    ) ?: run {
        Log.e(TAG, "unavailableStyle is null!")
        return
    }
    val meStyle = labelManager.addLabelStyles(
        LabelStyles.from("pin_me_$styleId", LabelStyle.from(createCirclePinBitmap(0xFF3B82F6.toInt())))
    ) ?: run {
        Log.e(TAG, "meStyle is null!")
        return
    }

    Log.d(TAG, "Styles registered, adding ${stations.size} labels")

    // 충전소 핀 렌더링
    stations.filter { it.lat > 0 && it.lng > 0 }.forEachIndexed { index, station ->
        val isAvailable = station.summary.available > 0
        val options = LabelOptions.from("station_$index", LatLng.from(station.lat, station.lng))
            .setStyles(if (isAvailable) availableStyle else unavailableStyle)
            .setTag(index) // 클릭 시 인덱스로 ChargerStation 조회
            .setClickable(true)

        val label = layer.addLabel(options)
        if (label == null) {
            Log.e(TAG, "Failed to add label for station_$index")
        }
    }

    // 내 위치 핀
    userLocation?.let { (lat, lng) ->
        val meOptions = LabelOptions.from("me_location", LatLng.from(lat, lng))
            .setStyles(meStyle)
        layer.addLabel(meOptions)
    }
}

/** 깔끔한 원형 점 마커 비트맵 생성 (순수 원, 테두리/중앙점 없음) */
private fun createCirclePinBitmap(color: Int, radiusPx: Int = 20): Bitmap {
    val size = radiusPx * 2
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // 단일 컬러 원 (깔끔한 픽셀 느낌)
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    canvas.drawCircle(radiusPx.toFloat(), radiusPx.toFloat(), radiusPx.toFloat(), fillPaint)

    return bitmap
}
