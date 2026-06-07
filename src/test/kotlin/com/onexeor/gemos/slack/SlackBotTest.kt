package com.onexeor.gemos.slack

import com.onexeor.gemos.brain.BrainDecisionResponse
import com.onexeor.gemos.core.run.ChildRunSummary
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlackBotTest {
    @Test
    fun `rejects unsigned requests by default when signing secret is not configured`() {
        assertFalse(SlackSignatureVerifier.isValid(null, null, "{}", ""))
    }

    @Test
    fun `allows unsigned requests only when signature requirement is disabled`() {
        assertTrue(SlackSignatureVerifier.isValid(null, null, "{}", "", requireSignature = false))
    }

    @Test
    fun `rejects signed requests with missing signature`() {
        assertFalse(SlackSignatureVerifier.isValid("123", null, "{}", "secret"))
    }

    @Test
    fun `formats brain decision for Slack`() {
        val text = SlackResponseFormatter.format(
            BrainDecisionResponse(
                runId = "run_parent",
                childRun = ChildRunSummary(
                    id = "run_child",
                    kind = "pipeline",
                    status = "created",
                    route = "scheduler",
                    provider = null,
                ),
                status = "created",
                decision = "run_pipeline",
                route = "scheduler",
                projectId = "aso-fabric",
                pipelineId = "aso-monitor",
                reason = "Request matched pipeline.",
            ),
        )

        assertTrue(text.contains("`run_parent`"))
        assertTrue(text.contains("`run_child`"))
        assertTrue(text.contains("`aso-monitor`"))
    }

    @Test
    fun `uses brain reply text when present`() {
        val text = SlackResponseFormatter.format(
            BrainDecisionResponse(
                runId = "run_parent",
                replyText = "Friendly answer",
                decision = "show_help",
                route = "context",
                reason = "Help requested.",
            ),
        )

        assertTrue(text == "Friendly answer")
    }
}
