package xyz.uthofficial.projectcirnobackend.entity

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import org.jetbrains.exposed.v1.javatime.CurrentDateTime
import org.jetbrains.exposed.v1.javatime.datetime
import java.util.UUID

/**
 * Table: user_identities (UUID PK, user FK UNIQUE, identity, goal, created_at, updated_at)
 * One-to-one relationship with users. Stores the user's identity (e.g. "secondary school student") and goal (e.g. "full A* in physics").
 */
object UserIdentities : UUIDTable("user_identities") {
    val user = reference("user_id", Users).uniqueIndex()
    val identity = varchar("identity", 255)
    val goal = varchar("goal", 255)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}

/**
 * Entity mapped to the user_identities table.
 */
class UserIdentity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<UserIdentity>(UserIdentities)

    var user by UserIdentities.user
    var identity by UserIdentities.identity
    var goal by UserIdentities.goal
    var createdAt by UserIdentities.createdAt
    var updatedAt by UserIdentities.updatedAt
}
