package com.onexeor.gemos.brain

import com.onexeor.gemos.core.GemConfig
import com.onexeor.gemos.core.run.ChildRunSummary
import com.onexeor.gemos.core.ProjectConfig
import kotlinx.serialization.Serializable

@Serializable
data class BrainRequest(
    val user: String,
    val text: String,
    val projectHint: String? = null,
    val threadId: String? = null,
    val contextMessages: List<BrainContextMessage> = emptyList(),
)

@Serializable
data class BrainContextMessage(
    val role: String,
    val text: String,
)

@Serializable
data class BrainDecisionResponse(
    val runId: String? = null,
    val childRun: ChildRunSummary? = null,
    val status: String? = null,
    val replyText: String? = null,
    val decision: String,
    val route: String,
    val projectId: String? = null,
    val pipelineId: String? = null,
    val provider: String? = null,
    val needsClarification: Boolean = false,
    val reason: String,
    val executorText: String? = null,
)

object BrainDecider {
    fun decide(config: GemConfig, request: BrainRequest): BrainDecisionResponse {
        val text = request.text.trim()
        if (text.isBlank()) {
            return BrainDecisionResponse(
                decision = "ask_clarification",
                route = "slack",
                needsClarification = true,
                reason = "Request text is empty.",
            )
        }

        val normalized = text.lowercase()
        commandDecision(config, request, normalized)?.let { return it }

        val project = resolveProject(config.projects.projects, request.projectHint, normalized)

        return BrainDecisionResponse(
            decision = "plan_with_llm",
            route = "planner",
            projectId = project?.id,
            provider = config.providers.providers.orchestration.plannerDefault,
            reason = "Semantic routing is delegated to the planner so the LLM can choose the right capability.",
            executorText = BrainPlannerPrompt.build(config, request, project),
        )
    }

    fun withReply(config: GemConfig, request: BrainRequest, decision: BrainDecisionResponse): BrainDecisionResponse =
        if (decision.replyText != null) {
            decision
        } else {
            decision.copy(replyText = buildReply(config, request, decision))
        }

    private fun commandDecision(config: GemConfig, request: BrainRequest, normalizedText: String): BrainDecisionResponse? {
        val command = normalizedText.trim().removePrefix("/").trimEnd('?', '!', '.')
        return when {
            command in setOf(
                "help",
                "what can you do",
                "what are you capable of",
                "what can you help with",
                "capabilities",
                "commands",
            ) -> BrainDecisionResponse(
                decision = "show_help",
                route = "context",
                reason = "User asked for Gem help.",
                replyText = helpText(),
            )
            command in setOf("projects", "list projects", "what projects") -> BrainDecisionResponse(
                decision = "list_projects",
                route = "context",
                reason = "User asked for configured projects.",
                replyText = projectsText(config.projects.projects),
            )
            command in setOf("status", "system status") -> BrainDecisionResponse(
                decision = "show_status",
                route = "context",
                reason = "User asked for Gem system status.",
                replyText = statusText(config),
            )
            command in setOf(
                "model",
                "what model are you",
                "what is the model are you",
                "which model are you",
                "are you codex",
                "are you claude",
            ) -> BrainDecisionResponse(
                decision = "show_model",
                route = "context",
                reason = "User asked which model/runtime Gem is using.",
                replyText = modelText(config),
            )
            command in setOf("runs", "recent runs") -> BrainDecisionResponse(
                decision = "show_runs",
                route = "context",
                reason = "User asked for recent runs.",
                replyText = "Run history is stored in Postgres and visible through the admin/runtime layer. The Slack `runs` view is the next small UI endpoint to wire.",
            )
            command == "continue" -> BrainDecisionResponse(
                decision = "continue_session",
                route = "context",
                reason = "User asked to continue the active Slack session.",
                replyText = continueText(request),
            )
            else -> null
        }
    }

