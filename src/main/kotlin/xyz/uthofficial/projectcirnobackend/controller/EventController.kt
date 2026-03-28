package xyz.uthofficial.projectcirnobackend.controller

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import xyz.uthofficial.projectcirnobackend.dto.CreateEventRequest
import xyz.uthofficial.projectcirnobackend.dto.EventResponse
import xyz.uthofficial.projectcirnobackend.repository.EventRepository
import xyz.uthofficial.projectcirnobackend.repository.UserRepository
import java.security.Principal

/**
 * REST controller for event endpoints.
 * All endpoints require JWT authentication (enforced by SecurityConfig).
 */
@RestController
@RequestMapping("/api/events")
class EventController(
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository
) {

    /**
     * POST /api/events
     * Creates a new event for the authenticated user.
     *
     * Request: { "name": "...", "datetime": "2026-04-01T14:00:00", "description": "...", "tags": [...] }
     * Response: 201 { "id": "...", "name": "...", "datetime": "...", "description": "...", "tags": [...], "createdAt": "..." }
     */
    @PostMapping
    fun createEvent(
        @Valid @RequestBody request: CreateEventRequest,
        principal: Principal
    ): ResponseEntity<Any> {
        val user = userRepository.findByUsername(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "User not found"))

        try {
            val record = eventRepository.createEvent(user.id.value, request)

            return ResponseEntity.status(HttpStatus.CREATED).body(
                EventResponse(
                    id = record.id,
                    name = record.name,
                    datetime = record.datetime,
                    description = record.description,
                    tags = record.tags,
                    createdAt = record.createdAt
                )
            )
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }
}
