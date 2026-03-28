package xyz.uthofficial.projectcirnobackend.service

import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import xyz.uthofficial.projectcirnobackend.dto.CreateEventRequest
import xyz.uthofficial.projectcirnobackend.repository.EventRepository
import xyz.uthofficial.projectcirnobackend.repository.UserRepository
import java.util.UUID

class EventTools(
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository,
    private val currentUsername: String
) {

    @Tool(description = "Create a new calendar event for the current user")
    fun createEvent(
        @ToolParam(description = "Event name", required = true) name: String,
        @ToolParam(description = "ISO 8601 datetime (e.g. 2026-03-30T14:00:00)", required = true) datetime: String,
        @ToolParam(description = "Event description") description: String? = null,
        @ToolParam(description = "Comma-separated tags (e.g. meeting, work)") tags: String? = null
    ): String {
        val user = userRepository.findByUsername(currentUsername) ?: return "Error: User not found"
        val tagList = tags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        val request = CreateEventRequest(name, datetime, description, tagList)
        val record = eventRepository.createEvent(user.id.value, request)
        return "Event '${record.name}' created for ${record.datetime}. ID: ${record.id}"
    }

    @Tool(description = "List calendar events for the current user within a time range")
    fun listEvents(
        @ToolParam(description = "Start date in ISO 8601 format (e.g. 2026-03-01T00:00:00)", required = true) start: String,
        @ToolParam(description = "Number of months to query", required = true) lengthMonths: Int
    ): String {
        val user = userRepository.findByUsername(currentUsername) ?: return "Error: User not found"
        val events = eventRepository.getEventsByTimeRange(user.id.value, start, lengthMonths)
        if (events.isEmpty()) return "No events found in this time range."
        return events.joinToString("\n") {
            val tags = if (it.tags.isNotEmpty()) " [${it.tags.joinToString(", ")}]" else ""
            val desc = if (it.description != null) " — ${it.description}" else ""
            "- ${it.name} @ ${it.datetime}${tags}${desc} (ID: ${it.id})"
        }
    }

    @Tool(description = "Update an existing calendar event")
    fun updateEvent(
        @ToolParam(description = "Event UUID", required = true) eventId: String,
        @ToolParam(description = "New event name", required = true) name: String,
        @ToolParam(description = "New ISO 8601 datetime", required = true) datetime: String,
        @ToolParam(description = "New description") description: String? = null,
        @ToolParam(description = "New comma-separated tags") tags: String? = null
    ): String {
        val user = userRepository.findByUsername(currentUsername) ?: return "Error: User not found"
        val tagList = tags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        val request = CreateEventRequest(name, datetime, description, tagList)
        val record = eventRepository.updateEvent(user.id.value, UUID.fromString(eventId), request)
        return "Event '${record.name}' updated. New datetime: ${record.datetime}"
    }

    @Tool(description = "Delete a calendar event by its ID")
    fun deleteEvent(
        @ToolParam(description = "Event UUID", required = true) eventId: String
    ): String {
        val user = userRepository.findByUsername(currentUsername) ?: return "Error: User not found"
        eventRepository.deleteEvent(user.id.value, UUID.fromString(eventId))
        return "Event deleted successfully."
    }
}
