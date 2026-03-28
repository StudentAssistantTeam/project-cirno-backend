package xyz.uthofficial.projectcirnobackend.controller

import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import xyz.uthofficial.projectcirnobackend.repository.UserRepository

@RestController
@RequestMapping("/api")
class UserController(
    private val userRepository: UserRepository
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
}
