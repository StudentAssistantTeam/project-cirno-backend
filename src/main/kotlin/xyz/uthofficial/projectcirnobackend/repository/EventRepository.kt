package xyz.uthofficial.projectcirnobackend.repository

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository
import xyz.uthofficial.projectcirnobackend.dto.CreateEventRequest
import xyz.uthofficial.projectcirnobackend.entity.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*

/** Plain DTO — safe to use outside Exposed transactions. */
data class EventRecord(
    val id: UUID,
    val name: String,
    val datetime: String,
    val description: String?,
    val tags: List<String>,
    val createdAt: String
)

@Repository
class EventRepository {

    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun createEvent(userId: UUID, request: CreateEventRequest): EventRecord = transaction {
        val user = User.findById(userId) ?: throw IllegalArgumentException("User not found")

        val parsedDatetime = try {
            LocalDateTime.parse(request.datetime, isoFormatter)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("Invalid datetime format. Expected ISO 8601 (e.g. 2026-04-01T14:00:00)")
        }

        // Deduplicate tag names, filter blanks, enforce 50-char limit
        val tagNames = request.tags
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(50)

        // Find or create tags
        val tagEntities = tagNames.map { name ->
            val existing = Tag.find { Tags.name eq name }.singleOrNull()
            existing ?: Tag.new { this.name = name }
        }

        // Create event (set timestamps manually since SQLite defaultExpression may not return values)
        val now = LocalDateTime.now()
        val event = Event.new {
            this.user = user.id
            this.name = request.name
            this.datetime = parsedDatetime
            this.description = request.description
            this.createdAt = now
            this.updatedAt = now
        }

        // Link tags via join table
        for (tag in tagEntities) {
            EventTags.insert {
                it[EventTags.event] = event.id
                it[EventTags.tag] = tag.id
            }
        }

        EventRecord(
            id = event.id.value,
            name = event.name,
            datetime = event.datetime.format(isoFormatter),
            description = event.description,
            tags = tagEntities.map { it.name },
            createdAt = event.createdAt.format(isoFormatter)
        )
    }
}
