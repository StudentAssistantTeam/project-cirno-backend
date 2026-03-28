package xyz.uthofficial.projectcirnobackend.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Request: POST /api/user/identity
 * Both identity and goal are required.
 */
data class UserIdentityRequest(
    @field:NotBlank(message = "Identity is required")
    @field:Size(max = 255, message = "Identity must be at most 255 characters")
    val identity: String,

    @field:NotBlank(message = "Goal is required")
    @field:Size(max = 255, message = "Goal must be at most 255 characters")
    val goal: String
)

/**
 * Request: PATCH /api/user/identity
 * Both fields are optional — only provided fields are updated.
 */
data class UserIdentityEditRequest(
    @field:Size(max = 255, message = "Identity must be at most 255 characters")
    val identity: String? = null,

    @field:Size(max = 255, message = "Goal must be at most 255 characters")
    val goal: String? = null
)

/**
 * Response: returned on GET /api/user/identity.
 * Contains the user's identity and goal.
 */
data class UserIdentityResponse(
    val identity: String,
    val goal: String
)
