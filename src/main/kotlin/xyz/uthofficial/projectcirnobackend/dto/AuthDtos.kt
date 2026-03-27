package xyz.uthofficial.projectcirnobackend.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

/**
 * Request: POST /api/auth/signup
 * Validations: username (3-50), email (valid format), password (8-100).
 */
data class SignupRequest(
    @field:NotBlank(message = "Username is required")
    @field:Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    val username: String,

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    val password: String
)

/**
 * Response: returned on successful signup.
 * Contains id, username, email. Password is intentionally excluded.
 */
data class SignupResponse(
    val id: UUID,
    val username: String,
    val email: String
)
