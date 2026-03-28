package xyz.uthofficial.projectcirnobackend.controller

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import xyz.uthofficial.projectcirnobackend.entity.Users
import xyz.uthofficial.projectcirnobackend.repository.UserRepository
import xyz.uthofficial.projectcirnobackend.security.JwtUtil
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var jwtUtil: JwtUtil

    @BeforeEach
    fun setup() {
        transaction { exec("DELETE FROM users") }
    }

    @Test
    fun `should return 201 when signup is successful`() {
        val signupRequest = """
            {
                "username": "newuser",
                "email": "newuser@example.com",
                "password": "password123"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(signupRequest))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.username").value("newuser"))
            .andExpect(jsonPath("$.email").value("newuser@example.com"))
    }

    @Test
    fun `should return 400 when username already exists`() {
        // First signup
        val firstRequest = """
            {
                "username": "existinguser",
                "email": "first@example.com",
                "password": "password123"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(firstRequest))
            .andExpect(status().isCreated)

        // Second signup with same username
        val secondRequest = """
            {
                "username": "existinguser",
                "email": "different@example.com",
                "password": "password123"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(secondRequest))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Username already taken"))
    }

    @Test
    fun `should return 400 when email already exists`() {
        // First signup
        val firstRequest = """
            {
                "username": "user1",
                "email": "existing@example.com",
                "password": "password123"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(firstRequest))
            .andExpect(status().isCreated)

        // Second signup with same email
        val secondRequest = """
            {
                "username": "user2",
                "email": "existing@example.com",
                "password": "password123"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(secondRequest))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Email already in use"))
    }

    @Test
    fun `should return 400 when username is too short`() {
        val request = """
            {
                "username": "ab",
                "email": "short@example.com",
                "password": "password123"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should return 400 when username is too long`() {
        val longUsername = "a".repeat(51)
        val request = """
            {
                "username": "$longUsername",
                "email": "long@example.com",
                "password": "password123"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should return 400 when username has invalid characters`() {
        val request = """
            {
                "username": "user name!",
                "email": "badname@example.com",
                "password": "password123"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should return 400 when email is invalid`() {
        val request = """
            {
                "username": "user",
                "email": "invalid-email",
                "password": "password123"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should return 400 when password is too short`() {
        val request = """
            {
                "username": "user",
                "email": "user@example.com",
                "password": "short"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should return 400 when password is too long`() {
        val longPassword = "a1b2c3d4".repeat(9) // 72 chars
        val request = """
            {
                "username": "user",
                "email": "user@example.com",
                "password": "$longPassword"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should return 400 when password has no digit`() {
        val request = """
            {
                "username": "user",
                "email": "nodigit@example.com",
                "password": "OnlyLetters"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should return 400 when password has no letter`() {
        val request = """
            {
                "username": "user",
                "email": "noletter@example.com",
                "password": "12345678"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should return 400 when username is missing`() {
        val request = """
            {
                "email": "user@example.com",
                "password": "password123"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should return 400 when email is missing`() {
        val request = """
            {
                "username": "user",
                "password": "password123"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should return 400 when password is missing`() {
        val request = """
            {
                "username": "user",
                "email": "user@example.com"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should store hashed password in database`() {
        val request = """
            {
                "username": "testuser",
                "email": "test@example.com",
                "password": "mypassword123"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isCreated)

        // Verify password is hashed in database
        val user = userRepository.findByUsername("testuser")
        assertNotNull(user)
        assertTrue(passwordEncoder.matches("mypassword123", user.password))
        assertFalse(user.password == "mypassword123") // Ensure it's not stored in plain text
    }

    private fun signupUser(username: String, email: String, password: String) {
        val request = """
            {
                "username": "$username",
                "email": "$email",
                "password": "$password"
            }
        """.trimIndent()
        mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isCreated)
    }

    @Test
    fun `should return 200 and JWT token when login with username succeeds`() {
        signupUser("loginuser", "login@example.com", "password123")

        val request = """
            {
                "usernameOrEmail": "loginuser",
                "password": "password123"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").exists())
            .andExpect(jsonPath("$.username").value("loginuser"))
            .andExpect(jsonPath("$.email").value("login@example.com"))
    }

    @Test
    fun `should return 200 and JWT token when login with email succeeds`() {
        signupUser("emailuser", "emailuser@example.com", "password123")

        val request = """
            {
                "usernameOrEmail": "emailuser@example.com",
                "password": "password123"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").exists())
            .andExpect(jsonPath("$.username").value("emailuser"))
            .andExpect(jsonPath("$.email").value("emailuser@example.com"))
    }

    @Test
    fun `should return 401 when login with wrong password`() {
        signupUser("wrongpwuser", "wrongpw@example.com", "password123")

        val request = """
            {
                "usernameOrEmail": "wrongpwuser",
                "password": "wrongpassword123"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("Invalid username/email or password"))
    }

    @Test
    fun `should return 401 when login with nonexistent username`() {
        val request = """
            {
                "usernameOrEmail": "nonexistent",
                "password": "password123"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("Invalid username/email or password"))
    }

    @Test
    fun `should return 401 when login with nonexistent email`() {
        val request = """
            {
                "usernameOrEmail": "nobody@example.com",
                "password": "password123"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("Invalid username/email or password"))
    }

    @Test
    fun `should return 400 when login with missing usernameOrEmail`() {
        val request = """
            {
                "password": "password123"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should return 400 when login with missing password`() {
        val request = """
            {
                "usernameOrEmail": "user"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should return a valid JWT token from login`() {
        signupUser("jwtuser", "jwt@example.com", "password123")

        val request = """
            {
                "usernameOrEmail": "jwtuser",
                "password": "password123"
            }
        """.trimIndent()

        val result = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").exists())
            .andReturn()

        val responseJson = result.response.contentAsString
        val token = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(responseJson)
            .get("token").asText()

        assertTrue(token.isNotBlank())
        assertTrue(jwtUtil.validateToken(token))
        assertEquals("jwtuser", jwtUtil.extractUsername(token))
    }
}
