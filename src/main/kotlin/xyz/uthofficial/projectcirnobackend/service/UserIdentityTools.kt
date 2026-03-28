package xyz.uthofficial.projectcirnobackend.service

import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import xyz.uthofficial.projectcirnobackend.repository.UserRepository

class UserIdentityTools(
    private val userIdentityService: UserIdentityService,
    private val userRepository: UserRepository,
    private val currentUsername: String
) {

    @Tool(description = "Set the current user's identity and academic goal. Fails if they already have one — use updateUserIdentity instead.")
    fun createUserIdentity(
        @ToolParam(description = "The user's identity (e.g. 'secondary school student')", required = true) identity: String,
        @ToolParam(description = "The user's academic goal (e.g. 'full A* in physics, aiming for Oxford')", required = true) goal: String
    ): String {
        val user = userRepository.findByUsername(currentUsername)
            ?: return "Error: User not found"
        return try {
            val result = userIdentityService.create(user.id.value, identity, goal)
            "Identity set to '${result.identity}' with goal '${result.goal}'."
        } catch (e: IllegalStateException) {
            "Error: This user already has an identity and goal set. Use updateUserIdentity to change them."
        }
    }

    @Tool(description = "Update the current user's identity and/or academic goal. Only provide the fields you want to change.")
    fun updateUserIdentity(
        @ToolParam(description = "New identity (e.g. 'Year 12 student'). Leave blank to keep unchanged.") identity: String? = null,
        @ToolParam(description = "New academic goal. Leave blank to keep unchanged.") goal: String? = null
    ): String {
        val user = userRepository.findByUsername(currentUsername)
            ?: return "Error: User not found"
        if (identity == null && goal == null) return "Error: Provide at least one field to update."
        return try {
            val result = userIdentityService.update(user.id.value, identity, goal)
            "Identity updated to '${result.identity}' with goal '${result.goal}'."
        } catch (e: NoSuchElementException) {
            "Error: No identity exists for this user. Use createUserIdentity first."
        }
    }
}
