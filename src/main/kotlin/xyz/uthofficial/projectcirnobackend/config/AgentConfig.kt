package xyz.uthofficial.projectcirnobackend.config

import com.google.adk.models.springai.SpringAI
import org.springframework.ai.chat.model.ChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AgentConfig {

    @Bean
    fun springAI(chatModel: ChatModel): SpringAI {
        return SpringAI(chatModel, "mimo-v2-flash")
    }
}
