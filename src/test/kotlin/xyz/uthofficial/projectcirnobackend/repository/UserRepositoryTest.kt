package xyz.uthofficial.projectcirnobackend.repository

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
class UserRepositoryTest {

    @Autowired
    private lateinit var userRepository: UserRepository

    @BeforeEach
    fun setup() {
        transaction { exec("DELETE FROM users") }
    }

    @Test
    fun `should create and find user by username`() {
        val user = userRepository.createUser("testuser", "test@example.com", "hashedPassword")

        val found = userRepository.findByUsername("testuser")

        assertNotNull(found)
        assertEquals("testuser", found.username)
        assertEquals("test@example.com", found.email)
        assertEquals("hashedPassword", found.password)
    }

    @Test
    fun `should create and find user by email`() {
        userRepository.createUser("testuser", "test@example.com", "hashedPassword")

        val found = userRepository.findByEmail("test@example.com")

        assertNotNull(found)
        assertEquals("testuser", found.username)
        assertEquals("test@example.com", found.email)
    }

    @Test
    fun `should return null when user not found by username`() {
        val found = userRepository.findByUsername("nonexistent")

        assertEquals(null, found)
    }

    @Test
    fun `should return null when user not found by email`() {
        val found = userRepository.findByEmail("nonexistent@example.com")

        assertEquals(null, found)
    }

    @Test
    fun `should return true when username exists`() {
        userRepository.createUser("testuser", "test@example.com", "hashedPassword")

        val exists = userRepository.existsByUsername("testuser")

        assertTrue(exists)
    }

    @Test
    fun `should return false when username does not exist`() {
        val exists = userRepository.existsByUsername("nonexistent")

        assertFalse(exists)
    }

    @Test
    fun `should return true when email exists`() {
        userRepository.createUser("testuser", "test@example.com", "hashedPassword")

        val exists = userRepository.existsByEmail("test@example.com")

        assertTrue(exists)
    }

    @Test
    fun `should return false when email does not exist`() {
        val exists = userRepository.existsByEmail("nonexistent@example.com")

        assertFalse(exists)
    }

    @Test
    fun `should find user by ID`() {
        val user = userRepository.createUser("testuser", "test@example.com", "hashedPassword")

        val found = userRepository.findById(user.id)

        assertNotNull(found)
        assertEquals("testuser", found.username)
    }

    @Test
    fun `should return null when user not found by ID`() {
        val fakeId = java.util.UUID.randomUUID()
        val found = userRepository.findById(fakeId)

        assertEquals(null, found)
    }

    @Test
    fun `should not allow duplicate usernames`() {
        userRepository.createUser("testuser", "test@example.com", "hashedPassword")

        val exists = userRepository.existsByUsername("testuser")

        assertTrue(exists)
    }

    @Test
    fun `should not allow duplicate emails`() {
        userRepository.createUser("testuser", "test@example.com", "hashedPassword")

        val exists = userRepository.existsByEmail("test@example.com")

        assertTrue(exists)
    }
}
