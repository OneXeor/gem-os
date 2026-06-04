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
)

@Serializable
data class BrainDecisionResponse(
    val runId: String? = null,
    val childRun: ChildRunSummary? = null,
    val status: String? = null,
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
