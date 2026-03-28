package xyz.uthofficial.projectcirnobackend.repository

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
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

        val tagNames = request.tags
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(50)

        val tagEntities = tagNames.map { name ->
            val existing = Tag.find { Tags.name eq name }.singleOrNull()
            existing ?: Tag.new { this.name = name }
        }

        val now = LocalDateTime.now()
        val event = Event.new {
            this.user = user.id
            this.name = request.name
            this.datetime = parsedDatetime
            this.description = request.description
            this.createdAt = now
            this.updatedAt = now
        }

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

    /**
     * Returns all events for [userId] whose datetime falls within [start, start + length months).
     */
    fun getEventsByTimeRange(userId: UUID, start: String, lengthMonths: Int): List<EventRecord> = transaction {
        val startLdt = try {
            LocalDateTime.parse(start, isoFormatter)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("Invalid start datetime format. Expected ISO 8601 (e.g. 2026-04-01T00:00:00)")
        }

        val endLdt = startLdt.plusMonths(lengthMonths.toLong())

        val query = Events.selectAll()
            .andWhere { Events.user eq userId }
            .andWhere { Events.datetime greaterEq startLdt }
            .andWhere { Events.datetime less endLdt }
            .orderBy(Events.datetime to SortOrder.ASC)

        query.map { row: ResultRow ->
            val event = Event.wrapRow(row)
            val eventId = event.id.value

            val tagRows = (EventTags innerJoin Tags)
                .selectAll()
                .where { EventTags.event eq event.id }
                .map { row2: ResultRow -> row2[Tags.name] }

            EventRecord(
                id = eventId,
                name = event.name,
                datetime = event.datetime.format(isoFormatter),
                description = event.description,
                tags = tagRows,
                createdAt = event.createdAt.format(isoFormatter)
            )
        }
    }
}
