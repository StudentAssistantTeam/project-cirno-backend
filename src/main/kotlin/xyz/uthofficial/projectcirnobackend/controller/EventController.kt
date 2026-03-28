package xyz.uthofficial.projectcirnobackend.controller

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import xyz.uthofficial.projectcirnobackend.dto.CreateEventRequest
import xyz.uthofficial.projectcirnobackend.dto.EventResponse
import xyz.uthofficial.projectcirnobackend.dto.EventsResponse
import xyz.uthofficial.projectcirnobackend.repository.EventRepository
import xyz.uthofficial.projectcirnobackend.repository.UserRepository
import java.security.Principal

@RestController
@RequestMapping("/api/events")
@Validated
class EventController(
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository
) {

    @PostMapping
    fun createEvent(
        @Valid @RequestBody request: CreateEventRequest,
        principal: Principal
    ): ResponseEntity<Any> {
        val user = userRepository.findByUsername(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "User not found"))

        try {
            eventRepository.createEvent(user.id.value, request)
            return ResponseEntity.status(HttpStatus.CREATED).body(request)
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    /**
     * GET /api/events?start=2026-01-01T00:00:00&length=3
     * Returns events for the authenticated user within [start, start + length months).
     */
    @GetMapping
    fun getEvents(
        @RequestParam start: String,
        @RequestParam length: Int,
        principal: Principal
    ): ResponseEntity<Any> {
        val user = userRepository.findByUsername(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "User not found"))

        try {
            val records = eventRepository.getEventsByTimeRange(user.id.value, start, length)
            val events = records.map { r ->
                EventResponse(
                    id = r.id,
                    name = r.name,
                    datetime = r.datetime,
                    description = r.description,
                    tags = r.tags,
                    createdAt = r.createdAt
                )
            }
            return ResponseEntity.ok(EventsResponse(events))
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }
}
