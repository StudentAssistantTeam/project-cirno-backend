# CORS Fix Proposal

## Problem

The frontend at `http://64.49.47.22:57177` cannot reach the backend at `http://localhost:8080`. The browser blocks requests with:

```
Access to fetch at 'http://localhost:8080/api/health' from origin 'http://64.49.47.22:57177'
has been blocked by CORS policy: No 'Access-Control-Allow-Origin' header is present on the
requested resource.
```

This persists despite `SecurityConfig.kt` already defining a `CorsConfigurationSource` bean
with the origin listed, and `application.yaml` containing `app.cors.allowed-origins`.

## Root Cause

Three compounding issues:

1. **`@ConfigurationProperties` binding failure for YAML lists in Kotlin.**
   The `CorsProperties` data class is defined **inline** inside `SecurityConfig.kt`:

   ```kotlin
   @ConfigurationProperties(prefix = "app.cors")
   data class CorsProperties(
       val allowedOrigins: List<String> = listOf("http://localhost:3000")
   )
   ```

   Spring Boot's relaxed binding maps `app.cors.allowed-origins` (kebab-case YAML key)
   to the `allowedOrigins` property. However, binding a **YAML list** to a Kotlin `List<String>`
   inside a `@Configuration` class using constructor injection can silently fall back to the
   default value `[http://localhost:3000]` — meaning `64.49.47.22:57177` is **never loaded**.

2. **`CorsConfigurer` bean lookup semantics in Spring Security 6.x.**
   Spring Security's `CorsConfigurer` looks for beans in this order:
   - A `CorsFilter` bean named `"corsFilter"`
   - A `CorsConfigurationSource` bean named `"corsConfigurationSource"`
   - Spring MVC's `mvcHandlerMappingIntrospector` (if `WebMvcConfigurer` CORS is configured)

   Our bean is named `corsConfigurationSource` and returns `CorsConfigurationSource` (the
   interface). The explicit call `cors { it.configurationSource(corsConfigurationSource()) }`
   should work, but if `@ConfigurationProperties` binding failed and the bean only contains
   the default origin, the CORS headers will only include `http://localhost:3000`.

3. **No `spring.mvc.cors` property-based config exists in Spring Boot 4.0.5.**
   The official Spring Boot 4.0.5 docs show CORS is only configurable via code:
   - `@CrossOrigin` annotations on controllers
   - `WebMvcConfigurer` bean with `addCorsMappings(CorsRegistry)`
   - `CorsConfigurationSource` bean + Spring Security integration

   There is no `spring.mvc.cors.*` YAML property.

## Research Sources

| Source | Finding |
|--------|---------|
| [Spring Boot 4.0.5 Servlet Docs](https://docs.spring.io/spring-boot/reference/web/servlet.html#web.servlet.spring-mvc.cors) | CORS configured only via `WebMvcConfigurer` bean or `@CrossOrigin`. No YAML property support. |
| [Spring Security CorsConfigurer (DeepWiki)](https://deepwiki.com/search/how-to-configure-cors-in-sprin_d2095e4c-fdae-408f-a94f-719befb0eb1a) | Bean lookup order: `corsFilter` → `corsConfigurationSource` → `mvcHandlerMappingIntrospector`. Multiple beans cause ambiguity. |
| [Spring Security #15769](https://github.com/spring-projects/spring-security/issues/15769) | Documentation updated to use `UrlBasedCorsConfigurationSource` for proper auto-detection. |
| [Spring Security #14773](https://github.com/spring-projects/spring-security/issues/14773) | `CorsConfigurationSource` not detected when return type is the interface, not concrete class. |

## Proposed Changes

### 1. `SecurityConfig.kt` — Simplify CORS

**Remove:**
- `CorsProperties` data class (lines 21-24)
- `@EnableConfigurationProperties(CorsProperties::class)` (line 40)
- Constructor injection of `CorsProperties` (line 41)
- Import of `ConfigurationProperties` and `EnableConfigurationProperties`

**Change:**
- `corsConfigurationSource()` return type: `CorsConfigurationSource` → `UrlBasedCorsConfigurationSource`
- Hardcode origins directly in the bean method
- Use `cors { withDefaults() }` in `SecurityFilterChain` (lets Spring Security auto-detect the bean by name)

**Result:**

```kotlin
@Configuration
@EnableWebSecurity
class SecurityConfig(private val jwtAuthenticationFilter: JwtAuthenticationFilter) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager

    @Bean
    fun corsConfigurationSource(): UrlBasedCorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = listOf(
            "http://localhost:3000",
            "http://64.49.47.22:57177"
        )
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { withDefaults() }
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
```

### 2. `application.yaml` — Remove `app.cors` section

```yaml
spring:
  application:
    name: project-cirno-backend
  datasource:
    url: jdbc:sqlite:cirno.db
    driver-class-name: org.sqlite.JDBC

app:
  jwt:
    secret: Y2lybm8tc2VjcmV0LWtleS1mb3Itand0LXRva2VuLXNpZ25pbmctMjAyNg==
    expiration-ms: 86400000
```

### 3. `src/test/resources/application.yaml` — Remove `app.cors` section

Same cleanup as above.

## Why This Works

| Concern | Resolution |
|---------|-----------|
| Origin `64.49.47.22:57177` missing from config | Hardcoded directly — no YAML binding failure possible |
| Bean not auto-detected by `CorsConfigurer` | Return type is `UrlBasedCorsConfigurationSource` (concrete class) |
| Bean name mismatch | Method is named `corsConfigurationSource` — matches Spring Security's lookup |
| Security chain not using bean | `cors { withDefaults() }` tells Spring Security to auto-detect and use the bean |
| Preflight (OPTIONS) blocked | `OPTIONS` is in `allowedMethods`, `/api/health` is `permitAll` — preflight passes |

## Tradeoffs

- **Origins are not runtime-configurable** — adding a new frontend URL requires a code change and rebuild. Acceptable for this project's current scale.
- **If runtime-configurable origins are needed later**, create a standalone `@ConfigurationProperties` class in its own file (not inline in `SecurityConfig.kt`) with proper `@ConfigurationPropertiesScan` annotation.

## How to Add New Origins Later

Add entries to the `allowedOrigins` list in `corsConfigurationSource()`:

```kotlin
configuration.allowedOrigins = listOf(
    "http://localhost:3000",
    "http://64.49.47.22:57177",
    "https://your-production-domain.com"
)
```
