package xyz.uthofficial.projectcirnobackend.controller

import com.google.adk.agents.LlmAgent
import com.google.adk.agents.RunConfig
import com.google.adk.events.Event
import com.google.adk.models.springai.SpringAI
import com.google.adk.runner.InMemoryRunner
import com.google.adk.tools.FunctionTool
import com.google.genai.types.Content
import com.google.genai.types.Part
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import xyz.uthofficial.projectcirnobackend.dto.ChatRequest
import xyz.uthofficial.projectcirnobackend.repository.EventRepository
import xyz.uthofficial.projectcirnobackend.repository.UserRepository
import xyz.uthofficial.projectcirnobackend.service.ChatSessionService
import xyz.uthofficial.projectcirnobackend.service.ErrorBookService
import xyz.uthofficial.projectcirnobackend.service.ErrorBookTools
import xyz.uthofficial.projectcirnobackend.service.EventTools
import xyz.uthofficial.projectcirnobackend.service.UserIdentityService
import xyz.uthofficial.projectcirnobackend.service.UserIdentityTools
import java.security.Principal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@RestController
@RequestMapping("/api/adk")
class AgentController(
    private val springAI: SpringAI,
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository,
    private val userIdentityService: UserIdentityService,
    private val errorBookService: ErrorBookService,
    private val chatSessionService: ChatSessionService
) {

    private fun buildSystemPrompt(userId: UUID): String {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val profile = userIdentityService.getByIdentity(userId)

        val profileBlock = if (profile != null) """
            ## User Profile (known)
            - **Identity**: ${profile.identity}
            - **Academic Goal**: ${profile.goal}
        """.trimIndent() else """
            ## User Profile (unknown)
            The user has NOT yet provided their identity or academic goal.
            You MUST NOT assume anything about who they are or what they need.
            You MUST conduct the onboarding protocol (see §2 below) before
            making any scheduling decisions or study plans.
        """.trimIndent()

        val errorSummary = errorBookService.getRecentErrorsSummary(userId)
        val errorBlock = """
            ## Recent Error Records
            $errorSummary
        """.trimIndent()

        return """
            # Cirno — Study Schedule Assistant

            You are **Cirno**, a warm, encouraging, and organised AI study planner.
            You help students take control of their time, build consistent habits, and
            work purposefully towards their academic goals.

            ## Core Identity
            - You speak with warmth, patience, and gentle enthusiasm.
            - You treat every learner with respect, regardless of their level.
            - You are concise when confirming actions, but never cold or robotic.
            - You proactively suggest study plans, revision strategies, and break schedules.

            ## Today
            - **Date**: $today

            $profileBlock

            $errorBlock

            ## Capabilities
            You have access to the following tools:

            ### Calendar Management
            - **Create events** — schedule study sessions, classes, deadlines, and exams.
            - **List events** — show upcoming commitments filtered by date range.
            - **Update events** — change time, duration, or details of existing events.
            - **Delete events** — remove cancelled or past commitments.

            ### User Profile Management
            - **Set identity & goal** — the user can tell you their academic identity
              (e.g. "Year 12 student") and their goal (e.g. "A* in Further Maths
              and a place at Cambridge"). You can save this via the provided tool.
            - **Update profile** — the user can change their identity or goal at any time.

            ### Errorbook Management
            - **Record errors** — when the user makes a mistake (e.g. a wrong answer on a past paper),
              record it with a markdown description, tags (subject, topic, difficulty), and optionally
              the date it occurred. Use this to track recurring weak areas.
            - **Update error records** — edit descriptions, tags, or dates of existing records.
            - **Delete error records** — remove records that are no longer relevant.
            - **Search errors** — search the user's errorbook by keywords (matches description substrings
              and exact tag names). Specify keywords and optionally a result limit (1–30, default 10).
            - **Review errors** — the system provides a summary of recent errors in the context,
              so you can reference them when scheduling revision or identifying weak topics.

            ## Behaviour Guidelines

            1. **Personalisation first.** If the user's profile is known, always tailor
               your scheduling advice to their goal. Prioritise the subjects they care
               most about, and schedule revision around their exam board's style and
               timing. If they mention a weak subject, give it more weight.

            2. **Profile onboarding protocol (when profile is unknown).**
               You MUST gather detailed information through a warm, natural conversation
               before making any scheduling decisions. Do NOT accept a vague answer like
               "I'm a Year 12 and I want to go to Cambridge." Drill deeper:

               **Step 1 — Level & year.** What year/level are they in?
               (e.g. Year 12, Year 13, 1st-year undergrad, GCSE retaker)

               **Step 2 — Subjects.** Which subjects are they studying RIGHT NOW?
               List every one. Ask which exam board if relevant (AQA, Edexcel, OCR, etc.).

               **Step 3 — Target institution & course.** Which university or course are
               they aiming for? If multiple, ask for their top choices and reach options.
               (e.g. Cambridge Natural Sciences, Manchester Computer Science, etc.)

               **Step 4 — Grade requirements.** What grades or UCAS points do they need?
               What are they currently predicted vs what do they need to achieve?
               Ask about any gap between the two.

               **Step 5 — Exam dates & deadlines.** When are their exams (or coursework
               deadlines, personal statement dates, UCAT, STEP, etc.)? If they don't
               know, help them find out.

               **Step 6 — Weak areas.** Which subjects or topics feel hardest? Any
               particular topic within a subject they struggle with?

               **Step 7 — Study habits.** How many hours per week are they currently
               studying? Do they have a routine? Any time commitments (part-time job,
               sports, etc.)?

               Cover as many of these as the conversation naturally allows.
               Do NOT ask all seven at once — weave them in across 2–3 messages.
               Once you have enough detail, summarise it back to the user and ask
               them to confirm before you save it via the tool.
               Example: "So to confirm — you're a Year 12 student taking Maths,
               Physics, and Chemistry on Edexcel, aiming for A* A* A so you can
               apply for Engineering at Imperial. Physics is your weakest subject,
               especially mechanics. Does that sound right?"

            3. **Scheduling philosophy.**
               - Break large goals into daily or weekly study blocks.
               - Suggest realistic session lengths (25–50 min focus + short breaks).
               - Space out subjects using interleaving and spaced repetition.
               - Always leave buffer time and rest days.
               - When the user mentions exam dates, work backwards from the deadline
                 to build a revision timeline.

            4. **Tone.** Be warm, supportive, and clear. Celebrate progress. Offer
               encouragement when the user seems stressed. Use a friendly, approachable
               style — think "study buddy", not "lecturer". When drilling into details,
               sound curious and interested, not interrogative.

            5. **Clarity.** When confirming an action (event created, goal updated, etc.),
               state exactly what happened in one or two sentences. No unnecessary filler.
        """.trimIndent()
    }

    private fun resolveUserId(principal: Principal): UUID {
        val user = userRepository.findByUsername(principal.name)
            ?: throw IllegalArgumentException("User not found")
        return user.id.value
    }

    private fun escapeJson(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    @PostMapping("/chat", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chat(
        @Valid @RequestBody request: ChatRequest,
        principal: Principal
    ): Flux<ServerSentEvent<String>> {
        logger.info("=== ADK CHAT REQUEST === user=${principal.name} sessionId=${request.sessionId} message=${request.message.take(100)}")

        val userId = resolveUserId(principal)
        val parsedSessionId = request.sessionId?.let { UUID.fromString(it) }
        val (sessionId, history) = chatSessionService.getOrCreateSession(parsedSessionId, userId)

        logger.info("Session: $sessionId — history size: ${history.size}")

        val eventTools = EventTools(eventRepository, userRepository, principal.name)
        val userIdentityTools = UserIdentityTools(userIdentityService, userRepository, principal.name)
        val errorBookTools = ErrorBookTools(errorBookService, userRepository, principal.name)

        val toolWrappers = listOf(
            FunctionTool.create(eventTools, "createEvent"),
            FunctionTool.create(eventTools, "listEvents"),
            FunctionTool.create(eventTools, "updateEvent"),
            FunctionTool.create(eventTools, "deleteEvent"),
            FunctionTool.create(userIdentityTools, "createUserIdentity"),
            FunctionTool.create(userIdentityTools, "updateUserIdentity"),
            FunctionTool.create(errorBookTools, "createErrorRecord"),
            FunctionTool.create(errorBookTools, "updateErrorRecord"),
            FunctionTool.create(errorBookTools, "deleteErrorRecord"),
            FunctionTool.create(errorBookTools, "searchErrorRecords")
        )

        val agent = LlmAgent.builder()
            .name("cirno-adk")
            .description("Cirno — Study Schedule Assistant powered by ADK")
            .model(springAI)
            .instruction(buildSystemPrompt(userId))
            .tools(*toolWrappers.toTypedArray())
            .build()

        val runner = InMemoryRunner(agent)

        val userIdStr = userId.toString()

        chatSessionService.appendUserMessage(sessionId, request.message)

        val messageContent = Content.builder()
            .role("user")
            .parts(listOf(Part.fromText(request.message)))
            .build()

        return Flux.defer {
            try {
                val events: List<Event> = runner
                    .runAsync(
                        userIdStr,
                        sessionId.toString(),
                        messageContent,
                        RunConfig.builder().build()
                    )
                    .toList()
                    .blockingGet()

                val assistantText = extractResponseText(events)

                if (assistantText.isNotEmpty()) {
                    chatSessionService.appendAssistantMessage(sessionId, assistantText)
                    logger.info("=== ADK RESPONSE (${assistantText.length} chars): ${assistantText.take(200)}")
                }

                Flux.concat(
                    Flux.just(
                        ServerSentEvent.builder(
                            """{"type":"session","content":"$sessionId"}"""
                        ).build()
                    ),
                    Flux.just(
                        ServerSentEvent.builder(
                            """{"type":"text","content":"${escapeJson(assistantText)}"}"""
                        ).build()
                    ),
                    Flux.just(
                        ServerSentEvent.builder("""{"type":"done"}""").build()
                    )
                )
            } catch (error: Exception) {
                logger.error("=== ADK CHAT ERROR === ${error.javaClass.name}: ${error.message}", error)
                val errorMessage = if (error.message != null) escapeJson(error.message!!) else "An unexpected error occurred"
                Flux.just(
                    ServerSentEvent.builder("""{"type":"error","content":"$errorMessage"}""").build(),
                    ServerSentEvent.builder("""{"type":"done"}""").build()
                )
            }
        }
    }

    private fun extractResponseText(events: List<Event>): String {
        val textParts = mutableListOf<String>()
        for (event in events) {
            if (event.author() == "user") continue
            if (event.partial().orElse(false)) continue
            val content = event.content().orElse(null) ?: continue
            val parts = content.parts().orElse(null) ?: continue
            for (part in parts) {
                val text = part.text().orElse(null)
                if (text != null && text.isNotBlank()) {
                    textParts.add(text)
                }
            }
        }
        return textParts.joinToString("\n")
    }

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(AgentController::class.java)
    }
}
