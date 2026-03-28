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
 * Contains id, username, email, and JWT token. Password is intentionally excluded.
 */
data class SignupResponse(
    val id: UUID,
    val username: String,
    val email: String,
    val token: String
)

/**
 * Request: POST /api/auth/login
 * Validations: username/email and password are required.
 */
data class LoginRequest(
    @field:NotBlank(message = "Username or email is required")
    val usernameOrEmail: String,

    @field:NotBlank(message = "Password is required")
    val password: String
)

/**
 * Response: returned on successful login.
 * Contains the JWT token and user info. Password is intentionally excluded.
 */
data class LoginResponse(
    val token: String,
    val username: String,
    val email: String
)
