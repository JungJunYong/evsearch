package com.evsearch.app.data.api

import com.evsearch.app.data.model.BffBatchStatusResponse
import com.evsearch.app.data.model.BffStationDetailResponse
import com.evsearch.app.data.model.BffStationsResponse
import com.evsearch.app.data.model.BatchStatusRequest
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
        @Query("zcode") zcode: String? = "11",
        @Query("zscode") zscode: String? = null,
        @Query("page") page: Int = 1,
        @Query("numOfRows") numOfRows: Int = 50
    ): BffStationsResponse

    @GET("v1/stations/{statId}")
    suspend fun getStationDetail(
        @Path("statId") statId: String
    ): BffStationDetailResponse

    @POST("v1/stations/batch-status")
    suspend fun getBatchStatus(
        @Body request: BatchStatusRequest
    ): BffBatchStatusResponse

    companion object {
        // Use 127.0.0.1:4000 (ADB reverse forwarded over USB)
        private const val BASE_URL = "http://127.0.0.1:4000/"

        fun create(baseUrl: String = BASE_URL): BffApiService {
            val logger = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
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
