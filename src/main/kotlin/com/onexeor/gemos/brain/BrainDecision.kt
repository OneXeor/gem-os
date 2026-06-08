package com.onexeor.gemos.brain

import com.onexeor.gemos.core.GemConfig
import com.onexeor.gemos.core.run.ChildRunSummary
import com.onexeor.gemos.core.PipelineConfig
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
        val pipeline = resolvePipeline(config.pipelines.pipelines, project, normalized)

        if (pipeline != null && project != null) {
            return BrainDecisionResponse(
                decision = "run_pipeline",
                route = config.providers.providers.orchestration.defaultPipelineRoute,
                projectId = project.id,
                pipelineId = pipeline.id,
                provider = pipeline.provider.chat ?: pipeline.provider.codeAgent,
                reason = "Request matched pipeline '${pipeline.id}' for project '${project.id}'.",
            )
        }

        if (looksLikeProjectQuestion(normalized)) {
            return BrainDecisionResponse(
                decision = "answer_from_context",
                route = "context",
                projectId = project?.id,
                reason = "Request can be answered from Gem identity/project configuration.",
            )
        }

        if (looksLikeCodeTask(normalized)) {
            return BrainDecisionResponse(
                decision = "use_code_agent",
                route = "code_agent",
                projectId = project?.id,
                provider = config.providers.providers.orchestration.defaultCodeRoute,
                reason = "Request looks like an implementation or repository task.",
            )
        }

        return BrainDecisionResponse(
            decision = "plan_with_llm",
            route = "planner",
            projectId = project?.id,
            provider = config.providers.providers.orchestration.plannerDefault,
            reason = "No deterministic pipeline matched; route to planner.",
        )
    }

    fun withReply(config: GemConfig, request: BrainRequest, decision: BrainDecisionResponse): BrainDecisionResponse =
        if (decision.replyText != null) {
            decision
        } else {
            decision.copy(replyText = buildReply(config, request, decision))
        }

    private fun commandDecision(config: GemConfig, request: BrainRequest, normalizedText: String): BrainDecisionResponse? {
        val command = normalizedText.trim().removePrefix("/")
        return when {
            command in setOf("help", "what can you do", "commands") -> BrainDecisionResponse(
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
                "I can plan this with `$provider`. For now I created the planning run.$contextNote"
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
            "I can help from Slack with:",
            "`help` - show commands",
            "`status` - show current Gem runtime assumptions",
            "`projects` - list configured projects",
            "`runs` - show run history status",
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

    private fun resolvePipeline(
        pipelines: List<PipelineConfig>,
        project: ProjectConfig?,
        normalizedText: String,
    ): PipelineConfig? {
        val pipelineHints = listOf("run", "start", "check", "monitor", "pipeline", "aso")
        if (pipelineHints.none { normalizedText.contains(it) }) return null

        val candidates = if (project == null) {
            pipelines
        } else {
            pipelines.filter { pipeline ->
                pipeline.projectIds.isEmpty() || project.id in pipeline.projectIds || pipeline.id in project.pipelines
            }
        }

        return candidates.firstOrNull { pipeline ->
            normalizedText.contains(pipeline.id.lowercase()) ||
                normalizedText.contains(pipeline.name.lowercase()) ||
                pipeline.id.split("-").any { it.length > 2 && normalizedText.contains(it.lowercase()) }
        }
    }

    private fun looksLikeProjectQuestion(normalizedText: String): Boolean =
        listOf("who are you", "who am i", "projects", "what projects", "status").any {
            normalizedText.contains(it)
        }

    private fun looksLikeCodeTask(normalizedText: String): Boolean =
        listOf("implement", "fix", "refactor", "code", "test", "bug", "pr", "branch").any {
            normalizedText.contains(it)
        }
}
