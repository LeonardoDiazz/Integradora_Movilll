package com.sgr.app.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

data class LoginRequest(val email: String, val password: String)

data class LoginResponse(
    val token: String,
    val tokenType: String,
    val userId: Long,
    val name: String,
    val lastName: String,
    val email: String,
    val role: String,
    val phone: String? = null
)

// Deserializer que acepta tanto "YYYY-MM-DD" como [year, month, day] del backend
class LocalDateStringDeserializer : JsonDeserializer<String> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): String? {
        return when {
            json.isJsonNull -> null
            json.isJsonPrimitive -> json.asString
            json.isJsonArray -> {
                val arr = json.asJsonArray
                if (arr.size() == 3)
                    String.format("%04d-%02d-%02d", arr[0].asInt, arr[1].asInt, arr[2].asInt)
                else null
            }
            else -> null
        }
    }
}

data class User(
    val id: Long,
    val name: String,
    val lastName: String,
    val email: String,
    val phone: String?,
    val role: String?,
    val active: Boolean?,
    val identifier: String?,
    val userType: String?,
    @field:JsonAdapter(LocalDateStringDeserializer::class)
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

data class UpdateUserRequest(
    val name: String,
    val lastName: String,
    val email: String,
    val identifier: String?,
    val password: String?,
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
    val active: Boolean?,
    val availability: String?,
    val allowStudents: Boolean?,
    val description: String
)

data class Equipment(
    val id: Long,
    val inventoryNumber: String,
    val name: String,
    val category: String,
    val description: String,
    val allowStudents: Boolean?,
    val active: Boolean?,
    @SerializedName("condition")
    val equipmentCondition: String?,
    val createdAt: String?,
    val spaceId: Long? = null,
    val spaceName: String? = null
)

data class Reservation(
    val id: Long,
    val requesterId: Long? = null,
    val requesterName: String?,
    val requesterEmail: String?,
    val requesterType: String? = null,
    val resourceType: String,
    val resourceName: String? = null,
    val spaceId: Long? = null,
    val spaceName: String? = null,
    val equipmentId: Long? = null,
    val equipmentName: String? = null,
    val reservationDate: String?,
    val endDate: String?,
    val schedule: String? = null,
    val startTime: String?,
    val endTime: String?,
    val purpose: String?,
    val observations: String?,
    val status: String?,
    val adminComment: String?,
    val createdAt: String?,
    val returnCondition: String? = null,
    val returnDescription: String? = null,
    val returnedAt: String? = null
)

data class CreateReservationRequest(
    val requesterId: Long,
    val resourceType: String,
    val resourceId: Long,
    val reservationDate: String,
    val startTime: String,
    val endDate: String?,
    val endTime: String,
    val purpose: String,
    val observations: String?
)

data class ApproveRequest(val adminComment: String?)
data class RejectRequest(val adminComment: String)
data class ReturnRequest(val returnCondition: String, val returnDescription: String?)

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

data class UpdateEquipmentRequest(
    val inventoryNumber: String,
    val name: String,
    val category: String,
    val description: String,
    val allowStudents: Boolean,
    val condition: String,
    val active: Boolean,
    val spaceId: Long?
)

data class UpdateReservationRequest(
    val requesterId: Long,
    val resourceType: String,
    val resourceId: Long,
    val reservationDate: String,
    val startTime: String,
    val endDate: String?,
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
