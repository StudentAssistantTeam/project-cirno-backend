package xyz.uthofficial.projectcirnobackend.controller

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import xyz.uthofficial.projectcirnobackend.dto.SignupRequest
import xyz.uthofficial.projectcirnobackend.dto.SignupResponse
import xyz.uthofficial.projectcirnobackend.repository.UserRepository

/**
 * REST controller for authentication endpoints.
 * Currently, exposes: POST /api/auth/signup.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {

    /**
     * POST /api/auth/signup
     * Request:  { "username": "...", "email": "...", "password": "..." }
     * Response: 201 { "id": "...", "username": "...", "email": "..." }
     * Errors:   400 { "error": "..." } for duplicates or validation failures.
     */
    @PostMapping("/signup")
    fun signup(@Valid @RequestBody request: SignupRequest): ResponseEntity<Any> {
        if (userRepository.existsByUsername(request.username)) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Username already taken"))
        }
        if (userRepository.existsByEmail(request.email)) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Email already in use"))
        }

        val hashedPassword = passwordEncoder.encode(request.password)!!
        val user = userRepository.createUser(request.username, request.email, hashedPassword)

        return ResponseEntity.status(HttpStatus.CREATED).body(
            SignupResponse(
                id = user.id.value,
                username = user.username,
                email = user.email
            )
        )
    }
}
