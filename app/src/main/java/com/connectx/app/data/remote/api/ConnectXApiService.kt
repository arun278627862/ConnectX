package com.connectx.app.data.remote.api

import retrofit2.Response
import retrofit2.http.*

data class LoginRequest(
    val email: String? = null,
    val password: String? = null,
    val phone: String? = null,
    val otp: String? = null,
    val googleIdToken: String? = null
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,
    val name: String,
    val phone: String? = null,
    val photoUrl: String? = null
)

// Separate DTO for user data from server (not a Room @Entity)
data class UserDto(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phoneNumber: String? = null,
    val avatarUrl: String? = null,
    val statusMessage: String = "",
    val isOnline: Boolean = false,
    val lastSeen: String = "Recently"
)

data class UpdateProfileRequest(
    val name: String,
    val phone: String,
    val statusMessage: String,
    val avatarUrl: String?
)

data class GenericResponse(
    val success: Boolean,
    val message: String
)

interface ConnectXApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/otp/send")
    suspend fun sendOtp(@Query("phone") phone: String): Response<GenericResponse>

    @POST("auth/otp/verify")
    suspend fun verifyOtp(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/google")
    suspend fun googleSignIn(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/refresh")
    suspend fun refreshToken(@Query("token") refreshToken: String): Response<AuthResponse>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Query("email") email: String): Response<GenericResponse>

    @PUT("user/profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<GenericResponse>

    // Returns UserDto list — NOT Room entities (avoids Gson crash on @Entity classes)
    @GET("users")
    suspend fun getUsers(): Response<List<UserDto>>
}
