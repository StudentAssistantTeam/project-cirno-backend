# Multi-Agent Architecture: Spring AI + ADK + A2A

## Executive Summary

This document outlines a plan to evolve Project Cirno Backend into a multi-language, multi-agent system using three complementary technologies:

- **Spring AI 1.0.0** -- LLM integration and tool calling within the existing Spring Boot application.
- **ADK Java 0.8.0** -- Agent framework providing hierarchy, sub-agent delegation, and the coordinator pattern.
- **A2A Protocol** -- Open standard (donated to Linux Foundation by Google) enabling cross-language agent communication.

The resulting system will feature a central coordinator agent that speaks directly to users, delegates tasks to specialized sub-agents, and communicates with external agents built in Python, Go, Node.js, or .NET via the A2A protocol.

---

## 1. Technology Overview

### 1.1 Spring AI (1.0.0)

Spring AI is the official Spring project for AI/LLM integration. It provides a framework-agnostic abstraction over multiple model providers with deep Spring Boot integration.

**Key capabilities for this project:**

| Capability | Description |
|---|---|
| `ChatModel` abstraction | Uniform API across OpenAI, Anthropic, Ollama, Azure, Vertex AI, etc. |
| `@Tool` annotation | Expose Java/Kotlin methods as callable tools for the LLM. Spring AI handles schema generation, invocation, and result routing automatically. |
| `@ToolParam` annotation | Describe method parameters with `description` and `required` attributes for the LLM's tool schema. |
| `ToolCallingManager` | Auto-configured lifecycle manager that intercepts tool calls, executes them, and feeds results back to the model. |
| Spring Boot starters | `spring-ai-starter-model-openai`, `spring-ai-starter-model-ollama`, etc. |
| BOM-managed versions | `spring-ai-bom:1.0.0` (already added to `build.gradle.kts`). |

**Tool calling flow (5 steps):**

1. Tool definitions are included in the chat request to the LLM.
2. The LLM responds with a tool call (name + arguments).
3. Spring AI's `ToolCallingManager` executes the matching `@Tool` method.
4. The tool's return value is sent back to the LLM.
5. The LLM produces the final response using the tool's output.

**Two approaches to register tools:**

- **Declarative:** Annotate methods with `@Tool(description = "...")`, pass the object instance to `ChatClient.prompt().tools(new EventTools())`.
- **Functional:** Define `Function<Request, Response>` beans annotated with `@Description`, reference by bean name via `ChatClient.prompt().toolNames("createEvent")`.

**Current project status:** The Spring AI BOM is already added to `build.gradle.kts:117`. No specific model starter or tool code exists yet.

---

### 1.2 ADK Java (0.8.0)

The Agent Development Kit (ADK) for Java is Google's code-first toolkit for building, evaluating, and deploying AI agents. It provides structured agent hierarchy, tool integration, session management, and execution orchestration.

**Maven coordinates:**

| Module | Artifact ID | Purpose |
|---|---|---|
| Core | `com.google.adk:google-adk` | Agent framework, runtime, tools, LLM integrations |
| Spring AI Bridge | `com.google.adk:google-adk-spring-ai` | Wraps Spring AI `ChatModel` as ADK's `BaseLlm` |
| A2A Bridge | `com.google.adk:google-adk-a2a` | `RemoteA2AAgent` + type converters |
| Dev Server | `com.google.adk:google-adk-dev` | Browser UI for local testing (optional) |

**Core components:**

```
Runner (orchestrator)
  └── App
        └── BaseAgent / LlmAgent (agent logic)
              ├── Instruction (system prompt)
              ├── Model (BaseLlm -- e.g., SpringAI, Gemini, Anthropic)
              ├── Tools (FunctionTool instances)
              └── Sub-agents (hierarchical delegation)
                    ├── Session (conversation state)
                    ├── InvocationContext (mutable execution state)
                    └── Event (immutable interaction log)
```

**Agent hierarchy and transfer:**

ADK supports building coordinator/orchestrator agents via `LlmAgent.builder().subAgents(...)`:

