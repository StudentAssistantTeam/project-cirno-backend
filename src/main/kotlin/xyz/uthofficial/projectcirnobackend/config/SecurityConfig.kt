package xyz.uthofficial.projectcirnobackend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import xyz.uthofficial.projectcirnobackend.security.JwtAuthenticationFilter

/**
 * Spring Security configuration for the application.
 *
 * Defines the HTTP security filter chain, password encoder, and authentication
 * manager bean. The application operates in a stateless session mode
 * designed for JWT-based authentication. No server-side session is created.
 *
 * Access rules:
 * - "/api/auth/" is public (signup, login).
 * - "/swagger-ui/", "/v3/api-docs/" is public (API docs).
 * - Everything else requires authentication (valid JWT).
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    /**
     * Provides a BCryptPasswordEncoder as the application-wide PasswordEncoder.
     *
     * BCrypt is a one-way adaptive hashing function with built-in salting,
     * suitable for password storage. The default strength (10 rounds) is used.
     *
     * @return A BCrypt-based password encoder.
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    /**
     * Exposes the AuthenticationManager as a Spring bean.
     *
     * The manager coordinates the authentication process. It accepts credentials,
     * delegates to the configured UserDetailsService and PasswordEncoder, and
     * returns an Authentication object on success.
     *
     * @param config Autoconfigured AuthenticationConfiguration provided by Spring Security.
     * @return The application's AuthenticationManager.
     */
    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager

    /**
     * Builds the HTTP security filter chain.
     *
     * Key decisions:
     * - CSRF is disabled, appropriate for a stateless REST API consumed by
     *   non-browser clients that send JWTs in the Authorization header.
     * - Sessions are STATELESS: no JSESSIONID cookie is issued; every request
     *   must carry a valid JWT.
     * - Auth endpoints like /api/auth/signup and /api/auth/login are accessible
     *   without authentication so that new users can register and existing
     *   users can obtain tokens.
     * - Swagger/OpenAPI documentation endpoints are left open for development.
     *
     * @param http The HttpSecurity builder to configure.
     * @return A fully constructed SecurityFilterChain.
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = listOf("http://localhost:3000")
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtAuthenticationFilter: JwtAuthenticationFilter
    ): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/api/health").permitAll()
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                    .anyRequest().authenticated()
            }
        return http.build()
    }
}
