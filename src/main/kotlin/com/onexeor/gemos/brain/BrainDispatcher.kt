package com.onexeor.gemos.brain

import com.onexeor.gemos.core.run.ChildRunSummary
import com.onexeor.gemos.core.run.RunRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BrainDispatcher(
    private val runs: RunRepository,
    private val json: Json,
) {
    fun dispatch(parentRunId: String, request: BrainRequest, decision: BrainDecisionResponse): ChildRunSummary? {
        val kind = when (decision.decision) {
            "run_pipeline" -> "pipeline"
            "use_code_agent" -> "code_agent"
            "plan_with_llm" -> "planner"
            "answer_from_context" -> "context_answer"
            else -> return null
        }

        val child = runs.createChildRun(
            parentRunId = parentRunId,
            kind = kind,
            userId = request.user,
            projectId = decision.projectId,
            pipelineId = decision.pipelineId,
            provider = decision.provider,
            route = decision.route,
            inputJson = json.encodeToString(decision),
        )

        return ChildRunSummary(
            id = child.id,
            kind = child.kind,
            status = child.status,
            route = child.route,
            provider = child.provider,
            pipelineId = child.pipelineId,
        )
    }
}
