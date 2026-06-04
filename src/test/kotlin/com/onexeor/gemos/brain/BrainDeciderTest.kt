package com.onexeor.gemos.brain

import com.onexeor.gemos.core.ConfigLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrainDeciderTest {
    private val config = ConfigLoader.load(".")

    @Test
    fun routesAsoRequestsToAsoPipeline() {
        val decision = BrainDecider.decide(
            config,
            BrainRequest(user = "viktor", text = "run ASO check", projectHint = "aso"),
        )

        assertEquals("run_pipeline", decision.decision)
        assertEquals("scheduler", decision.route)
        assertEquals("aso-fabric", decision.projectId)
        assertEquals("aso-monitor", decision.pipelineId)
    }

    @Test
    fun routesCodeRequestsToCodexAgent() {
        val decision = BrainDecider.decide(
            config,
            BrainRequest(user = "viktor", text = "fix this bug in hopin", projectHint = "hopin"),
        )

        assertEquals("use_code_agent", decision.decision)
        assertEquals("code_agent", decision.route)
        assertEquals("codex", decision.provider)
    }

    @Test
    fun asksForClarificationOnEmptyRequest() {
        val decision = BrainDecider.decide(config, BrainRequest(user = "viktor", text = " "))

        assertEquals("ask_clarification", decision.decision)
        assertTrue(decision.needsClarification)
    }

    @Test
    fun plannerHasNoClaudeFallbackByDefault() {
        assertNull(config.providers.providers.orchestration.plannerFallback)
    }
}
