package com.onexeor.gemos.brain

import com.onexeor.gemos.core.ConfigLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrainDeciderTest {
    private val config = ConfigLoader.load(".")

    @Test
    fun delegatesAsoRequestsToPlannerWithScriptCatalog() {
        val decision = BrainDecider.decide(
            config,
            BrainRequest(user = "viktor", text = "run ASO check", projectHint = "aso"),
        )

        assertEquals("plan_with_llm", decision.decision)
        assertEquals("planner", decision.route)
        assertEquals("aso-fabric", decision.projectId)
        assertTrue(decision.executorText.orEmpty().contains("script"))
        assertTrue(decision.executorText.orEmpty().contains("aso-monitor"))
    }

    @Test
    fun delegatesCodeRequestsToPlannerWithCodeAgentMode() {
        val decision = BrainDecider.decide(
            config,
            BrainRequest(user = "viktor", text = "fix this bug in hopin", projectHint = "hopin"),
        )

        assertEquals("plan_with_llm", decision.decision)
        assertEquals("planner", decision.route)
        assertEquals("codex", decision.provider)
        assertTrue(decision.executorText.orEmpty().contains("code_agent"))
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
    fun answersCapabilityQuestionDirectly() {
        val decision = BrainDecider.decide(
            config,
            BrainRequest(user = "viktor", text = "What are you capable of?"),
        )

        assertEquals("show_help", decision.decision)
        assertEquals("context", decision.route)
        assertTrue(decision.replyText.orEmpty().contains("ASO"))
    }

    @Test
    fun answersModelQuestionDirectly() {
        val decision = BrainDecider.decide(
            config,
            BrainRequest(user = "viktor", text = "What is the model are you?"),
        )

        assertEquals("show_model", decision.decision)
        assertEquals("context", decision.route)
        val reply = decision.replyText.orEmpty()
        assertTrue(reply.contains("Gem OS Brain"))
        assertTrue(reply.contains("not one raw model"))
        assertTrue(reply.contains("BGE-M3") || reply.contains("bge-m3"))
    }

    @Test
    fun addsFriendlyReplyForPlannerRoute() {
        val decision = BrainDecider.withReply(
            config,
            BrainRequest(user = "viktor", text = "think about the next gem step"),
            BrainDecider.decide(config, BrainRequest(user = "viktor", text = "think about the next gem step")),
        )

        assertEquals("plan_with_llm", decision.decision)
        assertTrue(decision.replyText.orEmpty().contains("decide whether"))
    }

    @Test
    fun delegatesSmallTalkToPlannerForDirectAnswerDecision() {
        val request = BrainRequest(user = "viktor", text = "how are you?")
        val decision = BrainDecider.withReply(config, request, BrainDecider.decide(config, request))

        assertEquals("plan_with_llm", decision.decision)
        assertEquals("planner", decision.route)
        assertTrue(decision.executorText.orEmpty().contains("direct_answer"))
    }

    @Test
    fun answersShortFollowUpsFromThreadContext() {
        val context = listOf(
            BrainContextMessage(role = "user", text = "how are you"),
            BrainContextMessage(
                role = "assistant",
                text = "I'm running. Slack sessions and thread memory are active, but real execution is still being wired.",
            ),
        )

        val meaningRequest = BrainRequest(user = "viktor", text = "What do you mean", contextMessages = context)
        val meaningDecision = BrainDecider.withReply(config, meaningRequest, BrainDecider.decide(config, meaningRequest))
        assertEquals("plan_with_llm", meaningDecision.decision)
        assertEquals("planner", meaningDecision.route)
        assertTrue(meaningDecision.executorText.orEmpty().contains("Recent Slack context"))

        val whyRequest = BrainRequest(user = "viktor", text = "Why?", contextMessages = context)
        val whyDecision = BrainDecider.withReply(config, whyRequest, BrainDecider.decide(config, whyRequest))
        assertEquals("plan_with_llm", whyDecision.decision)
        assertTrue(whyDecision.executorText.orEmpty().contains("assistant: I'm running"))
    }

    @Test
    fun delegatesWeatherQuestionsWithLiveDataWarning() {
        val request = BrainRequest(user = "viktor", text = "Hello man how is the wether today in wroclaw")
        val decision = BrainDecider.withReply(config, request, BrainDecider.decide(config, request))

        assertEquals("plan_with_llm", decision.decision)
        assertEquals("planner", decision.route)
        assertTrue(decision.executorText.orEmpty().contains("live-data"))
        assertTrue(decision.executorText.orEmpty().contains("Do not pretend unavailable live-data tools exist"))
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
