package xyz.uthofficial.projectcirnobackend.service

import org.springframework.stereotype.Service
import xyz.uthofficial.projectcirnobackend.dto.ErrorBookResponse
import xyz.uthofficial.projectcirnobackend.repository.ErrorBookRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

@Service
class ErrorBookService(
    private val errorBookRepository: ErrorBookRepository
) {

    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    private fun parseTags(tags: List<String>): List<String> = tags
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .take(50)

    private fun parseDate(date: String?): LocalDateTime? {
        if (date == null) return null
        return try {
            LocalDateTime.parse(date, isoFormatter)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("Invalid date format. Expected ISO 8601 (e.g. 2026-03-29T14:00:00)")
        }
    }

    private fun toResponse(record: xyz.uthofficial.projectcirnobackend.repository.ErrorBookRecord): ErrorBookResponse =
        ErrorBookResponse(
            id = record.id,
            description = record.description,
            imagePath = record.imagePath,
            date = record.date,
            eventId = record.eventId,
            tags = record.tags,
            createdAt = record.createdAt,
            updatedAt = record.updatedAt
        )

    fun create(
        userId: UUID,
        description: String?,
        imagePath: String?,
        tags: List<String>,
        date: String?,
        eventId: UUID?
    ): ErrorBookResponse {
        if (description.isNullOrBlank() && imagePath.isNullOrBlank()) {
            throw IllegalArgumentException("At least one of description or image must be provided")
        }
        val parsedDate = parseDate(date)
        val cleanTags = parseTags(tags)
        val record = errorBookRepository.create(
            userId = userId,
            description = description?.takeIf { it.isNotBlank() },
            imagePath = imagePath,
            date = parsedDate,
            eventId = eventId,
            tagNames = cleanTags
        )
        return toResponse(record)
    }

    fun findById(userId: UUID, errorId: UUID): ErrorBookResponse? {
        val record = errorBookRepository.findById(userId, errorId) ?: return null
        return toResponse(record)
    }

    fun findAll(userId: UUID, tag: String?, dateFrom: String?, dateTo: String?): List<ErrorBookResponse> {
        val from = parseDate(dateFrom)
        val to = parseDate(dateTo)
        return errorBookRepository.findAll(userId, tag, from, to).map { toResponse(it) }
    }

    fun update(
        userId: UUID,
        errorId: UUID,
        description: String?,
        tags: List<String>?,
        date: String?,
        eventId: UUID?
    ): ErrorBookResponse {
        val parsedDate = parseDate(date)
        val cleanTags = tags?.let { parseTags(it) }
        val record = errorBookRepository.update(
            userId = userId,
            errorId = errorId,
            description = description,
            imagePath = null,
            date = parsedDate,
            eventId = eventId,
            tagNames = cleanTags
        )
        return toResponse(record)
    }

    fun updateImagePath(userId: UUID, errorId: UUID, imagePath: String): String? {
        return errorBookRepository.updateImagePath(userId, errorId, imagePath)
    }

    fun delete(userId: UUID, errorId: UUID): String? {
        return errorBookRepository.delete(userId, errorId)
    }

    fun getRecentErrorsSummary(userId: UUID, limit: Int = 10): String {
        val errors = errorBookRepository.findAll(userId, null, null, null)
            .sortedByDescending { it.createdAt }
            .take(limit)
        if (errors.isEmpty()) return "No error records."
        return errors.joinToString("\n") { e ->
            val tags = if (e.tags.isNotEmpty()) " [${e.tags.joinToString(", ")}]" else ""
            val date = e.date ?: "no date"
            val desc = e.description?.take(80) ?: "(image only)"
            "- $desc ($date)$tags (ID: ${e.id})"
        }
    }
}
