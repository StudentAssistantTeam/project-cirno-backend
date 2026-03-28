package xyz.uthofficial.projectcirnobackend.config

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        transaction { exec("DELETE FROM users") }
    }

    @Test
    fun `should allow access to signup endpoint without authentication`() {
        val request = """
            {
                "username": "newuser",
                "email": "newuser@example.com",
                "password": "password123"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isCreated)
    }

    @Test
    fun `should allow access to swagger endpoints without authentication`() {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk)
    }

    @Test
    fun `should deny access to protected endpoints without authentication`() {
        mockMvc.perform(get("/api/protected"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `should use stateless session management`() {
        val request = """
            {
                "username": "testuser",
                "email": "test@example.com",
                "password": "password123"
            }
        """.trimIndent()

        val result = mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isCreated)
            .andReturn()

        // Stateless: no session cookie should be set
        val setCookieHeader = result.response.getHeader("Set-Cookie")
        assert(setCookieHeader == null || !setCookieHeader.contains("JSESSIONID"))
    }

    @Test
    fun `should have CSRF disabled`() {
        // CSRF is disabled in SecurityConfig for stateless REST API
        // This is verified by the fact that POST requests work without CSRF tokens
        val request = """
            {
                "username": "csrfuser",
                "email": "csrfuser@example.com",
                "password": "password123"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isCreated)
    }
}