```java
LlmAgent coordinator = LlmAgent.builder()
    .name("coordinator")
    .model(springAI)
    .instruction("Route tasks to the appropriate sub-agent.")
    .subAgents(eventAgent, queryAgent, notificationAgent)
    .build();
```

When the LLM determines a task should be delegated, it emits a `transfer_to_agent` function call. The ADK runtime intercepts this, finds the target agent via `rootAgent.findAgent(name)`, and transfers execution to it. The `AgentTransfer` processor dynamically injects instructions listing available sub-agents and explaining the transfer mechanism.

Additional controls:
- `disallowTransferToParent()` -- prevent delegation to parent.
- `disallowTransferToPeers()` -- prevent delegation to sibling agents.
- `AgentTool(agent)` -- wrap a sub-agent as a callable tool (alternative to transfer).

**Spring AI bridge (`google-adk-spring-ai`):**

This module provides the `SpringAI` class implementing ADK's `BaseLlm` interface. It wraps a Spring AI `ChatModel` and:

- Converts ADK `LlmRequest` → Spring AI `Prompt` via `MessageConverter`.
- Converts Spring AI `ChatResponse` → ADK `LlmResponse` via `MessageConverter`.
- Converts ADK `BaseTool` → Spring AI `ToolCallback` via `ToolConverter`.

Auto-configuration: If a `ChatModel` bean is available, `SpringAI` is automatically configured.

```java
@Bean
public LlmAgent coordinator(SpringAI springAI) {
    return LlmAgent.builder()
        .name("coordinator")
        .model(springAI)
        .instruction("You are a helpful assistant.")
        .build();
}
```

---

### 1.3 A2A Protocol

The Agent2Agent (A2A) Protocol is an open standard for enabling communication and interoperability between independent AI agent systems. Originally developed by Google and donated to the Linux Foundation.

**Design principles:**

| Principle | Implementation |
|---|---|
| Simplicity | HTTP, JSON-RPC 2.0, SSE |
| Enterprise Readiness | OAuth 2.0, OIDC, TLS 1.2+, OpenTelemetry |
| Asynchronous | Task polling, streaming, push notifications |
| Modality Independent | Text, files, structured JSON via `Part` objects |
| Opaque Execution | Agents interact via declared capabilities, not shared memory |

**Core data model:**

| Entity | Description |
|---|---|
| `AgentCard` | JSON metadata document at `/.well-known/agent-card.json`. Describes skills, capabilities, endpoint, security. |
| `Task` | Stateful unit of work with lifecycle: `SUBMITTED` → `WORKING` → `COMPLETED`/`FAILED`/`CANCELED`. |
| `Message` | A single communication turn containing `Part` objects. |
| `Part` | Content container: text, binary, URL, or structured JSON data. |
| `Artifact` | Tangible output produced by a task. |

**Task state machine:**

```
[*] → SUBMITTED → WORKING → COMPLETED (terminal)
                   ↓    ↓
                   ↓    → FAILED (terminal)
                   ↓
                   → INPUT_REQUIRED → WORKING (resumed)
                   → AUTH_REQUIRED → WORKING (resumed)
                   → CANCELED (terminal)
                   → REJECTED (terminal)
```

**Interaction patterns:**

| Pattern | Mechanism | Use Case |
|---|---|---|
| Synchronous | `SendMessage` | Simple request/response |
| Streaming | `SendStreamingMessage` + SSE | Real-time incremental updates |
| Push | `PushNotificationConfig` | Long-running tasks, disconnected clients |
| Multi-turn | Same `contextId` across tasks | Conversational refinement |

**A2A vs MCP:** They are complementary. MCP standardizes agent-to-tool communication. A2A standardizes agent-to-agent communication.

---

### 1.4 A2A Java SDK (`a2aproject/a2a-java`)

The A2A Java SDK is a multi-module Maven project implementing the A2A protocol for Java applications.

**Maven coordinates:** `io.github.a2asdk:*`

**Key modules:**

