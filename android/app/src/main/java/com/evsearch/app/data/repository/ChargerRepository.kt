package com.evsearch.app.data.repository

import android.content.Context
import com.evsearch.app.data.api.BffApiService
import com.evsearch.app.data.local.SavedChargerDao
import com.evsearch.app.data.local.SavedChargerEntity
import com.evsearch.app.data.model.BatchStatusKey
import com.evsearch.app.data.model.BatchStatusRequest
import com.evsearch.app.data.model.Charger
import com.evsearch.app.data.model.ChargerStation
import com.evsearch.app.widget.ChargerWidget
import androidx.glance.appwidget.updateAll
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChargerRepository(
    private val apiService: BffApiService,
    private val savedChargerDao: SavedChargerDao,
    private val context: Context? = null
) {

    private var cachedAssetStations: List<ChargerStation>? = null

    private fun loadStationsFromAssets(): List<ChargerStation> {
        if (cachedAssetStations != null) return cachedAssetStations!!
        return try {
            if (context != null) {
                val jsonString = context.assets.open("mockStations.json").bufferedReader().use { it.readText() }
                val root = com.google.gson.JsonParser.parseString(jsonString).asJsonObject

                // { success, count, data: [...] } 형태이므로 data 배열 안의 내용을 파싱
                val dataElement = root.getAsJsonArray("data")
                val type = object : TypeToken<List<ChargerStation>>() {}.type
                val list: List<ChargerStation> = Gson().fromJson(dataElement, type)

                cachedAssetStations = list
                list
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getSavedChargersFlow(): Flow<List<SavedChargerEntity>> = savedChargerDao.getAllSavedChargersFlow()

    suspend fun getStations(zcode: String? = "11", zscode: String? = null): Result<List<ChargerStation>> {
        // First attempt online API call
        try {
            val response = apiService.getStations(zcode = zcode, zscode = zscode)
            if (response.success && response.data.isNotEmpty()) {
                return Result.success(response.data)
            }
        } catch (e: Exception) {
            // Fallback to local embedded asset dataset
        }

        // Seamless Offline Fallback
        val assetList = loadStationsFromAssets()
        val filtered = if (zcode.isNullOrBlank()) {
            assetList
        } else {
            assetList.filter { it.zcode == zcode }
        }

        return if (filtered.isNotEmpty()) {
            Result.success(filtered)
        } else if (assetList.isNotEmpty()) {
            Result.success(assetList)
        } else {
            Result.failure(Exception("No station data available offline"))
        }
    }

    suspend fun getStationDetail(statId: String): Result<ChargerStation> {
        try {
            val response = apiService.getStationDetail(statId)
            if (response.success) {
                return Result.success(response.data)
            }
        } catch (e: Exception) {
            // Fallback to local asset
        }

        val assetList = loadStationsFromAssets()
        val station = assetList.find { it.statId == statId }
        return if (station != null) {
            Result.success(station)
        } else {
            Result.failure(Exception("Station not found offline"))
        }
    }

    suspend fun saveChargerToWidget(station: ChargerStation, charger: Charger) {
        val key = "${station.statId}:${charger.chgerId}"
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
        val entity = SavedChargerEntity(
            key = key,
            statId = station.statId,
            chgerId = charger.chgerId,
            stationName = station.name,
            chargerTypeName = charger.typeName,
            outputKw = charger.outputKw,
            status = charger.status,
            statusCode = charger.statusCode,
            statusUpdatedAt = charger.statusUpdatedAt,
            lastFetchedAt = now
        )
        savedChargerDao.insertOrUpdate(entity)

        if (context != null) {
            ChargerWidget().updateAll(context)
        }
    }

    suspend fun removeChargerFromWidget(key: String) {
        savedChargerDao.deleteByKey(key)

        if (context != null) {
            ChargerWidget().updateAll(context)
        }
    }

    suspend fun updateChargerCustomName(key: String, customName: String?) {
        savedChargerDao.updateCustomName(key, customName?.takeIf { it.isNotBlank() })

        if (context != null) {
            ChargerWidget().updateAll(context)
        }
    }

    suspend fun refreshSavedChargersStatus(): Result<Unit> {
        val saved = savedChargerDao.getAllSavedChargers()
        if (saved.isEmpty()) return Result.success(Unit)

        val keys = saved.map { BatchStatusKey(it.statId, it.chgerId) }
        try {
            val response = apiService.getBatchStatus(BatchStatusRequest(keys))
            if (response.success) {
                val resultsMap = response.data
                val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())

                for (entity in saved) {
                    val updated = resultsMap[entity.key]
                    if (updated != null) {
                        savedChargerDao.insertOrUpdate(
                            entity.copy(
                                status = updated.status,
                                statusCode = updated.statusCode,
                                statusUpdatedAt = updated.statusUpdatedAt ?: entity.statusUpdatedAt,
                                lastFetchedAt = now
                            )
                        )
                    }
                }
                return Result.success(Unit)
            }
        } catch (e: Exception) {
            // Fallback to local offline refresh
        }

        // Offline Fallback for Saved Widget Chargers
        val assetList = loadStationsFromAssets()
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
        for (entity in saved) {
            val station = assetList.find { it.statId == entity.statId }
            if (station != null) {
                val chg = station.chargers.find { it.chgerId == entity.chgerId } ?: station.chargers.firstOrNull()
                if (chg != null) {
                    savedChargerDao.insertOrUpdate(
                        entity.copy(
                            status = chg.status,
                            statusCode = chg.statusCode,
                            statusUpdatedAt = chg.statusUpdatedAt ?: entity.statusUpdatedAt,
                            lastFetchedAt = now
                        )
                    )
                }
            }
        }

        if (context != null) {
            ChargerWidget().updateAll(context)
        }

        return Result.success(Unit)
    }
}
