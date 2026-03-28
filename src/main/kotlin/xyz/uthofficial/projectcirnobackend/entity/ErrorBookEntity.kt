package xyz.uthofficial.projectcirnobackend.entity

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import org.jetbrains.exposed.v1.javatime.CurrentDateTime
import org.jetbrains.exposed.v1.javatime.datetime
import java.util.UUID

/**
 * Table: error_book (UUID PK, user FK, description, image_path, date, event_id, created_at, updated_at)
 * Each error record belongs to a single user. At least one of description or image_path must be set.
 */
object ErrorBooks : UUIDTable("error_book") {
    val user = reference("user_id", Users)
    val description = text("description").nullable()
    val imagePath = varchar("image_path", 500).nullable()
    val date = datetime("date").nullable()
    val eventId = reference("event_id", Events).nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}

class ErrorBook(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ErrorBook>(ErrorBooks)

    var user by ErrorBooks.user
    var description by ErrorBooks.description
    var imagePath by ErrorBooks.imagePath
    var date by ErrorBooks.date
    var eventId by ErrorBooks.eventId
    var createdAt by ErrorBooks.createdAt
    var updatedAt by ErrorBooks.updatedAt
}

/**
 * Table: error_tags (error FK, tag FK, composite unique constraint)
 * Join table linking error records and tags in a many-to-many relationship.
 * Reuses the shared Tags table.
 */
object ErrorTags : org.jetbrains.exposed.v1.core.Table("error_tags") {
    val error = reference("error_id", ErrorBooks)
    val tag = reference("tag_id", Tags)

    override val primaryKey = PrimaryKey(error, tag)
}
