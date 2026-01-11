package com.example.aquatest.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// Prosty model logowania (tak jak działało wcześniej)
data class LoginResponse(
    val user_id: String,
    val topic_id: String
)

data class AuthRequest(val email: String, val password: String)

data class ProvisionData(
    val user_id: String,
    val topic_id: String,
    val device_id: String
)

// Płaski model odpowiedzi z Provisioningu (analogicznie do Loginu)
data class ProvisionResponseData(
    val thing_name: String?,
    val certificate_id: String?,
    val certificate_pem: String?,
    val private_key: String?,
    val root_ca_url: String?,
    val message: String?
)

interface ApiService {
    @POST("register")
    suspend fun register(@Body request: AuthRequest): Response<Unit>

    @POST("login")
    suspend fun login(@Body request: AuthRequest): Response<LoginResponse>

    @POST("provision")
    suspend fun provision(@Body request: ProvisionData): Response<ProvisionResponseData>
}
