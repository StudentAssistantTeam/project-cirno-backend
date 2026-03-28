package xyz.uthofficial.projectcirnobackend.dto

import jakarta.validation.constraints.Size
import java.util.UUID

/**
 * Request: POST /api/errorbook
 * At least one of description or imagePath must be non-null (enforced in service layer).
 */
data class CreateErrorBookRequest(
    @field:Size(max = 50000, message = "Description must be at most 50000 characters")
    val description: String? = null,

    val tags: List<String> = emptyList(),

    val date: String? = null,

    val eventId: UUID? = null
)

/**
 * Request: PATCH /api/errorbook/{id}
 * All fields are optional — only provided fields are updated.
 */
data class EditErrorBookRequest(
    @field:Size(max = 50000, message = "Description must be at most 50000 characters")
    val description: String? = null,

    val tags: List<String>? = null,

    val date: String? = null,

    val eventId: UUID? = null
)

/**
 * Response: single error book record.
 */
data class ErrorBookResponse(
    val id: UUID,
    val description: String?,
    val imagePath: String?,
    val date: String?,
    val eventId: UUID?,
    val tags: List<String>,
    val createdAt: String,
    val updatedAt: String
)

/**
 * Response: list of error book records.
 */
data class ErrorBookListResponse(
    val errors: List<ErrorBookResponse>
)
