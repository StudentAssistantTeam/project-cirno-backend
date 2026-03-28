package xyz.uthofficial.projectcirnobackend.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

/**
 * JWT utility: generate, extract username, validate.
 * Secret is Base64-decoded from app.jwt.secret; expiration from app.jwt.expiration-ms.
 */
@Component
class JwtUtil(
    @Value($$"${app.jwt.secret}") private val secret: String,
    @Value($$"${app.jwt.expiration-ms}") private val expirationMs: Long
) {

    /** Lazily decoded HMAC-SHA key from the Base64 secret. */
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(secret))
    }

    /** Generates a signed JWT with the username as the subject claim. */
    fun generateToken(username: String): String {
        val now = Date()
        val expiry = Date(now.time + expirationMs)
        return Jwts.builder()
            .subject(username)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    /** Extracts the username (subject claim) from a valid JWT. */
    fun extractUsername(token: String): String {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
            .subject
    }

    /** Validates a JWT: verifies signature and checks expiration. */
    fun validateToken(token: String): Boolean {
        return try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
            true
        } catch (e: Exception) {
            false
        }
    }
}
