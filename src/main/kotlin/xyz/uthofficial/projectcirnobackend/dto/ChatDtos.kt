package xyz.uthofficial.projectcirnobackend.dto

import java.util.UUID

data class ChatSessionListItem(
    val id: UUID,
    val title: String?,
    val createdAt: String,
    val updatedAt: String,
    val lastMessagePreview: String?
)

data class ChatSessionListResponse(
    val sessions: List<ChatSessionListItem>
)

data class ChatMessageItem(
    val role: String,
    val content: String,
    val createdAt: String
)

data class ChatHistoryResponse(
    val sessionId: UUID,
    val messages: List<ChatMessageItem>
)
