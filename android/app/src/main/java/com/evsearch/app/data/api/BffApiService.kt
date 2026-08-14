package com.evsearch.app.data.api

import com.evsearch.app.data.model.BffBatchStatusResponse
import com.evsearch.app.data.model.BffStationDetailResponse
import com.evsearch.app.data.model.BffStationsResponse
import com.evsearch.app.data.model.BatchStatusRequest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface BffApiService {

    @GET("v1/stations")
    suspend fun getStations(
        @Query("zcode") zcode: String? = null,
        @Query("zscode") zscode: String? = null,
        @Query("page") page: Int = 1,
        @Query("numOfRows") numOfRows: Int = 3000
    ): BffStationsResponse

    @GET("v1/stations/{statId}")
    suspend fun getStationDetail(
        @Path("statId") statId: String
    ): BffStationDetailResponse

    @GET("v1/stations/chargev/search")
    suspend fun searchChargevStations(
        @Query("keyword") keyword: String
    ): BffStationsResponse

    @GET("v1/stations/chargev/charger/{cNum}")
    suspend fun getChargevByChargerNumber(
        @Path("cNum") cNum: String
    ): BffStationDetailResponse

    @POST("v1/stations/batch-status")
    suspend fun getBatchStatus(
        @Body request: BatchStatusRequest
    ): BffBatchStatusResponse

    companion object {
        // Production Live API Server (evsearch.wiqio.com)
        private const val BASE_URL = "https://evsearch.wiqio.com/"
        private const val API_KEY_HEADER_NAME = "X-API-Key"
        private const val API_KEY_HEADER_VALUE = "evsearch-sec-2026-v1-key"

        fun create(baseUrl: String = BASE_URL): BffApiService {
            val logger = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val authInterceptor = Interceptor { chain ->
                val originalRequest = chain.request()
                val authenticatedRequest = originalRequest.newBuilder()
                    .addHeader(API_KEY_HEADER_NAME, API_KEY_HEADER_VALUE)
                    .build()
                chain.proceed(authenticatedRequest)
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(logger)
                .build()

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BffApiService::class.java)
        }
    }
}
