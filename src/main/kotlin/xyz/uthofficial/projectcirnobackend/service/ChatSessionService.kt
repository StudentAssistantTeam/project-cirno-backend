package xyz.uthofficial.projectcirnobackend.service

import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class ChatSessionService {

    private val sessions = ConcurrentHashMap<String, MutableList<Message>>()
    private val maxSessions = 100
    private val maxMessagesPerSession = 20

    fun getHistory(sessionId: String): List<Message> {
        return sessions[sessionId]?.toList() ?: emptyList()
    }

    fun appendUserMessage(sessionId: String, content: String) {
        getOrCreate(sessionId).add(UserMessage(content))
    }

    fun appendAssistantMessage(sessionId: String, content: String) {
        getOrCreate(sessionId).add(AssistantMessage(content))
    }

    fun clearSession(sessionId: String) {
        sessions.remove(sessionId)
    }

    private fun getOrCreate(sessionId: String): MutableList<Message> {
        return sessions.getOrPut(sessionId) {
            evictIfNeeded()
            mutableListOf()
        }
    }

    private fun evictIfNeeded() {
        if (sessions.size >= maxSessions) {
            val oldest = sessions.keys.firstOrNull()
            if (oldest != null) sessions.remove(oldest)
        }
    }

    private fun trimHistory(messages: MutableList<Message>) {
        while (messages.size > maxMessagesPerSession) {
            messages.removeAt(1) // keep index 0 (system message slot) if present
        }
    }
}
