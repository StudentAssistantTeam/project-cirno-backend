# Multi-Agent Architecture: Spring AI + ADK + A2A

## Executive Summary

This document outlines a plan to evolve Project Cirno Backend into a multi-language, multi-agent system using three complementary technologies:

- **Spring AI 2.0.0-M4** -- LLM integration and tool calling within the existing Spring Boot application (milestone release, already in `build.gradle.kts`).
- **ADK Java 1.0.0-rc.1** -- Agent framework providing hierarchy, sub-agent delegation, and the coordinator pattern (release candidate on Maven Central).
- **A2A Protocol (v0.3.0)** -- Open standard (donated to Linux Foundation by Google) enabling cross-language agent communication. Both Java (`1.0.0.Alpha3`) and Python SDKs implement protocol version `0.3.0`.

The resulting system will feature a central coordinator agent that speaks directly to users, delegates tasks to specialized sub-agents, and communicates with external agents built in Python, Go, Node.js, or .NET via the A2A protocol.

---

## 1. Technology Overview

### 1.1 Spring AI (2.0.0-M4)

Spring AI is the official Spring project for AI/LLM integration. It provides a framework-agnostic abstraction over multiple model providers with deep Spring Boot integration. The project currently uses milestone release `2.0.0-M4`.

**Key capabilities for this project:**

| Capability | Description |
|---|---|
| `ChatModel` abstraction | Uniform API across OpenAI, Anthropic, Ollama, Azure, Vertex AI, etc. |
| `@Tool` annotation | Expose Java/Kotlin methods as callable tools for the LLM. Spring AI handles schema generation, invocation, and result routing automatically. |
| `@ToolParam` annotation | Describe method parameters with `description` and `required` attributes for the LLM's tool schema. |
| `ToolCallingManager` | Auto-configured lifecycle manager that intercepts tool calls, executes them, and feeds results back to the model. |
| Spring Boot starters | `spring-ai-starter-model-openai`, `spring-ai-starter-model-ollama`, etc. |
| BOM-managed versions | `spring-ai-bom:2.0.0-M4` (already in `build.gradle.kts:119`). |

**Tool calling flow (5 steps):**

1. Tool definitions are included in the chat request to the LLM.
2. The LLM responds with a tool call (name + arguments).
3. Spring AI's `ToolCallingManager` executes the matching `@Tool` method.
4. The tool's return value is sent back to the LLM.
5. The LLM produces the final response using the tool's output.

**Two approaches to register tools:**

- **Declarative:** Annotate methods with `@Tool(description = "...")`, pass the object instance to `ChatClient.prompt().tools(new EventTools())`.
- **Functional:** Define `Function<Request, Response>` beans annotated with `@Description`, reference by bean name via `ChatClient.prompt().toolNames("createEvent")`.

**Current project status:** The Spring AI BOM (`2.0.0-M4`) and `spring-ai-starter-model-openai` are already in `build.gradle.kts`. The `ChatModel` bean is configured in `application.yaml` pointing to the Xiaomi Mimo API (`mimo-v2-flash`). `@Tool`-annotated classes (`EventTools`, `UserIdentityTools`) already exist. Phase 1 is **complete**.

---

### 1.2 ADK Java (1.0.0-rc.1)

The Agent Development Kit (ADK) for Java is Google's code-first toolkit for building, evaluating, and deploying AI agents. It provides structured agent hierarchy, tool integration, session management, and execution orchestration. The latest release on Maven Central is `1.0.0-rc.1` (verified 2026-03-29).

**Maven coordinates:**

| Module | Artifact ID | Version | Purpose |
|---|---|---|---|
| Core | `com.google.adk:google-adk` | `1.0.0-rc.1` | Agent framework, runtime, tools, LLM integrations |
| Spring AI Bridge | `com.google.adk:google-adk-spring-ai` | `1.0.0-rc.1` | Wraps Spring AI `ChatModel` as ADK's `BaseLlm` |
| A2A Bridge | `com.google.adk:google-adk-a2a` | `1.0.0-rc.1` | `RemoteA2AAgent` + type converters |
| Dev Server | `com.google.adk:google-adk-dev` | `1.0.0-rc.1` | Browser UI for local testing (optional) |

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
- Maps ADK `GenerateContentConfig` → Spring AI `ChatOptions` via `ConfigMapper`.

