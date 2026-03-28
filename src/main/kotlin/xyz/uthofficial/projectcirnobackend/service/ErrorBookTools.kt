package xyz.uthofficial.projectcirnobackend.service

import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import xyz.uthofficial.projectcirnobackend.repository.UserRepository
import java.util.UUID

class ErrorBookTools(
    private val errorBookService: ErrorBookService,
    private val userRepository: UserRepository,
    private val currentUsername: String
) {

    @Tool(description = "Record a new error in the user's errorbook. Provide a markdown description of what went wrong, optional tags, and an optional date. At least a description must be provided.")
    fun createErrorRecord(
        @ToolParam(description = "Markdown description of the error (what went wrong, why, what to do next time)", required = true) description: String,
        @ToolParam(description = "Comma-separated tags (e.g. physics, mechanics, exam-mistake)") tags: String? = null,
        @ToolParam(description = "ISO 8601 datetime when the error occurred (e.g. 2026-03-29T14:00:00)") date: String? = null
    ): String {
        val user = userRepository.findByUsername(currentUsername) ?: return "Error: User not found"
        return try {
            val tagList = tags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
            val result = errorBookService.create(
                userId = user.id.value,
                description = description,
                imagePath = null,
                tags = tagList,
                date = date,
                eventId = null
            )
            "Error recorded. ID: ${result.id}. Tags: ${if (result.tags.isNotEmpty()) result.tags.joinToString(", ") else "none"}"
        } catch (e: IllegalArgumentException) {
            "Error: ${e.message}"
        }
    }

    @Tool(description = "Update an existing error record in the user's errorbook. Only provide the fields you want to change.")
    fun updateErrorRecord(
        @ToolParam(description = "The UUID of the error record to update", required = true) errorId: String,
        @ToolParam(description = "New markdown description of the error") description: String? = null,
        @ToolParam(description = "New comma-separated tags (replaces all existing tags)") tags: String? = null,
        @ToolParam(description = "New ISO 8601 datetime when the error occurred") date: String? = null
    ): String {
        val user = userRepository.findByUsername(currentUsername) ?: return "Error: User not found"
        return try {
            val tagList = tags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
            val result = errorBookService.update(
                userId = user.id.value,
                errorId = UUID.fromString(errorId),
                description = description,
                tags = tagList,
                date = date,
                eventId = null
            )
            "Error record updated. ID: ${result.id}."
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    @Tool(description = "Delete an error record from the user's errorbook by its ID.")
    fun deleteErrorRecord(
        @ToolParam(description = "The UUID of the error record to delete", required = true) errorId: String
    ): String {
        val user = userRepository.findByUsername(currentUsername) ?: return "Error: User not found"
        return try {
            errorBookService.delete(user.id.value, UUID.fromString(errorId))
            "Error record deleted."
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
