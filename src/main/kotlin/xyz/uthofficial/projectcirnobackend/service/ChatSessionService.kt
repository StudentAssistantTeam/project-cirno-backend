package xyz.uthofficial.projectcirnobackend.service

import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.stereotype.Service
import xyz.uthofficial.projectcirnobackend.dto.ChatMessageItem
import xyz.uthofficial.projectcirnobackend.dto.ChatSessionListItem
import xyz.uthofficial.projectcirnobackend.repository.ChatSessionRepository
import java.util.UUID

@Service
class ChatSessionService(
    private val chatSessionRepository: ChatSessionRepository
) {

    /**
     * Returns the session UUID and existing message history.
     * If [sessionId] is null, creates a new session for the user.
     */
    fun getOrCreateSession(sessionId: UUID?, userId: UUID): Pair<UUID, List<Message>> {
        if (sessionId != null) {
            if (!chatSessionRepository.sessionBelongsToUser(sessionId, userId)) {
                throw IllegalArgumentException("Session not found")
            }
            val history = chatSessionRepository.getMessagesAsSpringAi(sessionId)
            return sessionId to history
        }
        val newId = chatSessionRepository.createSession(userId)
        return newId to emptyList()
    }

    fun appendUserMessage(sessionId: UUID, content: String) {
        chatSessionRepository.appendMessages(sessionId, listOf(UserMessage(content)))
    }

    fun appendAssistantMessage(sessionId: UUID, content: String) {
        chatSessionRepository.appendMessages(sessionId, listOf(AssistantMessage(content)))
    }

    fun clearSession(sessionId: UUID) {
        chatSessionRepository.deleteSession(sessionId)
    }

    fun getSessionsByUser(userId: UUID): List<ChatSessionListItem> {
        return chatSessionRepository.getSessionsByUser(userId)
    }

    fun getHistory(sessionId: UUID): List<ChatMessageItem> {
        return chatSessionRepository.getMessages(sessionId)
    }

    fun deleteSession(sessionId: UUID, userId: UUID) {
        if (!chatSessionRepository.sessionBelongsToUser(sessionId, userId)) {
            throw IllegalArgumentException("Session not found")
        }
        chatSessionRepository.deleteSession(sessionId)
    }
}
