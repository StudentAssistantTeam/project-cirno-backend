package xyz.uthofficial.projectcirnobackend.dto

import jakarta.validation.constraints.NotBlank

data class ChatRequest(
    @field:NotBlank(message = "Message is required")
    val message: String,

    @field:NotBlank(message = "Session ID is required")
    val sessionId: String
)
