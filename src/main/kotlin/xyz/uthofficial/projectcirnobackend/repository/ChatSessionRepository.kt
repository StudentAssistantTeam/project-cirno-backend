package xyz.uthofficial.projectcirnobackend.repository

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.stereotype.Repository
import xyz.uthofficial.projectcirnobackend.dto.ChatMessageItem
import xyz.uthofficial.projectcirnobackend.dto.ChatSessionListItem
import xyz.uthofficial.projectcirnobackend.entity.ChatMessages
import xyz.uthofficial.projectcirnobackend.entity.ChatSessions
import xyz.uthofficial.projectcirnobackend.entity.Users
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@Repository
class ChatSessionRepository {

    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val previewLength = 80

    fun createSession(userId: UUID): UUID = transaction {
        val row = ChatSessions.insert {
            it[user] = userId
            it[title] = null
        }
        row[ChatSessions.id].value
    }

    fun getSessionsByUser(userId: UUID): List<ChatSessionListItem> = transaction {
        ChatSessions.selectAll()
            .where { ChatSessions.user eq userId }
            .orderBy(ChatSessions.updatedAt to SortOrder.DESC)
            .map { row ->
                val sessionId = row[ChatSessions.id].value
                val lastMsg = ChatMessages.selectAll()
                    .where { ChatMessages.session eq sessionId }
                    .orderBy(ChatMessages.createdAt to SortOrder.DESC)
                    .limit(1)
                    .singleOrNull()

                ChatSessionListItem(
                    id = sessionId,
                    title = row[ChatSessions.title],
                    createdAt = row[ChatSessions.createdAt].format(isoFormatter),
                    updatedAt = row[ChatSessions.updatedAt].format(isoFormatter),
                    lastMessagePreview = lastMsg?.get(ChatMessages.content)?.take(previewLength)
                )
            }
    }

    fun getMessages(sessionId: UUID): List<ChatMessageItem> = transaction {
        ChatMessages.selectAll()
            .where { ChatMessages.session eq sessionId }
            .orderBy(ChatMessages.createdAt to SortOrder.ASC)
            .map { row ->
                ChatMessageItem(
                    role = row[ChatMessages.role],
                    content = row[ChatMessages.content] ?: "",
                    createdAt = row[ChatMessages.createdAt].format(isoFormatter)
                )
            }
    }

    fun getMessagesAsSpringAi(sessionId: UUID): List<Message> = transaction {
        ChatMessages.selectAll()
            .where { ChatMessages.session eq sessionId }
            .orderBy(ChatMessages.createdAt to SortOrder.ASC)
            .map { row ->
                val role = row[ChatMessages.role]
                val content = row[ChatMessages.content] ?: ""
                when (role) {
                    "system" -> SystemMessage(content)
                    "user" -> UserMessage(content)
                    "assistant" -> AssistantMessage(content)
                    else -> UserMessage(content)
                }
            }
    }

    fun appendMessages(sessionId: UUID, messages: List<Message>) {
        if (messages.isEmpty()) return
        transaction {
            val now = LocalDateTime.now()
            var isFirstUserMessage = false

            // Check if session title is still null (first message ever)
            val sessionRow = ChatSessions.selectAll()
                .where { ChatSessions.id eq sessionId }
                .single()
            if (sessionRow[ChatSessions.title] == null) {
                isFirstUserMessage = true
            }

            for (msg in messages) {
                val role = when (msg) {
                    is SystemMessage -> "system"
                    is UserMessage -> "user"
                    is AssistantMessage -> "assistant"
                    else -> "user"
                }
                ChatMessages.insert {
                    it[session] = sessionId
                    it[ChatMessages.role] = role
                    it[content] = msg.text
                    it[createdAt] = now
                }

                // Auto-title from first user message
                if (isFirstUserMessage && msg is UserMessage) {
                    val title = msg.text.take(50)
                    ChatSessions.update({ ChatSessions.id eq sessionId }) {
                        it[ChatSessions.title] = title
                        it[updatedAt] = now
                    }
                    isFirstUserMessage = false
                }
            }

            // Always bump updated_at
            ChatSessions.update({ ChatSessions.id eq sessionId }) {
                it[updatedAt] = now
            }
        }
    }

    fun deleteSession(sessionId: UUID) {
        transaction {
            ChatMessages.deleteWhere { ChatMessages.session eq sessionId }
            ChatSessions.deleteWhere { ChatSessions.id eq sessionId }
        }
    }

    fun sessionBelongsToUser(sessionId: UUID, userId: UUID): Boolean = transaction {
        ChatSessions.selectAll()
            .where { ChatSessions.id eq sessionId }
            .singleOrNull()
            ?.get(ChatSessions.user)?.let { it == userId } ?: false
    }
}
