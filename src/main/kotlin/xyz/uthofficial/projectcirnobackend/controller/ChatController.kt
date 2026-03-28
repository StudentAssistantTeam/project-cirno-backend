package xyz.uthofficial.projectcirnobackend.controller

import jakarta.validation.Valid
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
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
    ): SseEmitter {
        logger.info("=== CHAT REQUEST === user=${principal.name} sessionId=${request.sessionId} message=${request.message.take(100)}")

        val emitter = SseEmitter(300_000)

        val eventTools = EventTools(eventRepository, userRepository, principal.name)
        val history = chatSessionService.getHistory(request.sessionId)
        logger.info("History size: ${history.size}")

        val messages: MutableList<Message> = mutableListOf(systemPrompt)
        messages.addAll(history)

        chatSessionService.appendUserMessage(request.sessionId, request.message)

        Thread {
            try {
                logger.info("Calling Mimo API (call mode)...")

                val response = ChatClient.create(chatModel)
                    .prompt(Prompt(messages))
                    .user(request.message)
                    .tools(eventTools)
                    .call()

                val content = response.content() ?: ""
                logger.info("=== RESPONSE (${content.length} chars): ${content.take(200)}")

                emitter.send(SseEmitter.event().data("""{"type":"text","content":"${escapeJson(content)}"}"""))
                emitter.send(SseEmitter.event().data("""{"type":"done"}"""))
                emitter.complete()

                chatSessionService.appendAssistantMessage(request.sessionId, content)
            } catch (e: Exception) {
                logger.error("=== CHAT ERROR === ${e.javaClass.name}: ${e.message}", e)
                try {
                    val errorMessage = if (e.message != null) escapeJson(e.message!!) else "An unexpected error occurred"
                    emitter.send(SseEmitter.event().data("""{"type":"error","content":"$errorMessage"}"""))
                    emitter.send(SseEmitter.event().data("""{"type":"done"}"""))
                    emitter.complete()
                } catch (sendEx: Exception) {
                    logger.error("Failed to send error event", sendEx)
                    emitter.completeWithError(sendEx)
                }
            }
        }.start()

        return emitter
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