**Auto-configuration behavior:** When a `ChatModel` bean is available (it is — configured via `application.yaml`), the module automatically creates a `SpringAI` bean. If both `ChatModel` and `StreamingChatModel` are present, a `SpringAI` bean is configured from both. An `EmbeddingModel` bean triggers a `SpringAIEmbedding` bean. Configuration properties are available under `adk.spring-ai.*` (e.g., `default-model`, `temperature`, `max-tokens`).

```kotlin
// The SpringAI bean is auto-configured — inject it directly
@Bean
fun coordinatorAgent(springAI: SpringAI): LlmAgent {
    return LlmAgent.builder()
        .name("coordinator")
        .model(springAI)
        .instruction("You are a helpful assistant.")
        .build()
}
```

---

### 1.3 A2A Protocol (v0.3.0)

The Agent2Agent (A2A) Protocol is an open standard for enabling communication and interoperability between independent AI agent systems. Originally developed by Google and donated to the Linux Foundation. Both the Java and Python SDKs currently implement **protocol version `0.3.0`**.

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

The A2A Java SDK is a multi-module Maven project implementing the A2A protocol (v0.3.0) for Java applications. Current version on Maven Central is `1.0.0.Alpha3` (verified 2026-03-29).

**Maven coordinates:** `io.github.a2asdk:*`

**Key modules:**

| Module | Purpose | Notes |
|---|---|---|
| `a2a-java-sdk-spec` | Protocol definitions and data types | Core types: `AgentCard`, `Message`, `Task`, `TextPart` |
| `a2a-java-sdk-client` | Client API for communicating with A2A servers | `A2ACardResolver`, `Client.builder()` |
| `a2a-java-sdk-client-transport-jsonrpc` | JSON-RPC transport for clients | **Recommended** for cross-language interop |
| `a2a-java-sdk-client-transport-grpc` | gRPC transport for clients | Binary protocol, requires Protobuf |
| `a2a-java-sdk-client-transport-rest` | REST transport for clients | HTTP + JSON |
| `a2a-java-sdk-server-common` | Server framework (`AgentExecutor`, `TaskUpdater`) | **Framework-agnostic** — works with Spring Boot |
| `a2a-java-sdk-reference-jsonrpc` | Quarkus-based JSON-RPC server | **Do not use directly** — Quarkus CDI dependency |

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

The existing `EventRepository` and `UserRepository` are exposed as `@Tool`-annotated methods. The current `ChatController` uses these tools directly via `ChatClient.prompt().tools(eventTools, userIdentityTools)`. When migrating to ADK, these same tools will be wrapped as `FunctionTool` instances.

### 3.1 Existing Tool Definitions

**`EventTools` (`src/main/kotlin/.../service/EventTools.kt`):**
- `createEvent(name, datetime, description, tags)` — creates a calendar event
- `listEvents(start, lengthMonths)` — lists events in a date range
- `updateEvent(eventId, name, datetime, description, tags)` — updates an event
- `deleteEvent(eventId)` — deletes an event

**`UserIdentityTools` (`src/main/kotlin/.../service/UserIdentityTools.kt`):**
- `createUserIdentity(identity, goal)` — sets user's academic profile
- `updateUserIdentity(identity, goal)` — updates user's profile

Both classes accept `currentUsername: String` in the constructor (resolved from `Principal`), avoiding the need to pass username as a tool parameter.

### 3.2 Tool Definitions (actual code)

