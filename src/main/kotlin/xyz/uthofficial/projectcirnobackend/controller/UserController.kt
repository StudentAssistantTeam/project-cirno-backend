package xyz.uthofficial.projectcirnobackend.controller

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import xyz.uthofficial.projectcirnobackend.dto.UserIdentityEditRequest
import xyz.uthofficial.projectcirnobackend.dto.UserIdentityRequest
import xyz.uthofficial.projectcirnobackend.dto.UserIdentityResponse
import xyz.uthofficial.projectcirnobackend.repository.UserRepository
import xyz.uthofficial.projectcirnobackend.service.UserIdentityService
import java.security.Principal

@RestController
@RequestMapping("/api")
class UserController(
    private val userRepository: UserRepository,
    private val userIdentityService: UserIdentityService
) {

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<Any> {
        val user = userRepository.findByUsername(principal.username)
            ?: return ResponseEntity.status(401).body(mapOf("error" to "User not found"))

        return ResponseEntity.ok(
            mapOf(
                "id" to user.id.value.toString(),
                "username" to user.username,
                "email" to user.email
            )
        )
    }

    @GetMapping("/user/identity")
    fun getIdentity(principal: Principal): ResponseEntity<UserIdentityResponse> {
        val user = userRepository.findByUsername(principal.name) ?: return ResponseEntity.status(401).build()
        val identity = userIdentityService.getByIdentity(user.id.value)
            ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(identity)
    }

    @GetMapping("/user/identity/goal")
    fun getGoal(principal: Principal): ResponseEntity<Any> {
        val user = userRepository.findByUsername(principal.name) ?: return ResponseEntity.status(401).build()
        val identity = userIdentityService.getByIdentity(user.id.value)
            ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(mapOf("goal" to identity.goal))
    }

    @PostMapping("/user/identity")
    fun createIdentity(@Valid @RequestBody request: UserIdentityRequest, principal: Principal): ResponseEntity<Any> {
        val user = userRepository.findByUsername(principal.name) ?: return ResponseEntity.status(401).build()
        if (userIdentityService.getByIdentity(user.id.value) != null) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Identity already exists, use PATCH to update"))
        }
        val result = userIdentityService.create(user.id.value, request.identity, request.goal)
        return ResponseEntity.status(201).body(result)
    }

    @PatchMapping("/user/identity")
    fun editIdentity(@Valid @RequestBody request: UserIdentityEditRequest, principal: Principal): ResponseEntity<Any> {
        val user = userRepository.findByUsername(principal.name) ?: return ResponseEntity.status(401).build()
        if (request.identity == null && request.goal == null) {
            return ResponseEntity.badRequest().body(mapOf("error" to "At least one field must be provided"))
        }
        try {
            val result = userIdentityService.update(user.id.value, request.identity, request.goal)
            return ResponseEntity.ok(result)
        } catch (e: NoSuchElementException) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Identity not found, use POST to create"))
        }
    }
}
