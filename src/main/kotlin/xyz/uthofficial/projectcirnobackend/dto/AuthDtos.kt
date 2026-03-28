package xyz.uthofficial.projectcirnobackend.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.util.UUID
import xyz.uthofficial.projectcirnobackend.validation.ValidPassword
import xyz.uthofficial.projectcirnobackend.validation.ValidUsername

/**
 * Request: POST /api/auth/signup
 * Validations: username (3-50, alphanumeric/dash/underscore), email (valid format), password (8-64, letter+digit, ASCII special only).
 */
data class SignupRequest(
    @field:ValidUsername
    val username: String,

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    val email: String,

    @field:ValidPassword
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