| Module | Purpose |
|---|---|
| `a2a-java-sdk-spec` | Protocol definitions and data types |
| `a2a-java-sdk-client` | Client API for communicating with A2A servers |
| `a2a-java-sdk-client-transport-jsonrpc` | JSON-RPC transport for clients |
| `a2a-java-sdk-client-transport-grpc` | gRPC transport for clients |
| `a2a-java-sdk-server-common` | Server framework (`AgentExecutor`, `TaskUpdater`) |
| `a2a-java-sdk-reference-jsonrpc` | Quarkus-based JSON-RPC server |

**Server-side key interfaces:**

```java
// AgentExecutor -- implement this for your agent's business logic
public interface AgentExecutor {
    void execute(RequestContext context, EventQueue eventQueue);
    void cancel(RequestContext context, EventQueue eventQueue);
}

// TaskUpdater -- manage task lifecycle
taskUpdater.submit();           // SUBMITTED
taskUpdater.startWork();        // WORKING
taskUpdater.addArtifact(parts); // Add output
taskUpdater.complete();         // COMPLETED
```

**Client-side usage:**

```java
// Discover agent
AgentCard card = new A2ACardResolver(httpClient, baseUrl).getAgentCard();

// Build client
Client client = Client.builder(card)
    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
    .build();

// Send message
client.sendMessage(A2A.toUserMessage("Hello from Java!"));
```

---

## 2. Proposed Architecture

### 2.1 System Overview

```
┌──────────────────────────────────────────────────────────────┐
│                    User / Frontend                            │
└──────────────────────────┬───────────────────────────────────┘
                           │ HTTP / WebSocket
┌──────────────────────────▼───────────────────────────────────┐
│                Coordinator Agent (ADK LlmAgent)                │
│                                                               │
│  Model: SpringAI (wraps OpenAI/Anthropic/Ollama ChatModel)    │
│  Instruction: Route user requests to appropriate sub-agent    │
│  Tools: Backend API tools (Event CRUD, Auth, Query)           │
│                                                               │
│  ┌──────────── Sub-Agent Hierarchy ──────────────────────┐   │
│  │                                                        │   │
│  │  EventAgent (LlmAgent)     QueryAgent (LlmAgent)      │   │
│  │  - create/update/delete    - search events             │   │
│  │  - list events             - summarize data            │   │
│  │  - tag management          - analytics                 │   │
│  │                                                        │   │
│  └────────────────────────────────────────────────────────┘   │
│                                                               │
│  ┌──────────── Remote A2A Agents ────────────────────────┐   │
│  │                                                        │   │
│  │  RemoteA2AAgent("python-nlp")  RemoteA2AAgent("go-ml") │   │
│  │  - NLP / sentiment analysis    - ML predictions        │   │
│  │  - Text summarization          - Data analysis         │   │
│  │                                                        │   │
│  └────────────────────────────────────────────────────────┘   │
│                                                               │
│  Runner ── SessionService (InMemory / SQLite)                 │
└──────────────────────────┬───────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────┐
│              Project Cirno Backend (existing)                  │
│                                                               │
│  Exposed ORM → SQLite │ JWT Auth │ REST API │ GraphQL        │
│  EventRepository │ UserRepository │ SecurityConfig            │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 Communication Layers

```
User ←→ Coordinator Agent (ADK + Spring AI, in-process)
Coordinator ←→ Sub-agents (ADK transfer_to_agent, in-process)
Coordinator ←→ Remote Agents (A2A protocol, JSON-RPC over HTTP)
Agents ←→ Backend Services (Spring AI @Tool, in-process)
```

---

## 3. Backend Interaction via Spring AI Tools

The existing `EventRepository` and `UserRepository` will be exposed as `@Tool`-annotated methods. The coordinator agent calls these tools to interact with the backend.

### 3.1 Tool Definitions

```kotlin
class EventTools(
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository
) {
    @Tool(description = "Create a new event for the authenticated user")
    fun createEvent(
        @ToolParam(description = "Event name", required = true) name: String,
        @ToolParam(description = "ISO 8601 datetime", required = true) datetime: String,
        @ToolParam(description = "Event description") description: String?,
        @ToolParam(description = "Comma-separated tags") tags: String?,
        @ToolParam(description = "Username") username: String
    ): String {
        val user = userRepository.findByUsername(username) ?: return "User not found"
        val request = CreateEventRequest(name, datetime, description, tags)
        eventRepository.createEvent(user.id.value, request)
        return "Event '$name' created successfully."
    }

    @Tool(description = "List events for a user within a time range")
    fun listEvents(
        @ToolParam(description = "Start date (ISO 8601)", required = true) start: String,
        @ToolParam(description = "Number of months", required = true) length: Int,
        @ToolParam(description = "Username", required = true) username: String
    ): String {
        val user = userRepository.findByUsername(username) ?: return "User not found"
        val events = eventRepository.getEventsByTimeRange(user.id.value, start, length)
        return events.joinToString("\n") { "${it.name} @ ${it.datetime}" }
    }

    @Tool(description = "Delete an event by ID")
    fun deleteEvent(
        @ToolParam(description = "Event UUID", required = true) eventId: String,
        @ToolParam(description = "Username", required = true) username: String
    ): String {
        val user = userRepository.findByUsername(username) ?: return "User not found"
        eventRepository.deleteEvent(user.id.value, UUID.fromString(eventId))
        return "Event deleted."
    }
}
```

---

## 4. ADK Agent Configuration

### 4.1 Coordinator Agent

```kotlin
@Configuration
class AgentConfig {

