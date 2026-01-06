package com.example.aquatest.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// Modele danych zgodne z formatem body Twojego API
data class AuthRequest(val email: String, val password: String)
data class ProvisionRequest(val user_id: String, val topic_id: String, val device_id: String)

interface ApiService {
    @POST("register")
    suspend fun register(@Body request: AuthRequest): Response<Unit>

    @POST("login")
    suspend fun login(@Body request: AuthRequest): Response<Unit>

    @POST("provision")
    suspend fun provision(@Body request: ProvisionRequest): Response<Unit>
}
