package xyz.uthofficial.projectcirnobackend.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import xyz.uthofficial.projectcirnobackend.validation.ValidTagList
import java.util.UUID

/**
 * Request: POST /api/events
 * Validations: name (max 255), datetime (ISO 8601 string), description (optional),
 * tags (list of tag name strings, each max 50 chars).
 */
data class CreateEventRequest(
    @field:NotBlank(message = "Event name is required")
    @field:Size(max = 255, message = "Event name must be at most 255 characters")
    val name: String,

    @field:NotBlank(message = "Datetime is required")
    val datetime: String,

    val description: String?,

    @field:ValidTagList
    val tags: List<String> = emptyList()
)

/**
 * Response: single event returned in GET /api/events results.
 */
data class EventResponse(
    val id: UUID,
    val name: String,
    val datetime: String,
    val description: String?,
    val tags: List<String>,
    val createdAt: String
)

/**
 * Response: GET /api/events
 */
data class EventsResponse(
    val events: List<EventResponse>
)