    @Bean
    fun eventTools(
        eventRepository: EventRepository,
        userRepository: UserRepository
    ) = EventTools(eventRepository, userRepository)

    @Bean
    fun coordinatorAgent(springAI: SpringAI, eventTools: EventTools): LlmAgent {
        return LlmAgent.builder()
            .name("coordinator")
            .model(springAI)
            .instruction("""
                You are the Cirno assistant. You help users manage their events.
                - For event CRUD operations, use the provided tools.
                - For NLP tasks (sentiment, summarization), transfer to nlp-agent.
                - For data analysis, transfer to analytics-agent.
                Always confirm actions with the user.
            """.trimIndent())
            .tools(FunctionTool.fromObject(eventTools))
            .subAgents(nlpAgent(springAI), analyticsAgent(springAI))
            .build()
    }

    @Bean
    fun nlpAgent(springAI: SpringAI): LlmAgent {
        return LlmAgent.builder()
            .name("nlp-agent")
            .model(springAI)
            .instruction("You handle NLP tasks: sentiment analysis, summarization, keyword extraction.")
            .build()
    }

    @Bean
    fun analyticsAgent(springAI: SpringAI): LlmAgent {
        return LlmAgent.builder()
            .name("analytics-agent")
            .model(springAI)
            .instruction("You analyze event data and provide insights.")
            .build()
    }

    @Bean
    fun runner(coordinatorAgent: LlmAgent): Runner {
        return Runner.builder()
            .app(App.builder()
                .name("cirno-ai")
                .rootAgent(coordinatorAgent)
                .build())
            .sessionService(InMemorySessionService())
            .build()
    }
}
```

### 4.2 Chat Controller

```kotlin
@RestController
@RequestMapping("/api/agent")
class AgentController(private val runner: Runner) {

