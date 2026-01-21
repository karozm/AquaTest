package com.example.aquatest.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

// Prosty model logowania
data class LoginResponse(
        val user_id: String,
)

data class AuthRequest(val email: String, val password: String)

data class ProvisionData(val device_id: String)

data class ProvisionResponseData(
        val certificate_pem: String?,
        val private_key: String?,
        val message: String?,
)

data class ReadingPayload(val value: String?, val ts: Double?)

data class Reading(
        val payload: ReadingPayload?,
        val user_id: String?,
        val device_ts: String?,
        val data_type: String?
)

data class RetrieveResponse(val count: Int?, val readings: List<Reading>?)

data class RebindRequest(val device_id: String, val user_id: String, val unbind: Boolean? = null)

data class DeviceInfo(val id: String, val name: String)

data class GetDevicesResponse(val devices: List<DeviceInfo>?)

data class GetVersionResponse(
        val version: String?,
        val url: String?,
        val sha256: String?,
        val size: Int?,
        val update: Boolean?
)

data class SetUpdateRequest(val user_id: String, val device_id: String, val accept: Boolean)

data class SetNameRequest(val device_id: String, val name: String)

interface ApiService {
        @POST("user/register") suspend fun register(@Body request: AuthRequest): Response<Unit>

        @POST("user/login") suspend fun login(@Body request: AuthRequest): Response<LoginResponse>

        @POST("device/provision")
        suspend fun provision(@Body request: ProvisionData): Response<ProvisionResponseData>

        @POST("device/rebind") suspend fun rebind(@Body request: RebindRequest): Response<Unit>

        @GET("user/get_devices")
        suspend fun getDevices(@Query("user_id") userId: String): Response<GetDevicesResponse>

        @GET("device/get_version")
        suspend fun getVersion(@Query("device_id") deviceId: String): Response<GetVersionResponse>

        @POST("device/set_update")
        suspend fun setUpdate(@Body request: SetUpdateRequest): Response<Unit>

        @POST("device/set_name") suspend fun setName(@Body request: SetNameRequest): Response<Unit>

        @GET suspend fun downloadFile(@Url url: String): Response<String>
}
