package xyz.uthofficial.projectcirnobackend.controller

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
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
import java.util.UUID

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

    /**
     * PUT /api/events/{id}
     * Updates an event's name, datetime, description, and tags.
     */
    @PutMapping("/{id}")
    fun updateEvent(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateEventRequest,
        principal: Principal
    ): ResponseEntity<Any> {
        val user = userRepository.findByUsername(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "User not found"))

        try {
            val record = eventRepository.updateEvent(user.id.value, id, request)
            return ResponseEntity.ok(
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

    /**
     * DELETE /api/events/{id}
     * Deletes an event and its tag associations.
     */
    @DeleteMapping("/{id}")
    fun deleteEvent(
        @PathVariable id: UUID,
        principal: Principal
    ): ResponseEntity<Any> {
        val user = userRepository.findByUsername(principal.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "User not found"))

        try {
            eventRepository.deleteEvent(user.id.value, id)
            return ResponseEntity.ok(mapOf("message" to "Event deleted"))
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }
}
