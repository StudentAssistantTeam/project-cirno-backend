package xyz.uthofficial.projectcirnobackend.controller

import jakarta.validation.Valid
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import xyz.uthofficial.projectcirnobackend.dto.ChatRequest
import xyz.uthofficial.projectcirnobackend.repository.EventRepository
import xyz.uthofficial.projectcirnobackend.repository.UserRepository
import xyz.uthofficial.projectcirnobackend.service.ChatSessionService
import xyz.uthofficial.projectcirnobackend.service.EventTools
import java.security.Principal

@RestController
@RequestMapping("/api/agent")
class ChatController(
    private val chatModel: ChatModel,
    private val chatSessionService: ChatSessionService,
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository
) {

    private val systemPrompt = SystemMessage(
        """
        You are Cirno, a helpful calendar assistant. You help users manage their events.
        You can create, list, update, and delete calendar events using the provided tools.
        Be concise and confirm actions clearly.
        """.trimIndent()
    )

    @PostMapping("/chat", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chat(
        @Valid @RequestBody request: ChatRequest,
        principal: Principal
    ): Flux<ServerSentEvent<String>> {
        val eventTools = EventTools(eventRepository, userRepository, principal.name)
        val history = chatSessionService.getHistory(request.sessionId)

        val messages: MutableList<Message> = mutableListOf(systemPrompt)
        messages.addAll(history)

        chatSessionService.appendUserMessage(request.sessionId, request.message)

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
            .concatWith(
                Mono.just(
                    ServerSentEvent.builder("""{"type":"done"}""").build()
                )
            )
            .doOnComplete {
                if (responseBuilder.isNotEmpty()) {
                    chatSessionService.appendAssistantMessage(request.sessionId, responseBuilder.toString())
                }
            }
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
