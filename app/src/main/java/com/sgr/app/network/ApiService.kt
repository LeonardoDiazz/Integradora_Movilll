package com.sgr.app.network

import com.sgr.app.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // AUTH
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body body: Map<String, String>): Response<MessageResponse>

    // DASHBOARD
    @GET("api/dashboard/stats")
    suspend fun getDashboardStats(): Response<DashboardStats>

    // USERS
    @GET("api/users")
    suspend fun getUsers(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("filter") filter: String = "",
        @Query("search") search: String = ""
    ): Response<PageResponse<User>>

    @POST("api/users")
    suspend fun createUser(@Body request: CreateUserRequest): Response<ResponseBody>

    @PUT("api/users/{id}")
    suspend fun updateUser(@Path("id") id: Long, @Body request: CreateUserRequest): Response<ResponseBody>

    @PATCH("api/users/{id}/toggle-status")
    suspend fun toggleUserStatus(@Path("id") id: Long): Response<ResponseBody>

    // SPACES
    @GET("api/spaces")
    suspend fun getSpaces(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("filter") filter: String = "",
        @Query("search") search: String = ""
    ): Response<PageResponse<Space>>

    @GET("api/spaces/{id}")
    suspend fun getSpace(@Path("id") id: Long): Response<Space>

    @PATCH("api/spaces/{id}/toggle-status")
    suspend fun toggleSpaceStatus(@Path("id") id: Long): Response<ResponseBody>

    // EQUIPMENT
    @GET("api/equipments")
    suspend fun getEquipments(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("filter") filter: String = "",
        @Query("search") search: String = ""
    ): Response<PageResponse<Equipment>>

    @GET("api/equipments/{id}")
    suspend fun getEquipment(@Path("id") id: Long): Response<Equipment>

    @PATCH("api/equipments/{id}/toggle-status")
    suspend fun toggleEquipmentStatus(@Path("id") id: Long): Response<ResponseBody>

    // RESERVATIONS (admin)
    @GET("api/reservations")
    suspend fun getReservations(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("filter") filter: String = ""
    ): Response<PageResponse<Reservation>>

    @GET("api/reservations/{id}")
    suspend fun getReservation(@Path("id") id: Long): Response<Reservation>

    @PATCH("api/reservations/{id}/approve")
    suspend fun approveReservation(@Path("id") id: Long, @Body body: ApproveRequest): Response<Reservation>

    @PATCH("api/reservations/{id}/reject")
    suspend fun rejectReservation(@Path("id") id: Long, @Body body: RejectRequest): Response<Reservation>

    @PATCH("api/reservations/{id}/return")
    suspend fun returnReservation(@Path("id") id: Long, @Body body: ReturnRequest): Response<Reservation>

    // RESERVATIONS (user)
    @GET("api/reservations/my")
    suspend fun getMyReservations(
        @Query("userId") userId: Long,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("filter") filter: String = ""
    ): Response<PageResponse<Reservation>>

    @POST("api/reservations")
    suspend fun createReservation(@Body request: CreateReservationRequest): Response<Reservation>

    @PATCH("api/reservations/my/{id}/cancel")
    suspend fun cancelMyReservation(
        @Path("id") id: Long,
        @Query("userId") userId: Long
    ): Response<Reservation>

    // PROFILE
    @PUT("api/users/profile/{userId}")
    suspend fun updateProfile(
        @Path("userId") userId: Long,
        @Body request: UpdateProfileRequest
    ): Response<ResponseBody>

    @PUT("api/users/profile/{userId}/change-password")
    suspend fun changePassword(
        @Path("userId") userId: Long,
        @Body request: ChangePasswordRequest
    ): Response<ResponseBody>

    // AUDIT
    @GET("api/reservations/audit")
    suspend fun getAudit(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("filter") filter: String = ""
    ): Response<PageResponse<Reservation>>
}