    @PostMapping("/chat")
    fun chat(@RequestBody request: ChatRequest, principal: Principal): ResponseEntity<Flowable<String>> {
        val content = Content.fromText(request.message)
        val events = runner.runAsync(principal.name, request.sessionId, content)
        // Stream responses
        val flow = events.map { it.stringify() }
        return ResponseEntity.ok(flow.toFlowable())
    }
}
```

---

## 5. A2A Integration

### 5.1 Exposing This Backend as an A2A Server

Make Project Cirno discoverable by other agents via the A2A protocol.

**AgentCard producer:**

```kotlin
@Component
class CirnoAgentCardProducer {
    @Bean
    fun agentCard(): AgentCard {
        return AgentCard.builder()
            .name("Cirno Event Manager")
            .description("Manages calendar events with CRUD operations and analytics")
            .url("http://localhost:8080")
            .capabilities(AgentCapabilities.builder()
                .streaming(true)
                .pushNotifications(false)
                .build())
            .skills(listOf(
                AgentSkill.builder()
                    .id("event-crud")
                    .name("Event Management")
                    .description("Create, read, update, delete calendar events")
                    .tags(listOf("events", "calendar"))
                    .inputModes(listOf("text"))
                    .outputModes(listOf("text"))
                    .build(),
                AgentSkill.builder()
                    .id("event-analytics")
                    .name("Event Analytics")
                    .description("Analyze event patterns and provide insights")
                    .tags(listOf("analytics", "data"))
                    .build()
            ))
            .build()
    }
}
```

**AgentExecutor that delegates to ADK:**

```kotlin
@Component
class CirnoAgentExecutor(private val runner: Runner) : AgentExecutor {
    override fun execute(context: RequestContext, eventQueue: EventQueue) {
        val taskUpdater = TaskUpdater(context, eventQueue)
        taskUpdater.submit()
        taskUpdater.startWork()

        val message = context.message()
        val text = (message.parts().first() as? TextPart)?.text ?: "Hello"

        // Run through ADK coordinator
        val response = runner.runAsync("a2a-client", context.task().id(),
            Content.fromText(text))
            .blockingLast()

        taskUpdater.addArtifact(listOf(TextPart(response.stringify())))
        taskUpdater.complete()
    }

    override fun cancel(context: RequestContext, eventQueue: EventQueue) {
        TaskUpdater(context, eventQueue).cancel()
    }
}
```

### 5.2 Connecting to Remote A2A Agents

Wrap external A2A agents (Python, Go, Node.js) as ADK agents:

```kotlin
@Configuration
class RemoteAgentConfig {

