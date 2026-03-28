package xyz.uthofficial.projectcirnobackend.controller

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import xyz.uthofficial.projectcirnobackend.dto.LoginRequest
import xyz.uthofficial.projectcirnobackend.dto.LoginResponse
import xyz.uthofficial.projectcirnobackend.dto.SignupRequest
import xyz.uthofficial.projectcirnobackend.dto.SignupResponse
import xyz.uthofficial.projectcirnobackend.repository.UserRepository
import xyz.uthofficial.projectcirnobackend.security.JwtUtil

/**
 * REST controller for authentication endpoints.
 * Exposes: POST /api/auth/signup and POST /api/auth/login.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authenticationManager: AuthenticationManager,
    private val jwtUtil: JwtUtil
) {

    /**
     * POST /api/auth/signup
     * Request:  { "username": "...", "email": "...", "password": "..." }
     * Response: 201 { "id": "...", "username": "...", "email": "...", "token": "..." }
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
        val record = userRepository.createUser(request.username, request.email, hashedPassword)
        val token = jwtUtil.generateToken(record.username)

        return ResponseEntity.status(HttpStatus.CREATED).body(
            SignupResponse(
                id = record.id,
                username = record.username,
                email = record.email,
                token = token
            )
        )
    }

    /**
     * POST /api/auth/login
     * Request:  { "usernameOrEmail": "...", "password": "..." }
     * Response: 200 { "token": "...", "username": "...", "email": "..." }
     * Errors:   401 { "error": "..." } for invalid credentials.
     */
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<Any> {
        val lookupUsername = if (request.usernameOrEmail.contains("@")) {
            userRepository.findByEmail(request.usernameOrEmail)?.username
        } else {
            request.usernameOrEmail
        }

        if (lookupUsername == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "Invalid username/email or password"))
        }

        try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(lookupUsername, request.password)
            )
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "Invalid username/email or password"))
        }

        val token = jwtUtil.generateToken(lookupUsername)
        val user = userRepository.findByUsername(lookupUsername)!!

        return ResponseEntity.ok(
            LoginResponse(
                token = token,
                username = user.username,
                email = user.email
            )
        )
    }
}
