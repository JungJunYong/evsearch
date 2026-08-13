package com.evsearch.app.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat

object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocation || coarseLocation
    }

    fun getCurrentLocation(context: Context, onLocationReceived: (Location?) -> Unit) {
        if (!hasLocationPermission(context)) {
            onLocationReceived(null)
            return
        }

        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            var lastKnown: Location? = null

            if (isGpsEnabled) {
                lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }
            if (lastKnown == null && isNetworkEnabled) {
                lastKnown = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }
            if (lastKnown == null) {
                lastKnown = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            }

            if (lastKnown != null) {
                onLocationReceived(lastKnown)
            }

            // Request fresh single update if available
            val provider = when {
                isGpsEnabled -> LocationManager.GPS_PROVIDER
                isNetworkEnabled -> LocationManager.NETWORK_PROVIDER
                else -> LocationManager.PASSIVE_PROVIDER
            }

            locationManager.requestSingleUpdate(
                provider,
                object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        onLocationReceived(location)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                },
                null
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
            onLocationReceived(null)
        } catch (e: Exception) {
            e.printStackTrace()
            onLocationReceived(null)
        }
    }

    /**
     * Map GPS coordinates to matching region code ("11" = 서울, "41" = 경기, etc.)
     */
    fun getMatchingRegionCode(lat: Double, lng: Double): String {
        return when {
            lat in 37.40..37.70 && lng in 126.75..127.20 -> "11" // 서울
            lat in 36.90..38.30 && lng in 126.30..127.90 -> "41" // 경기
            lat in 37.30..37.60 && lng in 126.50..126.80 -> "28" // 인천
            lat in 35.00..35.35 && lng in 128.80..129.30 -> "26" // 부산
            lat in 35.70..36.05 && lng in 128.40..128.80 -> "27" // 대구
            lat in 36.20..36.50 && lng in 127.25..127.50 -> "30" // 대전
            lat in 35.05..35.25 && lng in 126.70..127.00 -> "29" // 광주
            lat in 35.40..35.70 && lng in 129.10..129.50 -> "31" // 울산
            lat in 33.10..33.60 && lng in 126.10..126.95 -> "50" // 제주
            else -> "11" // Default to 서울
        }
    }
}
