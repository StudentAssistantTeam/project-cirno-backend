# Testing Setup Report — project-cirno-backend

## Existing Test Files

| File | Purpose |
|------|---------|
| `src/test/.../ProjectCirnoBackendApplicationTests.kt` | `@SpringBootTest` context load test (JUnit 5) |
| `src/test/.../TestProjectCirnoBackendApplication.kt` | Test app entry point wired with Testcontainers |
| `src/test/.../TestcontainersConfiguration.kt` | Empty `@TestConfiguration` placeholder for Testcontainers |

The `src/contractTest/resources/contracts/` directory exists but is empty — no contract definitions yet.

---

## Test Dependencies

### 1. Core Framework

- **JUnit 5** via `kotlin-test-junit5` + `junit-platform-launcher`
- Runner: `useJUnitPlatform()` configured for both `test` and `contractTest` tasks
- Spring profiles: tests run with `spring.profiles.active=local`

### 2. Web Layer Testing

| Dependency | Capability |
|------------|------------|
| `spring-boot-starter-webmvc-test` | MockMvc, `@WebMvcTest`, sliced controller tests |
| `spring-boot-starter-webclient-test` | `WebTestClient` for reactive-style HTTP testing |
| `spring-boot-starter-restclient-test` | `MockRestServiceServer` for RestClient |
| `spring-boot-starter-data-rest-test` | `@DataRestTest`, repository-level REST endpoint testing |
| `spring-boot-starter-graphql-test` | `@GraphQlTest`, GraphQL query/mutation testing |
| `spring-boot-starter-webservices-test` | `MockWebServiceServer` for SOAP/XML services |
| `spring-boot-starter-websocket-test` | WebSocket integration testing |

### 3. Security Testing

| Dependency | Capability |
|------------|------------|
| `spring-boot-starter-security-oauth2-authorization-server-test` | OAuth2 authorization server integration tests |
| `spring-boot-starter-security-oauth2-client-test` | OAuth2 client flow testing |
| `spring-boot-starter-security-oauth2-resource-server-test` | JWT/resource server security testing |

### 4. Infrastructure — Testcontainers

| Dependency | Capability |
|------------|------------|
| `testcontainers-junit-jupiter` | JUnit 5 lifecycle for Docker containers |
| `spring-boot-testcontainers` | Auto-configured container beans via `@ServiceConnection` |
| `spring-ai-spring-boot-testcontainers` | Containers for AI model testing |

### 5. Contract Testing (Spring Cloud Contract)

| Dependency | Capability |
|------------|------------|
| `spring-cloud-starter-contract-verifier` | Generates server-side tests from contract YAML/Groovy |
| `spring-cloud-starter-contract-stub-runner` | Runs stubs from contracts for consumer-side testing |
| Config: `TestMode.WEBTESTCLIENT`, `failOnNoContracts = false` | Contracts are verified via WebTestClient; no failure if none defined |

### 6. API Documentation

| Dependency | Capability |
|------------|------------|
| `spring-boot-starter-restdocs` | Generates Asciidoctor API docs from tests |
| `spring-restdocs-mockmvc` | MockMvc snippets for REST Docs |
| Asciidoctor plugin + `build/generated-snippets` output | Auto-generates docs after `test` task |

### 7. gRPC Testing

| Dependency | Capability |
|------------|------------|
| `spring-grpc-test` | In-process gRPC server for testing gRPC services |

### 8. Module Testing

| Dependency | Capability |
|------------|------------|
| `spring-modulith-starter-test` | Verifies Spring Modulith module boundaries and event publication |

### 9. Async Testing

| Dependency | Capability |
|------------|------------|
| `kotlinx-coroutines-test` | `runTest`, `TestDispatcher`, `UnconfinedTestDispatcher` for coroutines |

### 10. LDAP Testing

| Dependency | Capability |
|------------|------------|
| `unboundid-ldapsdk` | In-memory LDAP server for testing LDAP auth |

---

## Summary

| Test Type | Readiness |
|-----------|-----------|
| **Unit tests** (JUnit 5 + Kotlin) | Ready — framework installed, no tests written |
| **Web/Slice tests** (`@WebMvcTest`, `@GraphQlTest`, etc.) | Ready — 7 starter-test dependencies |
| **Integration tests** (`@SpringBootTest`) | Ready — 1 context-loads test exists |
| **Contract tests** (Spring Cloud Contract) | Partially ready — config exists, contracts directory empty |
| **Container tests** (Testcontainers) | Partially ready — config class empty, deps installed |
| **REST Docs** (Asciidoctor) | Ready — plugin configured, snippet dir wired |

The scaffolding is solid. What's missing is actual **test logic** — assertions, scenarios, and contract definitions.