    private fun buildReply(config: GemConfig, request: BrainRequest, decision: BrainDecisionResponse): String =
        when (decision.decision) {
            "ask_clarification" -> "I need a bit more detail. What should I work on?"
            "run_pipeline" -> {
                val pipeline = decision.pipelineId ?: "the matched pipeline"
                val project = decision.projectId ?: "the matched project"
                "I found `$pipeline` for `$project` and created a run. I will report progress here when execution is wired."
            }
            "answer_from_context" -> contextAnswer(config, request, decision)
            "use_code_agent" -> {
                val provider = decision.provider ?: config.providers.providers.orchestration.defaultCodeRoute
                val project = decision.projectId?.let { " for `$it`" }.orEmpty()
                "This looks like a code task$project. I would route it to `$provider`; the host runner/execution step is next."
            }
            "plan_with_llm" -> {
                val provider = decision.provider ?: config.providers.providers.orchestration.plannerDefault
                val contextNote = if (request.contextMessages.isNotEmpty()) {
                    " I loaded ${request.contextMessages.size} recent thread messages."
                } else {
                    ""
                }
                "I sent this to `$provider` to decide whether to answer directly, use a script, make an execution plan, run code-agent work, or investigate deeply.$contextNote"
            }
            else -> "I created a run for this request."
        }

    private fun contextAnswer(config: GemConfig, request: BrainRequest, decision: BrainDecisionResponse): String {
        val normalized = request.text.trim().lowercase()
        if (normalized.contains("who are you")) {
            return "${config.identity.gem.name} is ${config.identity.gem.role}."
        }
        if (normalized.contains("who am i")) {
            val user = config.identity.users.values.firstOrNull { request.user in it.slackUserIds }
                ?: config.identity.users["viktor"]
            return "You are ${user?.displayName ?: request.user}. I will use your project preferences and Slack context as memory is connected."
        }
        if (normalized.contains("projects") || normalized.contains("what projects")) {
            return projectsText(config.projects.projects)
        }
        if (normalized.contains("status")) {
            return statusText(config)
        }
        return decision.projectId?.let { "I found project `$it` in Gem configuration." }
            ?: "I can answer from Gem configuration, but I need a more specific question."
    }

    private fun helpText(): String =
        listOf(
            "I can help from Slack. Type these as normal messages:",
            "`help` - show commands",
            "`status` - show current Gem runtime assumptions",
            "`projects` - list configured projects",
            "`runs` - show run history status",
            "`session` - show this Slack thread's Gem session",
            "`reset session` - close this thread's active Gem session",
            "`continue` - continue from recent messages in this thread",
            "You can also ask for ASO, project, or code-agent work and I will route it.",
        ).joinToString("\n")

    private fun projectsText(projects: List<ProjectConfig>): String =
        if (projects.isEmpty()) {
            "No projects are configured yet."
        } else {
            projects.joinToString(prefix = "Configured projects:\n", separator = "\n") { project ->
                val aliases = project.aliases.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = " aliases: ") ?: ""
                "- `${project.id}` - ${project.name} (${project.status})$aliases"
            }
        }

    private fun statusText(config: GemConfig): String =
        listOf(
            "Gem runtime:",
            "- env: `${config.settings.gemEnv}`",
            "- planner: `${config.providers.providers.orchestration.plannerDefault}`",
            "- code agent: `${config.providers.providers.orchestration.defaultCodeRoute}`",
            "- embeddings: `${config.settings.embeddingsModel}` (${config.settings.embeddingsVectorSize})",
        ).joinToString("\n")

    private fun modelText(config: GemConfig): String =
        listOf(
            "I am Gem OS Brain, not one raw model.",
            "- Slack interface: `slack-bot`",
            "- local command/context packager: `brain`",
            "- semantic decision layer: `${config.providers.providers.orchestration.plannerDefault}`",
            "- default planner/code route: `${config.providers.providers.orchestration.plannerDefault}` / `${config.providers.providers.orchestration.defaultCodeRoute}`",
            "- local embeddings: `${config.settings.embeddingsModel}` (${config.settings.embeddingsVectorSize})",
            "- general LLM chat fallback: not wired yet",
        ).joinToString("\n")

    private fun continueText(request: BrainRequest): String {
        val lastUserMessage = request.contextMessages
            .asReversed()
            .firstOrNull { it.role == "user" && it.text.lowercase() != "continue" }
        return if (lastUserMessage == null) {
            "I do not have enough previous context in this Slack session yet."
        } else {
            "I found the active thread context. Last useful user message was: \"${lastUserMessage.text.take(180)}\""
        }
    }

    private fun resolveProject(
        projects: List<ProjectConfig>,
        hint: String?,
        normalizedText: String,
    ): ProjectConfig? {
        val normalizedHint = hint?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        if (normalizedHint != null) {
            projects.firstOrNull { project ->
                project.id.lowercase() == normalizedHint ||
                    project.name.lowercase() == normalizedHint ||
                    project.aliases.any { it.lowercase() == normalizedHint }
            }?.let { return it }
        }

        return projects.firstOrNull { project ->
            normalizedText.contains(project.id.lowercase()) ||
                normalizedText.contains(project.name.lowercase()) ||
                project.aliases.any { normalizedText.contains(it.lowercase()) }
        }
    }
}