```kotlin
// EventTools.kt — already implemented
class EventTools(
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository,
    private val currentUsername: String
) {
    @Tool(description = "Create a new calendar event for the current user")
    fun createEvent(
        @ToolParam(description = "Event name", required = true) name: String,
        @ToolParam(description = "ISO 8601 datetime", required = true) datetime: String,
        @ToolParam(description = "Event description") description: String? = null,
        @ToolParam(description = "Comma-separated tags") tags: String? = null
    ): String { /* ... */ }

    @Tool(description = "List calendar events for the current user within a time range")
    fun listEvents(
        @ToolParam(description = "Start date in ISO 8601 format", required = true) start: String,
        @ToolParam(description = "Number of months to query", required = true) lengthMonths: Int
    ): String { /* ... */ }

    @Tool(description = "Update an existing calendar event")
    fun updateEvent(
        @ToolParam(description = "Event UUID", required = true) eventId: String,
        @ToolParam(description = "New event name", required = true) name: String,
        @ToolParam(description = "New ISO 8601 datetime", required = true) datetime: String,
        @ToolParam(description = "New description") description: String? = null,
        @ToolParam(description = "New comma-separated tags") tags: String? = null
    ): String { /* ... */ }

    @Tool(description = "Delete a calendar event by its ID")
    fun deleteEvent(
        @ToolParam(description = "Event UUID", required = true) eventId: String
    ): String { /* ... */ }
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

### 5.1 Exposing This Backend as an A2A Server (Spring Boot Approach)

Make Project Cirno discoverable by other agents via the A2A protocol.

> **Important:** The A2A Java SDK's reference implementations (`a2a-java-sdk-reference-jsonrpc`, etc.) are built on **Quarkus** with CDI and cannot be used directly in a Spring Boot project. Instead, depend on `a2a-java-sdk-server-common` (which is framework-agnostic) and implement the HTTP endpoints yourself via Spring `@RestController`.

**AgentCard as a Spring Bean:**

```kotlin
@Configuration
class A2AConfig {

