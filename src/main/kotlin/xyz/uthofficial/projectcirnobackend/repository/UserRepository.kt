package xyz.uthofficial.projectcirnobackend.repository

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository
import xyz.uthofficial.projectcirnobackend.entity.User
import xyz.uthofficial.projectcirnobackend.entity.Users
import java.util.UUID

/** Plain data transfer object — safe to use outside Exposed transactions. */
data class UserRecord(val id: UUID, val username: String, val email: String)

/**
 * Spring-managed repository for user CRUD operations.
 * Each method opens its own Exposed transaction.
 */
@Repository
class UserRepository {

    /** Finds a user by unique username. Returns null if not found. */
    fun findByUsername(username: String): User? = transaction {
        User.find { Users.username eq username }.singleOrNull()
    }

    /** Finds a user by unique email. Returns null if not found. */
    fun findByEmail(email: String): User? = transaction {
        User.find { Users.email eq email }.singleOrNull()
    }

    /** Returns true if a user with this username already exists. */
    fun existsByUsername(username: String): Boolean = transaction {
        User.find { Users.username eq username }.any()
    }

    /** Returns true if a user with this email already exists. */
    fun existsByEmail(email: String): Boolean = transaction {
        User.find { Users.email eq email }.any()
    }

    /**
     * Creates a new user with the given credentials.
     * hashedPassword must already be BCrypt-hashed.
     * Returns a plain DTO to avoid leaking Exposed entities across transaction boundaries.
     */
    fun createUser(username: String, email: String, hashedPassword: String): UserRecord = transaction {
        val user = User.new {
            this.username = username
            this.email = email
            this.password = hashedPassword
        }
        UserRecord(user.id.value, user.username, user.email)
    }

    /** Finds a user by UUID primary key. Returns null if not found. */
    fun findById(id: UUID): User? = transaction {
        User.findById(id)
    }
}