object BrainPlannerPrompt {
    fun build(config: GemConfig, request: BrainRequest, project: ProjectConfig?): String {
        val pipelineCatalog = config.pipelines.pipelines.joinToString("\n") { pipeline ->
            val projects = pipeline.projectIds.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "any"
            val command = pipeline.execution.command.joinToString(" ").ifBlank { "not configured" }
            "- ${pipeline.id}: ${pipeline.name}; enabled=${pipeline.enabled}; projects=$projects; execution=${pipeline.execution.type}; command=$command"
        }.ifBlank { "- none configured" }

        val projectCatalog = config.projects.projects.joinToString("\n") { configuredProject ->
            val aliases = configuredProject.aliases.joinToString(", ").ifBlank { "none" }
            val pipelines = configuredProject.pipelines.joinToString(", ").ifBlank { "none" }
            "- ${configuredProject.id}: ${configuredProject.name}; aliases=$aliases; pipelines=$pipelines"
        }.ifBlank { "- none configured" }

        val context = request.contextMessages.takeLast(12).joinToString("\n") { message ->
            "${message.role}: ${message.text.take(1000)}"
        }.ifBlank { "none" }

        val selectedProject = project?.id ?: "unknown"
        return """
            You are Gem OS Brain's LLM decision layer, running from Slack.

            Decide the right next action for the user request. Do not rely on hard-coded keyword routing.
            Choose exactly one mode:
            - direct_answer: answer immediately when the result is low-risk and does not need tools.
            - default_value: return a sensible configured default when the user asks for one.
            - script: select a configured pipeline/script when the request maps to one.
            - multi_script_plan: produce an ordered plan of scripts/pipelines to execute.
            - code_agent: perform repository implementation, debugging, refactoring, or tests.
            - deep_investigation: inspect context/code/docs first and then report findings or a plan.
            - needs_clarification: ask one concise question only when required to avoid a wrong action.

            Safety:
            - Work non-interactively unless clarification is truly required.
            - Do not pretend unavailable live-data tools exist. If live data is needed, say which provider/tool is missing.
            - Do not execute destructive or externally visible changes without explicit approval.
            - For configured scripts, name the pipeline id and command before suggesting execution.

            Reply format:
            mode: <one mode>
            project: <project id or unknown>
            action: <concise decision>
            steps:
            - <only include concrete steps when useful>
            response:
            <Slack-ready answer or status>

            User: ${request.user}
            Project hint: ${request.projectHint ?: "none"}
            Resolved project: $selectedProject
            Request:
            ${request.text.trim()}

            Recent Slack context:
            $context

            Configured projects:
            $projectCatalog

            Configured scripts/pipelines:
            $pipelineCatalog
        """.trimIndent()
    }
}