    @Bean
    fun cirnoAgentCard(): AgentCard {
        return AgentCard.builder()
            .name("Cirno Event Manager")
            .description("Manages calendar events with CRUD operations and analytics")
            .url("http://localhost:8080")
            .protocolVersion("0.3.0")
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

**AgentCard endpoint + JSON-RPC endpoint (Spring Boot `@RestController`):**

```kotlin
@RestController
class A2AServerController(
    private val agentCard: AgentCard,
    private val agentExecutor: CirnoAgentExecutor
) {
    @GetMapping("/.well-known/agent-card.json")
    fun getAgentCard(): AgentCard = agentCard

    @PostMapping("/a2a/jsonrpc", consumes = ["application/json"], produces = ["application/json"])
    fun handleJsonRpc(@RequestBody body: String): ResponseEntity<String> {
        // Parse JSON-RPC request, delegate to agentExecutor
        // This is where you bridge A2A protocol ↔ AgentExecutor
        // Implementation depends on the specific JSON-RPC method (sendMessage, getTask, etc.)
        TODO("Implement JSON-RPC dispatching using a2a-java-sdk-server-common types")
    }
}
```

**AgentExecutor that delegates to ADK:**

```kotlin
@Component
class CirnoAgentExecutor(private val runner: Runner) : AgentExecutor {
    override fun execute(context: RequestContext, eventQueue: EventQueue) {
        val taskUpdater = TaskUpdater(context, eventQueue)

        if (context.task() == null) {
            taskUpdater.submit()
        }
        taskUpdater.startWork()

        val message = context.message()
        val text = message.parts().filterIsInstance<TextPart>().firstOrNull()?.text ?: "Hello"

        // Run through ADK coordinator
        val response = runner.runAsync("a2a-client", context.task().id(),
            Content.fromText(text))
            .blockingLast()

        taskUpdater.addArtifact(listOf(TextPart(response.stringify())), null, null, null)
        taskUpdater.complete()
    }

    override fun cancel(context: RequestContext, eventQueue: EventQueue) {
        val task = context.task()
        if (task.status().state() == TaskState.CANCELED || task.status().state() == TaskState.COMPLETED) {
            throw TaskNotCancelableError()
        }
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

### 5.3 Cross-Language Interoperability (Python)

The A2A protocol is designed for cross-language communication. Both Java and Python SDKs implement **protocol version `0.3.0`** and participate in a shared **Test Compatibility Kit (TCK)** that validates interoperability across JSON-RPC, gRPC, and REST transports.

**What this means:** A Java A2A client can talk to a Python A2A server (and vice versa) without any custom translation layer. The wire protocol is the contract.

#### Compatibility Matrix

| Java (Kotlin) Role | Python Role | Transport | Status |
|---|---|---|---|
| A2A Client (`a2a-java-sdk-client`) | A2A Server (`a2a-sdk` + FastAPI) | JSON-RPC 2.0 over HTTP | **Verified via TCK** |
| A2A Client | A2A Server | gRPC (Protobuf) | **Verified via TCK** |
| A2A Client | A2A Server | REST (HTTP+JSON) | **Verified via TCK** |
| ADK `RemoteA2AAgent` | A2A Server (`a2a-sdk`) | JSON-RPC 2.0 over HTTP | **Supported** (uses `a2a-java-sdk-client` internally) |
| A2A Server (`server-common`) | A2A Client (`a2a-sdk`) | JSON-RPC 2.0 over HTTP | **Requires custom Spring Boot endpoint** |

#### Key Considerations for Python Interop

| Concern | Detail |
|---|---|
| **Protocol version** | Both SDKs implement `0.3.0`. Set `protocolVersion("0.3.0")` in your `AgentCard`. |
| **AgentCard discovery** | Python server exposes `GET /.well-known/agent-card.json`. Java client uses `A2ACardResolver` to fetch it. |
| **JSON-RPC is safest** | Pure JSON over HTTP — language-agnostic. Both SDKs have mature JSON-RPC support. Prefer it over gRPC for cross-language. |
| **Async model** | Python SDK is `asyncio`-based. Java SDK is RxJava-based. Both translate to the same wire format transparently. |
| **Streaming** | JSON-RPC uses Server-Sent Events (SSE). Both support it. Check the Python agent's `AgentCard.capabilities.streaming`. |
| **Task state machine** | Both follow `SUBMITTED → WORKING → COMPLETED`. `TaskUpdater` in Java maps to `TaskUpdater` in Python. |
| **TextPart works universally** | `TextPart` is the safest cross-language content type. `FilePart` and `DataPart` may have minor serialization differences — test if used. |
| **Security** | Both support OAuth 2.0 / API keys via `security`/`security_schemes` in `AgentCard`. Coordinate auth schemes between Java and Python agents. |
| **Maturity** | Both SDKs are pre-1.0 (`1.0.0-rc.1` and `1.0.0.Alpha3`). Expect minor API changes. JSON-RPC is the most stable transport. |

#### Python A2A Server Example (for reference)

The Python side uses `a2a-sdk` (from `a2aproject/a2a-python`):

```python
# pip install a2a-sdk

from a2a.server.agent_execution import AgentExecutor, RequestContext
from a2a.server.events import EventQueue
from a2a.server.tasks import TaskUpdater, InMemoryTaskStore
from a2a.server.request_handlers import DefaultRequestHandler
from a2a.server.apps import A2AFastAPIApplication
from a2a.types import AgentCard, AgentCapabilities, AgentSkill
from a2a.utils import new_agent_text_message, new_task

class NlpAgentExecutor(AgentExecutor):
    async def execute(self, context: RequestContext, event_queue: EventQueue) -> None:
        updater = TaskUpdater(event_queue, context.task or new_task(context.message))
        updater.submit()
        updater.start_work()

        text = context.message.parts[0].root.text
        # ... your NLP logic here ...
        response = new_agent_text_message(f"Sentiment: positive ({text})", context.context_id, updater.task.id)
        await updater.add_artifact([response.parts[0].root], None, None, None)
        await updater.complete()

# Build server
card = AgentCard(
    name="Python NLP Agent",
    description="Performs NLP tasks",
    url="http://localhost:8000",
    protocol_version="0.3.0",
    default_input_modes=["text/plain"],
    default_output_modes=["text/plain"],
    capabilities=AgentCapabilities(streaming=False),
    skills=[AgentSkill(id="nlp", name="NLP", tags=["nlp", "sentiment"])]
)
handler = DefaultRequestHandler(NlpAgentExecutor(), InMemoryTaskStore())
app = A2AFastAPIApplication(card, handler).build()
```

#### Architecture Diagram: Java Client → Python Server

```
┌─────────────────────────────────────────┐
│  Cirno Backend (Kotlin) — A2A Client    │
│                                         │
│  RemoteA2AAgent("python-nlp")           │
│    └── io.a2a.client.Client             │
│          └── JSONRPCTransport           │
│                └── HTTP POST            │
└──────────────────┬──────────────────────┘
                   │ JSON-RPC 2.0 over HTTP
                   │ (protocol version 0.3.0)
                   ▼
┌─────────────────────────────────────────┐
│  Python NLP Agent — A2A Server          │
│                                         │
│  A2AFastAPIApplication                  │
│    └── DefaultRequestHandler            │
│          └── NlpAgentExecutor           │
│                └── TaskUpdater          │
└─────────────────────────────────────────┘
```

---

## 6. Dependency Matrix

| Dependency | GroupId | ArtifactId | Version | Purpose | Status |
|---|---|---|---|---|---|
| Spring AI BOM | `org.springframework.ai` | `spring-ai-bom` | `2.0.0-M4` | Dependency management for Spring AI | **Already in `build.gradle.kts`** |
| Spring AI OpenAI | `org.springframework.ai` | `spring-ai-starter-model-openai` | *(BOM-managed)* | LLM provider (Mimo API) | **Already in `build.gradle.kts`** |
| Spring Boot | `org.springframework.boot` | `spring-boot-dependencies` | `4.0.5` | Core framework | **Already in `build.gradle.kts`** |
| ADK Core | `com.google.adk` | `google-adk` | `0.8.0` | Agent framework (runner, agents, tools) | To add |
| ADK Spring AI | `com.google.adk` | `google-adk-spring-ai` | `0.8.0` | Spring AI `ChatModel` → ADK `BaseLlm` bridge | To add |
| ADK A2A | `com.google.adk` | `google-adk-a2a` | `0.8.0` | `RemoteA2AAgent` + type converters | To add |
| A2A SDK Client | `io.github.a2asdk` | `a2a-java-sdk-client` | `1.0.0.Alpha3` | A2A client API | To add |
| A2A Client Transport JSON-RPC | `io.github.a2asdk` | `a2a-java-sdk-client-transport-jsonrpc` | `1.0.0.Alpha3` | JSON-RPC 2.0 transport for client | To add |
| A2A Client Transport gRPC | `io.github.a2asdk` | `a2a-java-sdk-client-transport-grpc` | `1.0.0.Alpha3` | gRPC transport for client | Optional |
| A2A Client Transport REST | `io.github.a2asdk` | `a2a-java-sdk-client-transport-rest` | `1.0.0.Alpha3` | REST transport for client | Optional |
| A2A Server Common | `io.github.a2asdk` | `a2a-java-sdk-server-common` | `1.0.0.Alpha3` | Server-side types (`AgentExecutor`, `TaskUpdater`) | To add |

> **Note on compatibility:** ADK `0.8.0` uses Spring Boot `4.0.2` internally. This project uses Spring Boot `4.0.5` and Spring AI `2.0.0-M4` (milestone). The ADK Spring AI bridge was likely built against a stable Spring AI version — test integration early. See [Section 9](#9-risks-and-considerations) for details.

---

## 7. Implementation Phases

### Phase 1: Spring AI Tool Calling ✅ DONE

**Goal:** Expose backend operations as LLM-callable tools.

- [x] Add `spring-ai-starter-model-openai` dependency. *(in `build.gradle.kts`)*
- [x] Configure `ChatModel` bean with API key in `application.yaml`. *(Mimo API at `api.xiaomimimo.com`)*
- [x] Create `EventTools` class with `@Tool`-annotated methods. *(`EventTools.kt` + `UserIdentityTools.kt`)*
- [x] Create `/api/agent/chat` endpoint using `ChatClient` with tools. *(`ChatController.kt`)*
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

> **Note:** A2A reference implementations (`a2a-java-sdk-reference-jsonrpc`, etc.) are built on **Quarkus** with CDI and cannot be used directly. Use `a2a-java-sdk-server-common` and implement Spring `@RestController` endpoints for AgentCard discovery and JSON-RPC dispatching.

- [ ] Add `a2a-java-sdk-server-common` dependency.
- [ ] Implement `AgentCard` as a Spring `@Bean` describing Cirno's skills.
- [ ] Implement `AgentExecutor` delegating to ADK `Runner`.
- [ ] Create `@RestController` for `GET /.well-known/agent-card.json`.
- [ ] Create `@RestController` for `POST /a2a/jsonrpc` (JSON-RPC dispatching).
- [ ] Test with an external A2A client (e.g., Python SDK `a2a-sdk`).

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

### Current `application.yaml` (already configured)

```yaml
spring:
  application:
    name: project-cirno-backend
  datasource:
    url: jdbc:sqlite:cirno.db
    driver-class-name: org.sqlite.JDBC

  ai:
    openai:
      api-key: ${MIMO_API_KEY:placeholder}
      base-url: https://api.xiaomimimo.com
      chat:
        options:
          model: mimo-v2-flash
          max-tokens: 2048

app:
  jwt:
    secret: ...
    expiration-ms: 86400000
```

### Future `application.yaml` additions (for ADK + A2A)

```yaml
adk:
  spring-ai:
    # Auto-configured when ChatModel bean exists
    # SpringAI bean is auto-created from the ChatModel

  # A2A server configuration (programmatic, not auto-config)
  # → Define AgentCard as a @Bean in A2AConfig.kt

# Remote A2A agents to connect to
# → Configure via @Bean in RemoteAgentConfig.kt
# Example:
#   python-nlp-agent: http://python-nlp-agent:8081
#   go-ml-agent: http://go-ml-agent:8082
```

> **Note:** ADK Spring Boot auto-configuration reads properties under `adk.spring-ai.*`. When a `ChatModel` bean is present (as configured above), a `SpringAI` bean is automatically created. A2A agent cards and remote agent connections are configured programmatically via Spring `@Bean` definitions, not YAML.

---

## 9. Risks and Considerations

| Risk | Detail | Mitigation |
|---|---|---|
| **Spring AI BOM version mismatch** | Project uses `spring-ai-bom:2.0.0-M4` (milestone). ADK's `google-adk-spring-ai` was built against a stable Spring AI version. | Test ADK + Spring AI integration early. If incompatible, upgrade to a stable Spring AI release when available, or fork ADK's Spring AI module. |
| **Spring Boot version drift** | ADK uses Spring Boot `4.0.2` internally. Project uses `4.0.5`. Minor patch difference — likely safe but not guaranteed. | Monitor for runtime conflicts. Pin transitive ADK dependencies if needed. |
| **A2A SDK pre-release maturity** | A2A Java SDK is `1.0.0.Alpha3`. API surface may change before GA. | Use JSON-RPC transport (most stable). Pin version. Monitor `a2aproject/a2a-java` releases. |
| **ADK version pre-release** | ADK Java is `0.8.0`. API may evolve. | Pin to `0.8.0`. Follow `google/adk-java` releases for breaking changes. |
| **Quarkus-incompatible A2A references** | A2A reference server implementations are Quarkus-based (CDI). Cannot drop into Spring Boot. | Use `a2a-java-sdk-server-common` only. Implement Spring `@RestController` endpoints manually (see Section 5.1). |
| **ADK is Java-first, not Kotlin-first** | Kotlin compiles to JVM; ADK classes work via standard interop. Use Java-style builders. | No mitigation needed — Kotlin interop is seamless for builder-pattern APIs. |
| **A2A JSON-RPC dispatching complexity** | Must manually parse JSON-RPC 2.0 requests and dispatch to `AgentExecutor`. | Use Jackson for JSON parsing. Study `a2a-java-sdk-reference-jsonrpc` source for dispatch logic. |
| **Tool calling latency** | Long-running tool calls block the LLM loop. | Use Spring AI's streaming (`Flux`) and ADK's `Flowable<Event>` for async responses. |
| **State management** | ADK's `InMemorySessionService` is single-JVM only. | Use `InMemorySessionService` for dev. For production, consider `FirestoreSessionService` or custom `BaseSessionService` backed by the project's Exposed/SQLite layer. |
| **Security** | A2A agents must authenticate. AgentCard supports `securitySchemes`. | Implement OAuth 2.0 or API key auth on A2A endpoints. Coordinate auth between Java and Python agents. |

---

## 10. Reference Links

| Resource | URL |
|---|---|
| Spring AI Documentation | https://docs.spring.io/spring-ai/reference/ |
| ADK Java Repository | https://github.com/google/adk-java |
| ADK Java Wiki (Architecture, Getting Started) | https://github.com/google/adk-java/wiki |
| A2A Protocol Specification | https://github.com/google/A2A |
| A2A Protocol Version 0.3.0 | https://github.com/google/A2A/blob/main/specification/versions/0.3.0 |
| A2A Java SDK | https://github.com/a2aproject/a2a-java |
| A2A Python SDK | https://github.com/a2aproject/a2a-python |
| A2A Python SDK PyPI | `pip install a2a-sdk` |
| A2A Test Compatibility Kit (TCK) | https://github.com/a2aproject/a2a-java/tree/main/tck |
| A2A Go SDK | `go get github.com/a2aproject/a2a-go` |
| A2A JavaScript SDK | `npm install @a2a-js/sdk` |
| A2A .NET SDK | `dotnet add package A2A` |
| Spring Boot Reference | https://docs.spring.io/spring-boot/reference/ |
