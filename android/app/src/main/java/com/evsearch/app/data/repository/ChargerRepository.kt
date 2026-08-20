package com.evsearch.app.data.repository

import android.content.Context
import com.evsearch.app.data.api.BffApiService
import com.evsearch.app.data.local.SavedChargerDao
import com.evsearch.app.data.local.SavedChargerEntity
import com.evsearch.app.data.model.BatchStatusKey
import com.evsearch.app.data.model.BatchStatusRequest
import com.evsearch.app.data.model.Charger
import com.evsearch.app.data.model.ChargerStation
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChargerRepository(
    private val apiService: BffApiService,
    private val savedChargerDao: SavedChargerDao,
    private val context: Context? = null
) {

    /** 홈 위젯에 표시할 목록. */
    fun getWidgetChargersFlow(): Flow<List<SavedChargerEntity>> = savedChargerDao.getWidgetChargersFlow()

    /** 즐겨찾기(빈자리 알림 대상) 목록. */
    fun getFavoriteChargersFlow(): Flow<List<SavedChargerEntity>> = savedChargerDao.getFavoriteChargersFlow()

    /** 두 목록의 합집합. 상세 화면의 등록 여부 표시에 사용. */
    fun getTrackedChargersFlow(): Flow<List<SavedChargerEntity>> = savedChargerDao.getTrackedChargersFlow()

    private var inMemoryStationCache: Map<String, ChargerStation> = emptyMap()

    fun cacheStationsInMemory(stations: List<ChargerStation>) {
        if (stations.isNotEmpty()) {
            val map = inMemoryStationCache.toMutableMap()
            stations.forEach { map[it.statId] = it }
            inMemoryStationCache = map
        }
    }

    suspend fun getStations(zcode: String? = null, zscode: String? = null): Result<List<ChargerStation>> {
        return try {
            val targetZcode = if (zcode == "all") null else zcode
            val response = apiService.getStations(zcode = targetZcode, zscode = zscode)
            if (response.success) {
                cacheStationsInMemory(response.data)
                Result.success(response.data)
            } else {
                Result.failure(Exception("충전소 정보를 불러오지 못했습니다."))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /** 통합 검색: KECO + ChargEV를 BFF가 합쳐서 반환 (앱은 소스 구분 없이 사용). */
    suspend fun searchStations(keyword: String): Result<List<ChargerStation>> {
        if (keyword.isBlank()) return Result.success(emptyList())
        return try {
            val response = apiService.searchStations(keyword)
            if (response.success && response.data.isNotEmpty()) {
                cacheStationsInMemory(response.data)
                Result.success(response.data)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.success(emptyList())
        }
    }

    /** 통합 지도 마커: 화면 bounds 내 KECO + ChargEV 경량 마커 (클러스터링용). */
    suspend fun getMapMarkers(
        swLat: Double,
        swLng: Double,
        neLat: Double,
        neLng: Double
    ): Result<List<com.evsearch.app.data.model.StationMarker>> {
        return try {
            val response = apiService.getMapMarkers(swLat, swLng, neLat, neLng)
            if (response.success) Result.success(response.data) else Result.success(emptyList())
        } catch (e: Exception) {
            e.printStackTrace()
            Result.success(emptyList())
        }
    }

    /** 전국 ChargEV 마커를 지도 표시용 경량 ChargerStation 목록으로 변환. */
    suspend fun getChargevPoiStations(): Result<List<ChargerStation>> {
        return try {
            val response = apiService.getChargevPoi()
            if (response.success) {
                val stations = response.data.map { m ->
                    ChargerStation(
                        statId = m.statId,
                        name = m.name,
                        address = "",
                        addressDetail = null,
                        lat = m.lat,
                        lng = m.lng,
                        useTime = null,
                        operatorName = m.operatorName ?: "GS차지비 (ChargEV)",
                        operatorCall = null,
                        parkingFree = null,
                        note = null,
                        zcode = null,
                        zscode = null,
                        updatedAt = "",
                        chargers = emptyList(),
                        summary = com.evsearch.app.data.model.StationSummary(
                            total = 0,
                            available = if (m.available) 1 else 0,
                            charging = 0,
                            maintenance = if (m.available) 0 else 1,
                            unknown = 0
                        ),
                        dataSource = "chargev-search"
                    )
                }
                Result.success(stations)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.success(emptyList())
        }
    }

    // 하위 호환: 기존 호출부가 남아있을 수 있어 유지 (내부적으로 통합 검색 사용)
    suspend fun searchChargevStations(keyword: String): Result<List<ChargerStation>> = searchStations(keyword)

    suspend fun getStationDetail(statId: String): Result<ChargerStation> {
        // 1) 방금 받은 목록/검색 결과가 있으면 즉시 표시 (같은 BFF 응답의 메모리 캐시)
        inMemoryStationCache[statId]?.let { return Result.success(it) }

        // 2) BFF 상세 조회
        return try {
            val response = apiService.getStationDetail(statId)
            if (response.success) {
                cacheStationsInMemory(listOf(response.data))
                Result.success(response.data)
            } else {
                Result.failure(Exception("충전소 정보를 찾을 수 없습니다."))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("충전소 정보를 불러오지 못했습니다."))
        }
    }

    private fun nowStamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())

    private fun toEntity(
        station: ChargerStation,
        charger: Charger,
        isWidget: Boolean,
        isFavorite: Boolean
    ) = SavedChargerEntity(
        key = "${station.statId}:${charger.chgerId}",
        statId = station.statId,
        chgerId = charger.chgerId,
        stationName = station.name,
        chargerTypeName = charger.typeName,
        outputKw = charger.outputKw,
        status = charger.status,
        statusCode = charger.statusCode,
        statusUpdatedAt = charger.statusUpdatedAt,
        lastFetchedAt = nowStamp(),
        stateSinceAt = charger.lastChargeStartedAt ?: charger.statusUpdatedAt,
        isWidget = isWidget,
        isFavorite = isFavorite
    )

    /** 위젯 목록에 추가 (즐겨찾기 소속은 건드리지 않는다). */
    suspend fun addChargerToWidget(station: ChargerStation, charger: Charger) {
        val key = "${station.statId}:${charger.chgerId}"
        val existing = savedChargerDao.getSavedChargerByKey(key)
        if (existing != null) {
            savedChargerDao.setWidgetFlag(key, true)
        } else {
            savedChargerDao.insertOrUpdate(toEntity(station, charger, isWidget = true, isFavorite = false))
        }
        afterListChanged()
    }

    /** 즐겨찾기 목록에 추가 (위젯 소속은 건드리지 않는다). */
    suspend fun addChargerToFavorites(station: ChargerStation, charger: Charger) {
        val key = "${station.statId}:${charger.chgerId}"
        val existing = savedChargerDao.getSavedChargerByKey(key)
        if (existing != null) {
            savedChargerDao.setFavoriteFlag(key, true)
        } else {
            savedChargerDao.insertOrUpdate(toEntity(station, charger, isWidget = false, isFavorite = true))
        }
        afterListChanged()
    }

    /** 여러 대를 한 번에 위젯 목록에 넣는다(서버 재등록은 마지막에 한 번만). */
    suspend fun addChargersToWidget(station: ChargerStation, chargers: List<Charger>) {
        for (charger in chargers) {
            val key = "${station.statId}:${charger.chgerId}"
            val existing = savedChargerDao.getSavedChargerByKey(key)
            if (existing != null) {
                savedChargerDao.setWidgetFlag(key, true)
            } else {
                savedChargerDao.insertOrUpdate(toEntity(station, charger, isWidget = true, isFavorite = false))
            }
        }
        afterListChanged()
    }

    /** 하위 호환: 기존 호출부(위젯 등록). */
    suspend fun saveChargerToWidget(station: ChargerStation, charger: Charger) =
        addChargerToWidget(station, charger)

    suspend fun removeChargerFromWidget(key: String) {
        savedChargerDao.setWidgetFlag(key, false)
        savedChargerDao.deleteOrphans()
        afterListChanged()
    }

    suspend fun removeChargerFromFavorites(key: String) {
        savedChargerDao.setFavoriteFlag(key, false)
        savedChargerDao.deleteOrphans()
        afterListChanged()
    }

    /** 즐겨찾기 항목별 알림 수신 여부. */
    suspend fun setChargerAlertEnabled(key: String, enabled: Boolean) {
        savedChargerDao.setAlertEnabled(key, enabled)
        syncAlertSubscription()
    }

    suspend fun updateChargerCustomName(key: String, customName: String?) {
        savedChargerDao.updateCustomName(key, customName?.takeIf { it.isNotBlank() })

        if (context != null) {
            com.evsearch.app.widget.WidgetUpdateHelper.updateAllWidgets(context)
        }
    }

    /** 목록이 바뀌면 위젯을 다시 그리고 서버 감시 대상도 맞춘다. */
    private suspend fun afterListChanged() {
        if (context != null) {
            com.evsearch.app.widget.WidgetUpdateHelper.updateAllWidgets(context)
        }
        syncAlertSubscription()
    }

    /** FCM 등록 토큰 획득 (Firebase 미설정 시 null). */
    private suspend fun fcmToken(): String? = kotlin.coroutines.suspendCoroutine { cont ->
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { cont.resumeWith(Result.success(it)) }
                .addOnFailureListener { cont.resumeWith(Result.success(null)) }
        } catch (e: Exception) {
            cont.resumeWith(Result.success(null))
        }
    }

    /**
     * 서버 감시 대상 동기화.
     *
     * 서버 폴링은 **알림을 켰을 때만** 돌아야 하므로, 알림이 꺼져 있거나 감시 대상이 없으면
     * 구독을 해지한다. 알림이 켜져 있으면 즐겨찾기(항목 알림 ON)는 notify=true로,
     * 위젯 전용 항목은 notify=false로 올려 상태 변화 시 위젯만 갱신하게 한다.
     */
    suspend fun syncAlertSubscription(): Result<Unit> {
        val ctx = context ?: return Result.failure(Exception("context 없음"))
        return try {
            val alertOn = com.evsearch.app.alert.AlertPrefs.getEnabled(ctx)
            val tracked = savedChargerDao.getTrackedChargers()
            val notifyTargets = tracked.count { it.isFavorite && it.alertEnabled }

            // 알림 OFF 또는 알릴 대상이 없으면 서버 조회를 멈춘다(15분 주기·수동 새로고침만).
            if (!alertOn || tracked.isEmpty() || notifyTargets == 0) {
                unsubscribeVacancyAlert()
                return Result.success(Unit)
            }

            val keys = tracked.map {
                com.evsearch.app.data.model.AlertWatchKey(
                    statId = it.statId,
                    chgerId = it.chgerId,
                    notify = it.isFavorite && it.alertEnabled
                )
            }

            val token = fcmToken()
                ?: return Result.failure(Exception("FCM 토큰을 가져올 수 없습니다 (Firebase 설정 확인)"))
            com.evsearch.app.alert.AlertPrefs.setToken(ctx, token)

            apiService.subscribeAlert(
                com.evsearch.app.data.model.AlertSubscribeRequest(
                    token = token,
                    keys = keys,
                    startMin = com.evsearch.app.alert.AlertPrefs.getStartMin(ctx),
                    endMin = com.evsearch.app.alert.AlertPrefs.getEndMin(ctx),
                    intervalSec = com.evsearch.app.alert.AlertPrefs.getIntervalSec(ctx),
                    enabled = true,
                    silentSync = true
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /** 빈자리 알림 켜기: 설정 저장 후 감시 대상을 서버에 등록. */
    suspend fun subscribeVacancyAlert(startMin: Int, endMin: Int): Result<Unit> {
        val ctx = context ?: return Result.failure(Exception("context 없음"))
        com.evsearch.app.alert.AlertPrefs.setEnabled(ctx, true)
        com.evsearch.app.alert.AlertPrefs.setStartMin(ctx, startMin)
        com.evsearch.app.alert.AlertPrefs.setEndMin(ctx, endMin)
        return syncAlertSubscription()
    }

    /** 빈자리 알림 끄기: 알림만 끄고 위젯 실시간 동기화는 유지. */
    suspend fun disableVacancyAlert(): Result<Unit> {
        val ctx = context ?: return Result.success(Unit)
        com.evsearch.app.alert.AlertPrefs.setEnabled(ctx, false)
        return syncAlertSubscription()
    }

    /** 서버 구독 완전 해지. */
    suspend fun unsubscribeVacancyAlert(): Result<Unit> {
        return try {
            val ctx = context ?: return Result.success(Unit)
            val token = com.evsearch.app.alert.AlertPrefs.getToken(ctx) ?: fcmToken() ?: return Result.success(Unit)
            apiService.unsubscribeAlert(com.evsearch.app.data.model.AlertUnsubscribeRequest(token))
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * 위젯 + 즐겨찾기 목록의 상태를 BFF에서 일괄 조회해 Room을 갱신한다.
     *
     * @param maxAgeMs 서버 캐시 허용 나이. 위젯 실시간 갱신 경로에서는 짧게(기본 20초) 준다.
     */
    /**
     * 위젯 + 즐겨찾기 목록의 상태를 BFF에서 일괄 조회해 Room을 갱신한다.
     *
     * 데이터 출처는 BFF(evsearch.wiqio.com) 하나뿐이다. 조회가 실패하면 아무것도 쓰지 않고
     * Room의 마지막 정상 상태를 그대로 유지한다(값을 임의로 만들지 않는다).
     *
     * @param maxAgeMs 서버 캐시 허용 나이. 위젯 갱신 경로에서는 짧게(기본 20초) 준다.
     */
    suspend fun refreshTrackedChargersStatus(maxAgeMs: Long = 20_000L): Result<Unit> {
        val saved = savedChargerDao.getTrackedChargers()
        if (saved.isEmpty()) return Result.success(Unit)

        val keys = saved.map { BatchStatusKey(it.statId, it.chgerId) }
        return try {
            val response = apiService.getBatchStatus(BatchStatusRequest(keys, maxAgeMs))
            if (!response.success) return Result.failure(Exception("상태를 불러오지 못했습니다."))

            val resultsMap = response.data
            val now = nowStamp()
            for (entity in saved) {
                val updated = resultsMap[entity.key] ?: continue
                // 상태가 그대로면 기존 시작 시각을 유지하고, 바뀌었으면 서버가 준 시각으로 갱신한다.
                val stateSince = if (updated.status == entity.status)
                    entity.stateSinceAt ?: updated.lastChargeStartedAt ?: updated.statusUpdatedAt
                else
                    updated.lastChargeStartedAt ?: updated.statusUpdatedAt ?: now
                savedChargerDao.updateStatus(
                    key = entity.key,
                    status = updated.status,
                    statusCode = updated.statusCode,
                    statusUpdatedAt = updated.statusUpdatedAt ?: entity.statusUpdatedAt,
                    stateSinceAt = stateSince,
                    lastFetchedAt = now
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /** 하위 호환 별칭. */
    suspend fun refreshSavedChargersStatus(): Result<Unit> = refreshTrackedChargersStatus()
}
