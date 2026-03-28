package xyz.uthofficial.projectcirnobackend.repository

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Repository
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import xyz.uthofficial.projectcirnobackend.entity.UserIdentity
import xyz.uthofficial.projectcirnobackend.entity.UserIdentities
import xyz.uthofficial.projectcirnobackend.entity.Users
import java.time.LocalDateTime
import java.util.UUID

@Repository
class UserIdentityRepository {

    fun findByUserId(userId: UUID): UserIdentity? = transaction {
        UserIdentity.find { UserIdentities.user eq userId }.singleOrNull()
    }

    fun create(userId: UUID, identity: String, goal: String): UserIdentity = transaction {
        val existing = UserIdentity.find { UserIdentities.user eq userId }.singleOrNull()
        if (existing != null) throw IllegalStateException("User already has an identity")
        UserIdentity.new {
            this.user = EntityID(userId, Users)
            this.identity = identity
            this.goal = goal
        }
    }

    fun update(userId: UUID, identity: String?, goal: String?): UserIdentity = transaction {
        val existing = UserIdentity.find { UserIdentities.user eq userId }.singleOrNull()
            ?: throw NoSuchElementException("Identity not found")
        if (identity != null) existing.identity = identity
        if (goal != null) existing.goal = goal
        existing.updatedAt = LocalDateTime.now()
        existing
    }
}
