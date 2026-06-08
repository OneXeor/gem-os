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
    fun answersHelpCommandDirectly() {
        val decision = BrainDecider.decide(config, BrainRequest(user = "viktor", text = "help"))

        assertEquals("show_help", decision.decision)
        assertEquals("context", decision.route)
        val reply = decision.replyText.orEmpty()
        assertTrue(reply.contains("`projects`"))
        assertTrue(reply.contains("`session`"))
        assertTrue(reply.contains("normal messages"))
    }

    @Test
    fun addsFriendlyReplyForPlannerRoute() {
        val decision = BrainDecider.withReply(
            config,
            BrainRequest(user = "viktor", text = "think about the next gem step"),
            BrainDecider.decide(config, BrainRequest(user = "viktor", text = "think about the next gem step")),
        )

        assertEquals("plan_with_llm", decision.decision)
        assertTrue(decision.replyText.orEmpty().contains("created the planning run"))
    }

    @Test
    fun answersSmallTalkWithoutPlanner() {
        val request = BrainRequest(user = "viktor", text = "how are you?")
        val decision = BrainDecider.withReply(config, request, BrainDecider.decide(config, request))

        assertEquals("answer_small_talk", decision.decision)
        assertEquals("chat", decision.route)
        assertTrue(decision.replyText.orEmpty().contains("running"))
    }

    @Test
    fun continuesFromThreadContext() {
        val decision = BrainDecider.decide(
            config,
            BrainRequest(
                user = "viktor",
                text = "continue",
                contextMessages = listOf(
                    BrainContextMessage(role = "user", text = "build the thread session store"),
                    BrainContextMessage(role = "assistant", text = "I will wire that next."),
                ),
            ),
        )

        assertEquals("continue_session", decision.decision)
        assertTrue(decision.replyText.orEmpty().contains("build the thread session store"))
    }

    @Test
    fun plannerHasNoClaudeFallbackByDefault() {
        assertNull(config.providers.providers.orchestration.plannerFallback)
    }
}
