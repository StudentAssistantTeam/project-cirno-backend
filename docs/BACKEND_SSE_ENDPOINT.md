# Backend SSE Endpoint: `/api/agent/chat`

## Overview

The backend exposes a `POST /api/agent/chat` endpoint that returns Server-Sent Events (SSE) for the frontend to consume. The coordinator agent (ADK + Spring AI) processes the user's message and streams responses incrementally.

## Endpoint Contract

| Field | Type | Description |
|---|---|---|
| **URL** | `POST /api/agent/chat` | |
| **Auth** | `Authorization: Bearer <token>` | Same JWT as existing endpoints |
| **Request** | `{ "message": "string", "sessionId": "string" }` | `sessionId` is a UUID, client-generated |
| **Response** | `text/event-stream` | Each event is `data: {json}\n\n` |
| **Event types** | `text`, `tool_call`, `agent_transfer`, `done` | `done` signals end of stream |

## SSE Response Format

```
data: {"type":"text","content":"I've created your event 'Team meeting' for March 30th."}

data: {"type":"tool_call","content":"createEvent(name=Team meeting, datetime=2026-03-30T14:00)"}

data: {"type":"agent_transfer","content":"Delegating to nlp-agent..."}

data: {"type":"done"}
```

## Spring AI Tool Calling

The existing `EventRepository` and `UserRepository` are exposed as `@Tool`-annotated methods. The coordinator agent calls these tools to interact with the backend.

### Tool Definitions

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

## ADK Agent Configuration

### Coordinator Agent

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

### SSE Controller

```kotlin
@RestController
@RequestMapping("/api/agent")
class AgentController(private val runner: Runner) {

    @PostMapping("/chat", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chat(@RequestBody request: ChatRequest, principal: Principal): Flux<ServerSentEvent<String>> {
        val content = Content.fromText(request.message)
        return runner.runAsync(principal.name, request.sessionId, content)
            .map { event ->
                ServerSentEvent.builder<String>()
                    .event(event.type().name)
                    .data(event.stringify())
                    .build()
            }
            .toFlux()
    }
}
```

## A2A Server Capability

Make this backend discoverable by other agents via the A2A protocol.

### AgentCard Producer

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

### AgentExecutor (delegates to ADK)

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

## A2A Client + Remote Agents

Connect to external agents (Python, Go, Node.js) via A2A protocol:

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

## Dependency Matrix

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

## Configuration

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

## Implementation Phases

### Phase 1: Spring AI Tool Calling
- [ ] Add `spring-ai-starter-model-openai` dependency
- [ ] Configure `ChatModel` bean with API key in `application.yaml`
- [ ] Create `EventTools` class with `@Tool`-annotated methods
- [ ] Create `/api/agent/chat` endpoint using `ChatClient` with tools
- [ ] Test tool calling end-to-end with a simple event query

### Phase 2: ADK Coordinator Agent
- [ ] Add `google-adk`, `google-adk-spring-ai` dependencies
- [ ] Create coordinator `LlmAgent` using `SpringAI` model
- [ ] Register `EventTools` as `FunctionTool` instances
- [ ] Create sub-agents: `EventAgent`, `QueryAgent`
- [ ] Wire sub-agents into coordinator via `.subAgents()`
- [ ] Create `AgentController` exposing `/api/agent/chat` with streaming
- [ ] Test agent transfer (coordinator → sub-agent → response)

### Phase 3: A2A Server Capability
- [ ] Add `a2a-java-sdk-server-common` dependency
- [ ] Implement `AgentCardProducer` describing Cirno's skills
- [ ] Implement `AgentExecutor` delegating to ADK runner
- [ ] Serve `/.well-known/agent-card.json` endpoint
- [ ] Test with an external A2A client (e.g., Python SDK)

### Phase 4: A2A Client + Remote Agents
- [ ] Add `a2a-java-sdk-client`, `a2a-java-sdk-client-transport-jsonrpc` dependencies
- [ ] Create `RemoteA2AAgent` wrappers for external agent URLs
- [ ] Include remote agents as sub-agents in coordinator
- [ ] Test cross-language agent delegation (e.g., Python NLP agent)

### Phase 5: WebSocket Streaming
- [ ] Configure WebSocket endpoint using existing `spring-boot-starter-websocket`
- [ ] Stream ADK `Flowable<Event>` responses to WebSocket clients
- [ ] Support session persistence across WebSocket connections