    @Bean
    fun pythonNlpAgent(): RemoteA2AAgent {
        val cardUrl = "http://python-nlp-agent:8081/.well-known/agent-card.json"
        val card = A2ACardResolver(JdkA2AHttpClient(), "http://python-nlp-agent:8081", cardUrl)
            .agentCard

        val client = Client.builder(card)
            .withTransport(JSONRPCTransport::class.java, JSONRPCTransportConfig())
            .clientConfig(ClientConfig.builder()
                .setStreaming(card.capabilities().streaming())
                .build())
            .build()

        return RemoteA2AAgent.builder()
            .name("python-nlp-agent")
            .a2aClient(client)
            .agentCard(card)
            .build()
    }
}
```

The coordinator can then include `pythonNlpAgent` as a sub-agent or as an `AgentTool`, enabling transparent delegation to the Python service via A2A.

---

## 6. Dependency Matrix

| Dependency | GroupId | ArtifactId | Version | Purpose |
|---|---|---|---|---|
| Spring AI BOM | `org.springframework.ai` | `spring-ai-bom` | `1.0.0` | *(already added)* |
| Spring AI OpenAI | `org.springframework.ai` | `spring-ai-starter-model-openai` | *(BOM)* | LLM provider |
| ADK Core | `com.google.adk` | `google-adk` | `0.8.0` | Agent framework |
| ADK Spring AI | `com.google.adk` | `google-adk-spring-ai` | `0.8.0` | Spring AI bridge |
| ADK A2A | `com.google.adk` | `google-adk-a2a` | `0.8.0` | RemoteA2AAgent |
| A2A SDK Client | `io.github.a2asdk` | `a2a-java-sdk-client` | *TBD* | A2A client API |
| A2A JSON-RPC | `io.github.a2asdk` | `a2a-java-sdk-client-transport-jsonrpc` | *TBD* | JSON-RPC transport |
| A2A Server | `io.github.a2asdk` | `a2a-java-sdk-server-common` | *TBD* | A2A server framework |

---

## 7. Implementation Phases

### Phase 1: Spring AI Tool Calling

**Goal:** Expose backend operations as LLM-callable tools.

- [ ] Add `spring-ai-starter-model-openai` dependency.
- [ ] Configure `ChatModel` bean with API key in `application.yaml`.
- [ ] Create `EventTools` class with `@Tool`-annotated methods.
- [ ] Create `/api/agent/chat` endpoint using `ChatClient` with tools.
- [ ] Test tool calling end-to-end with a simple event query.

### Phase 2: ADK Coordinator Agent

**Goal:** Build the multi-agent hierarchy with task delegation.

- [ ] Add `google-adk`, `google-adk-spring-ai` dependencies.
- [ ] Create coordinator `LlmAgent` using `SpringAI` model.
- [ ] Register `EventTools` as `FunctionTool` instances.
- [ ] Create sub-agents: `EventAgent`, `QueryAgent`.
- [ ] Wire sub-agents into coordinator via `.subAgents()`.
- [ ] Create `AgentController` exposing `/api/agent/chat` with streaming.
- [ ] Test agent transfer (coordinator → sub-agent → response).

### Phase 3: A2A Server Capability

**Goal:** Expose this backend as an A2A-compliant agent.

- [ ] Add `a2a-java-sdk-server-common` dependency.
- [ ] Implement `AgentCardProducer` describing Cirno's skills.
- [ ] Implement `AgentExecutor` delegating to ADK runner.
- [ ] Serve `/.well-known/agent-card.json` endpoint.
- [ ] Test with an external A2A client (e.g., Python SDK).

### Phase 4: A2A Client + Remote Agents

**Goal:** Connect to external agents via A2A protocol.

- [ ] Add `a2a-java-sdk-client`, `a2a-java-sdk-client-transport-jsonrpc` dependencies.
- [ ] Create `RemoteA2AAgent` wrappers for external agent URLs.
- [ ] Include remote agents as sub-agents in coordinator.
- [ ] Test cross-language agent delegation (e.g., Python NLP agent).

### Phase 5: WebSocket Streaming

**Goal:** Real-time multi-turn conversations.

- [ ] Configure WebSocket endpoint using existing `spring-boot-starter-websocket`.
- [ ] Stream ADK `Flowable<Event>` responses to WebSocket clients.
- [ ] Support session persistence across WebSocket connections.

---

## 8. Configuration Reference

### `application.yaml` additions

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
          max-tokens: 2048

  # A2A server configuration
  a2a:
    agent:
      name: "Cirno Event Manager"
      url: "http://localhost:8080"
      skills:
        - id: event-crud
          name: Event Management
          tags: [events, calendar]
        - id: event-analytics
          name: Event Analytics
          tags: [analytics, data]

  # Remote A2A agents to connect to
  agents:
    remote:
      - name: python-nlp-agent
        url: http://python-nlp-agent:8081
        transport: jsonrpc
      - name: go-ml-agent
        url: http://go-ml-agent:8082
        transport: jsonrpc
```

---

## 9. Risks and Considerations

| Risk | Mitigation |
|---|---|
| ADK is Java-first, not Kotlin-first | Kotlin compiles to JVM; ADK classes work via standard interop. Use Java-style builders. |
| A2A Java SDK maturity | SDK is actively developed; use JSON-RPC transport (most stable). Fallback: implement A2A JSON-RPC manually. |
| Spring AI + ADK version coupling | Both depend on Spring Boot 4.x. Pin versions via BOMs. |
| Tool calling latency | Use streaming (`Flowable`) for long-running tool calls. |
| State management | ADK's `InMemorySessionService` for dev; `FirestoreSessionService` for production. |
| Security | A2A agents should authenticate via OAuth 2.0 / API keys. AgentCard supports `securitySchemes`. |

---

## 10. Reference Links

| Resource | URL |
|---|---|
| Spring AI Documentation | https://docs.spring.io/spring-ai/reference/ |
| ADK Java Repository | https://github.com/google/adk-java |
| A2A Protocol Specification | https://github.com/google/A2A |
| A2A Java SDK | https://github.com/a2aproject/a2a-java |
| A2A Python SDK | `pip install a2a-sdk` |
| A2A Go SDK | `go get github.com/a2aproject/a2a-go` |
| A2A JavaScript SDK | `npm install @a2a-js/sdk` |
| A2A .NET SDK | `dotnet add package A2A` |
