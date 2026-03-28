package xyz.uthofficial.projectcirnobackend.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class EventControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setup() {
        transaction {
            exec("DELETE FROM event_tags")
            exec("DELETE FROM events")
            exec("DELETE FROM tags")
            exec("DELETE FROM users")
        }
    }

    private fun signupAndLogin(username: String = "testuser", email: String = "test@example.com"): String {
        // Signup
        val signupRequest = """
            {
                "username": "$username",
                "email": "$email",
                "password": "password123"
            }
        """.trimIndent()

        val signupResult = mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(signupRequest))
            .andExpect(status().isCreated)
            .andReturn()

        return objectMapper.readTree(signupResult.response.contentAsString).get("token").asText()
    }

    @Test
    fun `should return 403 when creating event without auth`() {
        val request = """
            {
                "name": "My Event",
                "datetime": "2026-04-01T14:00:00"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/events")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `should return 201 when creating event with all fields`() {
        val token = signupAndLogin()

        val request = """
            {
                "name": "Study Session",
                "datetime": "2026-04-01T14:00:00",
                "description": "Review algorithms",
                "tags": ["math", "study"]
            }
        """.trimIndent()

        mockMvc.perform(post("/api/events")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").value("Study Session"))
            .andExpect(jsonPath("$.datetime").value("2026-04-01T14:00:00"))
            .andExpect(jsonPath("$.description").value("Review algorithms"))
            .andExpect(jsonPath("$.tags").isArray)
            .andExpect(jsonPath("$.tags.length()").value(2))
            .andExpect(jsonPath("$.tags[0]").value("math"))
            .andExpect(jsonPath("$.tags[1]").value("study"))
            .andExpect(jsonPath("$.createdAt").exists())
    }

    @Test
    fun `should return 201 when creating event without optional fields`() {
        val token = signupAndLogin()

        val request = """
            {
                "name": "Quick Event",
                "datetime": "2026-05-15T09:00:00"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/events")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").value("Quick Event"))
            .andExpect(jsonPath("$.datetime").value("2026-05-15T09:00:00"))
            .andExpect(jsonPath("$.description").isEmpty)
            .andExpect(jsonPath("$.tags").isArray)
            .andExpect(jsonPath("$.tags.length()").value(0))
    }

    @Test
    fun `should reuse existing tags instead of creating duplicates`() {
        val token = signupAndLogin()

        // Create first event with tag "math"
        val firstRequest = """
            {
                "name": "First Event",
                "datetime": "2026-04-01T10:00:00",
                "tags": ["math"]
            }
        """.trimIndent()

        mockMvc.perform(post("/api/events")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(firstRequest))
            .andExpect(status().isCreated)

        // Create second event with same tag "math"
        val secondRequest = """
            {
                "name": "Second Event",
                "datetime": "2026-04-02T10:00:00",
                "tags": ["math", "physics"]
            }
        """.trimIndent()

        mockMvc.perform(post("/api/events")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(secondRequest))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.tags.length()").value(2))

        // Verify only one "math" tag exists in the database
        val tagCount = transaction {
            exec("SELECT COUNT(*) FROM tags WHERE name = 'math'") { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
        assertTrue(tagCount == 1, "Expected exactly one 'math' tag, found $tagCount")
    }

    @Test
    fun `should deduplicate tags within a single request`() {
        val token = signupAndLogin()

        val request = """
            {
                "name": "Dup Tag Event",
                "datetime": "2026-04-01T10:00:00",
                "tags": ["math", "math", "MATH"]
            }
        """.trimIndent()

        mockMvc.perform(post("/api/events")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.tags.length()").value(2))
    }

    @Test
    fun `should return 400 when event name is missing`() {
        val token = signupAndLogin()

        val request = """
            {
                "datetime": "2026-04-01T14:00:00"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/events")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should return 400 when datetime is missing`() {
        val token = signupAndLogin()

        val request = """
            {
                "name": "No Date Event"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/events")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should return 400 when tag name exceeds 50 characters`() {
        val token = signupAndLogin()
        val longTag = "a".repeat(51)

        val request = """
            {
                "name": "Long Tag Event",
                "datetime": "2026-04-01T14:00:00",
                "tags": ["$longTag"]
            }
        """.trimIndent()

        mockMvc.perform(post("/api/events")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should return 400 when datetime format is invalid`() {
        val token = signupAndLogin()

        val request = """
            {
                "name": "Bad Date Event",
                "datetime": "not-a-date"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/events")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isBadRequest)
    }
}
