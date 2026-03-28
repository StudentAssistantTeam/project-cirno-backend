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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.ByteBuffer
import java.util.UUID
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
            .andExpect(jsonPath("$.name").value("Study Session"))
            .andExpect(jsonPath("$.datetime").value("2026-04-01T14:00:00"))
            .andExpect(jsonPath("$.description").value("Review algorithms"))
            .andExpect(jsonPath("$.tags").isArray)
            .andExpect(jsonPath("$.tags.length()").value(2))
            .andExpect(jsonPath("$.tags[0]").value("math"))
            .andExpect(jsonPath("$.tags[1]").value("study"))
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
            .andExpect(jsonPath("$.name").value("Quick Event"))
            .andExpect(jsonPath("$.datetime").value("2026-05-15T09:00:00"))
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
            .andExpect(jsonPath("$.tags.length()").value(3))

        // Verify DB has only two distinct tags (math + MATH, deduplicated)
        val tagCount = transaction {
            exec("SELECT COUNT(*) FROM tags") { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
        assertTrue(tagCount == 2, "Expected exactly 2 tags in DB, found $tagCount")
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

    // --- GET /api/events tests ---

    private fun createEvent(token: String, name: String, datetime: String, tags: List<String> = emptyList()) {
        val tagsJson = if (tags.isNotEmpty()) """, "tags": ${objectMapper.writeValueAsString(tags)}""" else ""
        val body = """{"name": "$name", "datetime": "$datetime"$tagsJson}"""
        mockMvc.perform(post("/api/events")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isCreated)
    }

    @Test
    fun `should return 403 when getting events without auth`() {
        mockMvc.perform(get("/api/events")
            .param("start", "2026-01-01T00:00:00")
            .param("length", "1"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `should return events within time range`() {
        val token = signupAndLogin()
        createEvent(token, "Jan Event", "2026-01-15T10:00:00")
        createEvent(token, "Feb Event", "2026-02-15T10:00:00")
        createEvent(token, "Mar Event", "2026-03-15T10:00:00", listOf("math"))

        mockMvc.perform(get("/api/events")
            .header("Authorization", "Bearer $token")
            .param("start", "2026-01-01T00:00:00")
            .param("length", "2"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.events.length()").value(2))
            .andExpect(jsonPath("$.events[0].name").value("Jan Event"))
            .andExpect(jsonPath("$.events[0].tags").isArray)
            .andExpect(jsonPath("$.events[1].name").value("Feb Event"))
    }

    @Test
    fun `should return empty list when no events in range`() {
        val token = signupAndLogin()
        createEvent(token, "Summer Event", "2026-07-01T10:00:00")

        mockMvc.perform(get("/api/events")
            .header("Authorization", "Bearer $token")
            .param("start", "2026-01-01T00:00:00")
            .param("length", "3"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.events.length()").value(0))
    }

    @Test
    fun `should return 400 when start format is invalid`() {
        val token = signupAndLogin()

        mockMvc.perform(get("/api/events")
            .header("Authorization", "Bearer $token")
            .param("start", "not-a-date")
            .param("length", "1"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should only return events for the authenticated user`() {
        val token1 = signupAndLogin("user1", "user1@example.com")
        val token2 = signupAndLogin("user2", "user2@example.com")
        createEvent(token1, "User1 Event", "2026-02-15T10:00:00")
        createEvent(token2, "User2 Event", "2026-02-16T10:00:00")

        mockMvc.perform(get("/api/events")
            .header("Authorization", "Bearer $token1")
            .param("start", "2026-01-01T00:00:00")
            .param("length", "12"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.events.length()").value(1))
            .andExpect(jsonPath("$.events[0].name").value("User1 Event"))
    }

    // --- PUT /api/events/{id} tests ---

    @Test
    fun `should return 403 when updating event without auth`() {
        val eventId = UUID.randomUUID().toString()
        val request = """
            {
                "name": "Updated Event",
                "datetime": "2026-04-01T14:00:00"
            }
        """.trimIndent()

        mockMvc.perform(put("/api/events/$eventId")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `should return 200 when updating event successfully`() {
        val token = signupAndLogin()
        createEvent(token, "Original Event", "2026-04-01T14:00:00", listOf("tag1"))

        // Get the event ID (assuming we can get it from the creation response or by querying)
        // For simplicity, let's query the DB to get the ID
        val eventId = transaction {
            exec("SELECT id FROM events LIMIT 1") { rs ->
                if (rs.next()) {
                    val bytes = rs.getBytes("id")
                    val bb = ByteBuffer.wrap(bytes)
                    UUID(bb.long, bb.long).toString()
                } else null
            }
        }

        val request = """
            {
                "name": "Updated Event",
                "datetime": "2026-05-01T14:00:00",
                "description": "Updated description",
                "tags": ["tag2", "tag3"]
            }
        """.trimIndent()

        mockMvc.perform(put("/api/events/$eventId")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Updated Event"))
            .andExpect(jsonPath("$.datetime").value("2026-05-01T14:00:00"))
            .andExpect(jsonPath("$.description").value("Updated description"))
            .andExpect(jsonPath("$.tags.length()").value(2))
            .andExpect(jsonPath("$.tags[0]").value("tag2"))
            .andExpect(jsonPath("$.tags[1]").value("tag3"))
    }

    @Test
    fun `should return 400 when updating non-existent event`() {
        val token = signupAndLogin()
        val fakeId = UUID.randomUUID().toString()

        val request = """
            {
                "name": "Updated Event",
                "datetime": "2026-04-01T14:00:00"
            }
        """.trimIndent()

        mockMvc.perform(put("/api/events/$fakeId")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should return 400 when updating another user's event`() {
        val token1 = signupAndLogin("user1", "user1@example.com")
        val token2 = signupAndLogin("user2", "user2@example.com")
        
        createEvent(token1, "User1 Event", "2026-04-01T14:00:00")
        
        val eventId = transaction {
            exec("SELECT id FROM events LIMIT 1") { rs ->
                if (rs.next()) {
                    val bytes = rs.getBytes("id")
                    val bb = ByteBuffer.wrap(bytes)
                    UUID(bb.long, bb.long).toString()
                } else null
            }
        }

        val request = """
            {
                "name": "Hacked Event",
                "datetime": "2026-04-01T14:00:00"
            }
        """.trimIndent()

        mockMvc.perform(put("/api/events/$eventId")
            .header("Authorization", "Bearer $token2")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isBadRequest)
    }

    // --- DELETE /api/events/{id} tests ---

    @Test
    fun `should return 403 when deleting event without auth`() {
        val eventId = UUID.randomUUID().toString()
        mockMvc.perform(delete("/api/events/$eventId"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `should return 200 when deleting event successfully`() {
        val token = signupAndLogin()
        createEvent(token, "To Delete", "2026-04-01T14:00:00")

        val eventId = transaction {
            exec("SELECT id FROM events LIMIT 1") { rs ->
                if (rs.next()) {
                    val bytes = rs.getBytes("id")
                    val bb = ByteBuffer.wrap(bytes)
                    UUID(bb.long, bb.long).toString()
                } else null
            }
        }

        mockMvc.perform(delete("/api/events/$eventId")
            .header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Event deleted"))
            
        // Verify it's gone
        mockMvc.perform(get("/api/events")
            .header("Authorization", "Bearer $token")
            .param("start", "2026-01-01T00:00:00")
            .param("length", "12"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.events.length()").value(0))
    }

    @Test
    fun `should return 400 when deleting non-existent event`() {
        val token = signupAndLogin()
        val fakeId = UUID.randomUUID().toString()

        mockMvc.perform(delete("/api/events/$fakeId")
            .header("Authorization", "Bearer $token"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should return 400 when deleting another user's event`() {
        val token1 = signupAndLogin("user1", "user1@example.com")
        val token2 = signupAndLogin("user2", "user2@example.com")
        
        createEvent(token1, "User1 Event", "2026-04-01T14:00:00")
        
        val eventId = transaction {
            exec("SELECT id FROM events LIMIT 1") { rs ->
                if (rs.next()) {
                    val bytes = rs.getBytes("id")
                    val bb = ByteBuffer.wrap(bytes)
                    UUID(bb.long, bb.long).toString()
                } else null
            }
        }

        mockMvc.perform(delete("/api/events/$eventId")
            .header("Authorization", "Bearer $token2"))
            .andExpect(status().isBadRequest)
    }
}
