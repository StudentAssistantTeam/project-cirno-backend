package xyz.uthofficial.projectcirnobackend.entity

import kotlin.uuid.ExperimentalUuidApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import org.jetbrains.exposed.v1.javatime.CurrentDateTime
import org.jetbrains.exposed.v1.javatime.datetime
import java.util.*

/**
 * Table: chat_sessions (UUID PK, user FK, title, created_at, updated_at)
 * Each chat session belongs to a single user.
 */
object ChatSessions : UUIDTable("chat_sessions") {
    val user = reference("user_id", Users)
    val title = varchar("title", 255).nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}

/**
 * Table: chat_messages (UUID PK, session FK, role, content, created_at)
 * Messages within a chat session, ordered by created_at.
 */
@OptIn(ExperimentalUuidApi::class)
object ChatMessages : Table("chat_messages") {
    val id = uuid("id").autoGenerate()
    val session = reference("session_id", ChatSessions)
    val role = varchar("role", 20)
    val content = text("content").nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)

    override val primaryKey = PrimaryKey(id)
}
