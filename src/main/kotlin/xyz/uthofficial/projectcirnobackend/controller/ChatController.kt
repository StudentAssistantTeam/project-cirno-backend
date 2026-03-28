package xyz.uthofficial.projectcirnobackend.controller

import jakarta.validation.Valid
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import xyz.uthofficial.projectcirnobackend.dto.*
import xyz.uthofficial.projectcirnobackend.repository.EventRepository
import xyz.uthofficial.projectcirnobackend.repository.UserRepository
import xyz.uthofficial.projectcirnobackend.service.ChatSessionService
import xyz.uthofficial.projectcirnobackend.service.EventTools
import java.security.Principal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@RestController
@RequestMapping("/api/agent")
class ChatController(
    private val chatModel: ChatModel,
    private val chatSessionService: ChatSessionService,
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository
) {

    private fun buildSystemPrompt(): SystemMessage {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        return SystemMessage(
            """
            You are Cirno, a helpful calendar assistant. You help users manage their events.
            You can create, list, update, and delete calendar events using the provided tools.
            Be concise and confirm actions clearly.
            Today's date is $today.
            """.trimIndent()
        )
    }

    private fun resolveUserId(principal: Principal): UUID {
        val user = userRepository.findByUsername(principal.name)
            ?: throw IllegalArgumentException("User not found")
        return user.id.value
    }

    @PostMapping("/chat", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chat(
        @Valid @RequestBody request: ChatRequest,
        principal: Principal
    ): Flux<ServerSentEvent<String>> {
        logger.info("=== CHAT REQUEST === user=${principal.name} sessionId=${request.sessionId} message=${request.message.take(100)}")

        val userId = resolveUserId(principal)
        val parsedSessionId = request.sessionId?.let { UUID.fromString(it) }
        val (sessionId, history) = chatSessionService.getOrCreateSession(parsedSessionId, userId)

        logger.info("Session: $sessionId — history size: ${history.size}")

        val eventTools = EventTools(eventRepository, userRepository, principal.name)

        val messages: MutableList<Message> = mutableListOf(buildSystemPrompt())
        messages.addAll(history)

        chatSessionService.appendUserMessage(sessionId, request.message)

        val responseBuilder = StringBuilder()

        return ChatClient.create(chatModel)
            .prompt(Prompt(messages))
            .user(request.message)
            .tools(eventTools)
            .stream()
            .content()
            .doOnNext { chunk -> responseBuilder.append(chunk) }
            .map { chunk ->
                ServerSentEvent.builder("""{"type":"text","content":"${escapeJson(chunk)}"}""")
                    .build()
            }
            .startWith(
                ServerSentEvent.builder("""{"type":"session","content":"$sessionId"}""")
                    .build()
            )
            .concatWith(
                Mono.just(
                    ServerSentEvent.builder("""{"type":"done"}""").build()
                )
            )
            .doOnComplete {
                if (responseBuilder.isNotEmpty()) {
                    chatSessionService.appendAssistantMessage(sessionId, responseBuilder.toString())
                    logger.info("=== RESPONSE (${responseBuilder.length} chars): ${responseBuilder.take(200)}")
                }
            }
            .onErrorResume { error ->
                logger.error("=== CHAT ERROR === ${error.javaClass.name}: ${error.message}", error)
                val errorMessage = if (error.message != null) escapeJson(error.message!!) else "An unexpected error occurred"
                Flux.just(
                    ServerSentEvent.builder("""{"type":"error","content":"$errorMessage"}""").build(),
                    ServerSentEvent.builder("""{"type":"done"}""").build()
                )
            }
    }

    @PostMapping("/sessions")
    fun createSession(principal: Principal): Map<String, String> {
        val userId = resolveUserId(principal)
        val (sessionId, _) = chatSessionService.getOrCreateSession(null, userId)
        logger.info("Created session $sessionId for user=${principal.name}")
        return mapOf("sessionId" to sessionId.toString())
    }

    @GetMapping("/sessions")
    fun listSessions(principal: Principal): ChatSessionListResponse {
        val userId = resolveUserId(principal)
        return ChatSessionListResponse(chatSessionService.getSessionsByUser(userId))
    }

    @GetMapping("/sessions/{id}")
    fun getSessionHistory(@PathVariable id: String, principal: Principal): ChatHistoryResponse {
        val sessionId = UUID.fromString(id)
        val userId = resolveUserId(principal)
        return ChatHistoryResponse(sessionId, chatSessionService.getHistory(sessionId))
    }

    @DeleteMapping("/sessions/{id}")
    fun deleteSession(@PathVariable id: String, principal: Principal) {
        val sessionId = UUID.fromString(id)
        val userId = resolveUserId(principal)
        chatSessionService.deleteSession(sessionId, userId)
    }

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(ChatController::class.java)
    }

    private fun escapeJson(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
