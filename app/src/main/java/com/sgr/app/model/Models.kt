package com.sgr.app.model

data class LoginRequest(val email: String, val password: String)

data class LoginResponse(
    val token: String,
    val tokenType: String,
    val userId: Long,
    val name: String,
    val lastName: String,
    val email: String,
    val role: String
)

data class User(
    val id: Long,
    val name: String,
    val lastName: String,
    val email: String,
    val phone: String?,
    val role: String,
    val active: Boolean,
    val identifier: String?,
    val userType: String?,
    val birthDate: String?
)

data class CreateUserRequest(
    val name: String,
    val lastName: String,
    val email: String,
    val identifier: String,
    val password: String,
    val role: String,
    val active: Boolean,
    val userType: String?,
    val birthDate: String?,
    val phone: String?
)

data class Space(
    val id: Long,
    val name: String,
    val category: String,
    val location: String,
    val capacity: Int,
    val active: Boolean,
    val availability: String,
    val allowStudents: Boolean,
    val description: String
)

data class Equipment(
    val id: Long,
    val inventoryNumber: String,
    val name: String,
    val category: String,
    val description: String,
    val allowStudents: Boolean,
    val active: Boolean,
    val equipmentCondition: String,
    val createdAt: String?
)

data class Reservation(
    val id: Long,
    val requesterId: Long,
    val requesterName: String?,
    val requesterEmail: String?,
    val resourceType: String,
    val spaceId: Long?,
    val spaceName: String?,
    val equipmentId: Long?,
    val equipmentName: String?,
    val reservationDate: String,
    val startTime: String,
    val endTime: String,
    val purpose: String,
    val observations: String?,
    val status: String,
    val adminComment: String?,
    val createdAt: String?
)

data class CreateReservationRequest(
    val requesterId: Long,
    val resourceType: String,
    val spaceId: Long?,
    val equipmentId: Long?,
    val reservationDate: String,
    val startTime: String,
    val endTime: String,
    val purpose: String,
    val observations: String?
)

data class ApproveRequest(val adminComment: String?)
data class RejectRequest(val adminComment: String)
data class ReturnRequest(val condition: String, val description: String?)

data class PageResponse<T>(
    val content: List<T>,
    val totalElements: Long,
    val totalPages: Int,
    val number: Int,
    val size: Int
)

data class DashboardStats(
    val activeUsers: Int,
    val totalUsers: Int,
    val totalSpaces: Int,
    val totalEquipments: Int,
    val pendingReservations: Int,
    val totalReservations: Int,
    val approvedReservations: Int,
    val rejectedReservations: Int,
    val pendingRequests: Int,
    val totalRequests: Int
)

data class MessageResponse(val message: String)

data class UpdateProfileRequest(val name: String, val lastName: String, val phone: String?)
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String, val confirmPassword: String)

data class CreateSpaceRequest(
    val name: String,
    val category: String,
    val location: String,
    val capacity: Int,
    val description: String,
    val allowStudents: Boolean,
    val availability: String,
    val active: Boolean
)

data class CreateEquipmentRequest(
    val inventoryNumber: String,
    val name: String,
    val category: String,
    val description: String,
    val allowStudents: Boolean,
    val equipmentCondition: String,
    val active: Boolean
)

data class UpdateReservationRequest(
    val requesterId: Long,
    val resourceType: String,
    val spaceId: Long?,
    val equipmentId: Long?,
    val reservationDate: String,
    val startTime: String,
    val endTime: String,
    val purpose: String,
    val observations: String?
)

data class HistoryItem(
    val id: Long?,
    val action: String?,
    val changedBy: String?,
    val changedAt: String?,
    val details: String?
)
