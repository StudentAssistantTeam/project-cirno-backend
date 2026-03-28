package xyz.uthofficial.projectcirnobackend.repository

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository
import xyz.uthofficial.projectcirnobackend.entity.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class ErrorBookRecord(
    val id: UUID,
    val description: String?,
    val imagePath: String?,
    val date: String?,
    val eventId: UUID?,
    val tags: List<String>,
    val createdAt: String,
    val updatedAt: String
)

@Repository
class ErrorBookRepository {

    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    private fun resolveTags(tagNames: List<String>): List<Tag> = transaction {
        tagNames.map { name ->
            Tag.find { Tags.name eq name }.singleOrNull()
                ?: Tag.new { this.name = name }
        }
    }

    private fun loadTagsForError(errorId: UUID): List<String> = transaction {
        (ErrorTags innerJoin Tags)
            .selectAll()
            .where { ErrorTags.error eq errorId }
            .map { row: ResultRow -> row[Tags.name] }
    }

    fun create(
        userId: UUID,
        description: String?,
        imagePath: String?,
        date: LocalDateTime?,
        eventId: UUID?,
        tagNames: List<String>
    ): ErrorBookRecord = transaction {
        val tagEntities = resolveTags(tagNames)
        val now = LocalDateTime.now()

        val errorBook = ErrorBook.new {
            this.user = EntityID(userId, Users)
            this.description = description
            this.imagePath = imagePath
            this.date = date
            this.eventId = eventId?.let { EntityID(it, Events) }
            this.createdAt = now
            this.updatedAt = now
        }

        for (tag in tagEntities) {
            ErrorTags.insert {
                it[ErrorTags.error] = errorBook.id
                it[ErrorTags.tag] = tag.id
            }
        }

        ErrorBookRecord(
            id = errorBook.id.value,
            description = errorBook.description,
            imagePath = errorBook.imagePath,
            date = errorBook.date?.format(isoFormatter),
            eventId = errorBook.eventId?.value,
            tags = tagEntities.map { it.name },
            createdAt = errorBook.createdAt.format(isoFormatter),
            updatedAt = errorBook.updatedAt.format(isoFormatter)
        )
    }

    fun findById(userId: UUID, errorId: UUID): ErrorBookRecord? = transaction {
        val row = ErrorBooks.selectAll()
            .andWhere { ErrorBooks.id eq errorId }
            .andWhere { ErrorBooks.user eq userId }
            .singleOrNull()
            ?: return@transaction null

        val errorBook = ErrorBook.wrapRow(row)
        val tagNames = loadTagsForError(errorId)

        ErrorBookRecord(
            id = errorBook.id.value,
            description = errorBook.description,
            imagePath = errorBook.imagePath,
            date = errorBook.date?.format(isoFormatter),
            eventId = errorBook.eventId?.value,
            tags = tagNames,
            createdAt = errorBook.createdAt.format(isoFormatter),
            updatedAt = errorBook.updatedAt.format(isoFormatter)
        )
    }

    fun findAll(userId: UUID, tag: String?, dateFrom: LocalDateTime?, dateTo: LocalDateTime?): List<ErrorBookRecord> =
        transaction {
            val query = ErrorBooks.selectAll()
                .andWhere { ErrorBooks.user eq userId }

            if (dateFrom != null) {
                query.andWhere { ErrorBooks.date greaterEq dateFrom }
            }
            if (dateTo != null) {
                query.andWhere { ErrorBooks.date less dateTo }
            }

            val rows = query.toList()

            rows.map { row ->
                val errorBook = ErrorBook.wrapRow(row)
                val errorId = errorBook.id.value
                val tagNames = loadTagsForError(errorId)

                ErrorBookRecord(
                    id = errorId,
                    description = errorBook.description,
                    imagePath = errorBook.imagePath,
                    date = errorBook.date?.format(isoFormatter),
                    eventId = errorBook.eventId?.value,
                    tags = tagNames,
                    createdAt = errorBook.createdAt.format(isoFormatter),
                    updatedAt = errorBook.updatedAt.format(isoFormatter)
                )
            }
        }.let { records ->
            if (tag != null) {
                records.filter { it.tags.any { t -> t.equals(tag, ignoreCase = true) } }
            } else {
                records
            }
        }

    fun update(
        userId: UUID,
        errorId: UUID,
        description: String?,
        imagePath: String?,
        date: LocalDateTime?,
        eventId: UUID?,
        tagNames: List<String>?
    ): ErrorBookRecord = transaction {
        ErrorBooks.selectAll()
            .andWhere { ErrorBooks.id eq errorId }
            .andWhere { ErrorBooks.user eq userId }
            .singleOrNull()
            ?: throw IllegalArgumentException("Error record not found")

        if (description != null || imagePath != null || date != null || eventId != null) {
            ErrorBooks.update({ ErrorBooks.id eq errorId }) {
                if (description != null) it[ErrorBooks.description] = description
                if (imagePath != null) it[ErrorBooks.imagePath] = imagePath
                if (date != null) it[ErrorBooks.date] = date
                if (eventId != null) {
                    it[ErrorBooks.eventId] = if (eventId != UUID(0, 0)) EntityID(eventId, Events) else null
                }
                it[ErrorBooks.updatedAt] = LocalDateTime.now()
            }
        }

        if (tagNames != null) {
            ErrorTags.deleteWhere { ErrorTags.error eq errorId }
            val tagEntities = resolveTags(tagNames)
            for (tag in tagEntities) {
                ErrorTags.insert {
                    it[ErrorTags.error] = errorId
                    it[ErrorTags.tag] = tag.id
                }
            }
        }

        findById(userId, errorId)!!
    }

    fun delete(userId: UUID, errorId: UUID): String? = transaction {
        val row = ErrorBooks.selectAll()
            .andWhere { ErrorBooks.id eq errorId }
            .andWhere { ErrorBooks.user eq userId }
            .singleOrNull()
            ?: throw IllegalArgumentException("Error record not found")

        val imagePath = ErrorBook.wrapRow(row).imagePath

        ErrorTags.deleteWhere { ErrorTags.error eq errorId }
        ErrorBooks.deleteWhere { ErrorBooks.id eq errorId }

        imagePath
    }

    fun updateImagePath(userId: UUID, errorId: UUID, newImagePath: String): String? = transaction {
        val oldRow = ErrorBooks.selectAll()
            .andWhere { ErrorBooks.id eq errorId }
            .andWhere { ErrorBooks.user eq userId }
            .singleOrNull()
            ?: throw IllegalArgumentException("Error record not found")

        val oldPath = ErrorBook.wrapRow(oldRow).imagePath

        ErrorBooks.update({ ErrorBooks.id eq errorId }) {
            it[ErrorBooks.imagePath] = newImagePath
            it[ErrorBooks.updatedAt] = LocalDateTime.now()
        }

        oldPath
    }
}
