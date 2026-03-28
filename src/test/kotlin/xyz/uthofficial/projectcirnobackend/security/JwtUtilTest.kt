package xyz.uthofficial.projectcirnobackend.security

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
class JwtUtilTest {

    @Value("\${app.jwt.secret}")
    private lateinit var secret: String

    @Value("\${app.jwt.expiration-ms}")
    private var expirationMs: Long = 86400000

    private lateinit var jwtUtil: JwtUtil

    @BeforeEach
    fun setup() {
        jwtUtil = JwtUtil(secret, expirationMs)
    }

    @Test
    fun `should generate valid token`() {
        val token = jwtUtil.generateToken("testuser")

        assertNotNull(token)
        assertTrue(token.isNotEmpty())
    }

    @Test
    fun `should extract username from valid token`() {
        val token = jwtUtil.generateToken("testuser")

        val username = jwtUtil.extractUsername(token)

        assertEquals("testuser", username)
    }

    @Test
    fun `should validate valid token`() {
        val token = jwtUtil.generateToken("testuser")

        val isValid = jwtUtil.validateToken(token)

        assertTrue(isValid)
    }

    @Test
    fun `should reject invalid token`() {
        val invalidToken = "invalid.token.here"

        val isValid = jwtUtil.validateToken(invalidToken)

        assertFalse(isValid)
    }

    @Test
    fun `should reject token with wrong signature`() {
        val token = jwtUtil.generateToken("testuser")

        // Create a different JWT util with a different secret
        val differentSecret = "different-secret-key-for-testing-12345678901234567890123456789012"
        val differentJwtUtil = JwtUtil(differentSecret, expirationMs)

        val isValid = differentJwtUtil.validateToken(token)

        assertFalse(isValid)
    }

    @Test
    fun `should extract correct username from token`() {
        val username = "uniqueuser123"
        val token = jwtUtil.generateToken(username)

        val extractedUsername = jwtUtil.extractUsername(token)

        assertEquals(username, extractedUsername)
    }

    @Test
    fun `should handle different usernames`() {
        val user1 = "user1"
        val user2 = "user2"

        val token1 = jwtUtil.generateToken(user1)
        val token2 = jwtUtil.generateToken(user2)

        assertEquals(user1, jwtUtil.extractUsername(token1))
        assertEquals(user2, jwtUtil.extractUsername(token2))
    }
}

// Helper function for null checks in tests
fun <T> assertNotNull(value: T?): T {
    if (value == null) throw AssertionError("Expected non-null value but was null")
    return value
}
