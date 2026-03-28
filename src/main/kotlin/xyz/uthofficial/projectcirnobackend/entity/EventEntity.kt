package xyz.uthofficial.projectcirnobackend.entity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import org.jetbrains.exposed.v1.javatime.CurrentDateTime
import org.jetbrains.exposed.v1.javatime.datetime
import java.util.UUID

/**
 * Table: events (UUID PK, user FK, name, datetime, description, created_at, updated_at)
 * Each event belongs to a single user.
 */
object Events : UUIDTable("events") {
    val user = reference("user_id", Users)
    val name = varchar("name", 255)
    val datetime = datetime("datetime")
    val description = text("description").nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}

/**
 * Entity mapped to the events table.
 */
class Event(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Event>(Events)

    var user by Events.user
    var name by Events.name
    var datetime by Events.datetime
    var description by Events.description
    var createdAt by Events.createdAt
    var updatedAt by Events.updatedAt
}

/**
 * Table: tags (UUID PK, name unique)
 * Reusable tags that can be associated with many events.
 */
object Tags : UUIDTable("tags") {
    val name = varchar("name", 50).uniqueIndex()
}

/**
 * Entity mapped to the tags table.
 */
class Tag(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Tag>(Tags)

    var name by Tags.name
}

/**
 * Table: event_tags (event FK, tag FK, composite unique constraint)
 * Join table linking events and tags in a many-to-many relationship.
 */
object EventTags : Table("event_tags") {
    val event = reference("event_id", Events)
    val tag = reference("tag_id", Tags)

    override val primaryKey = PrimaryKey(event, tag)
}
