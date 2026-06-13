package com.onexeor.gemos.slack

import com.onexeor.gemos.brain.BrainContextMessage
import com.onexeor.gemos.brain.BrainDecisionResponse
import com.onexeor.gemos.brain.BrainRequest
import com.onexeor.gemos.core.memory.SlackMessageDirection
import com.onexeor.gemos.core.memory.SlackThreadMemoryRepository
import com.onexeor.gemos.core.memory.SlackThreadSessionRecord
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class SlackEventHandler(
    private val logger: org.slf4j.Logger,
    private val http: HttpClient,
    private val slack: SlackApiClient,
    private val brainBaseUrl: String,
    private val codexRunExecutor: CodexRunExecutor,
    private val allowedUsers: Set<String>,
    private val memory: SlackThreadMemoryRepository,
    private val scope: CoroutineScope,
) {
    suspend fun handle(envelope: SlackEventEnvelope) {
        val event = envelope.event
        if (envelope.type != "event_callback" || event == null || event.shouldIgnore()) {
            logger.info("Ignored Slack event type={} eventType={} subtype={} botIdPresent={}", envelope.type, event?.type, event?.subtype, event?.botId != null)
            return
        }

        val user = event.user
        val channel = event.channel
        val text = event.text?.stripBotMention()?.trim().orEmpty()
        if (user.isNullOrBlank() || channel.isNullOrBlank() || text.isBlank()) {
            logger.info("Ignored Slack event with missing user/channel/text")
            return
        }

        logger.info("Accepted Slack event type={} user={} channel={} thread={}", event.type, user, channel, event.threadTs ?: event.eventTs)
        val threadTs = event.threadTs ?: event.eventTs

        if (allowedUsers.isNotEmpty() && user !in allowedUsers) {
            logger.warn("Rejected Slack user {} because they are not in SLACK_ALLOWED_USERS", user)
            slack.postMessage(channel, "Gem is not enabled for this Slack user yet.", threadTs)
            return
        }

        if (threadTs != null && text.isResetSessionCommand()) {
            val closed = closeSession(channel, threadTs)
            val reply = if (closed == null) {
                "There is no active Gem session in this Slack thread."
            } else {
                "Closed Gem session `${closed.id}`. The next message in this thread will start a fresh session."
            }
            slack.postMessage(channel, reply, threadTs)
            return
        }

        val session = if (threadTs == null) null else getOrCreateSession(channel, threadTs, user)

        if (threadTs != null && session != null) {
            rememberMessage(session.id, channel, threadTs, event.eventTs, user, SlackMessageDirection.USER, text)
        }

        if (threadTs != null && text.isSessionCommand()) {
            val reply = session?.toSlackText() ?: "There is no active Gem session in this Slack thread."
            if (session != null) {
                rememberMessage(session.id, channel, threadTs, null, null, SlackMessageDirection.GEM, reply)
            }
            slack.postMessage(channel, reply, threadTs)
            return
        }

        val contextMessages = session?.let { loadContextMessages(it.id) }.orEmpty()
        val decision = http.post("$brainBaseUrl/decide") {
            contentType(ContentType.Application.Json)
            setBody(
                BrainRequest(
                    user = user,
                    text = text,
                    threadId = threadTs,
                    contextMessages = contextMessages,
                ),
            )
        }.body<BrainDecisionResponse>()

        val reply = SlackResponseFormatter.format(decision)
        logger.info("Brain decision created run={} decision={} route={} childRun={}", decision.runId, decision.decision, decision.route, decision.childRun?.id)
        if (session != null) {
            updateSession(session.id, decision)
        }
        if (threadTs != null && session != null) {
            rememberMessage(session.id, channel, threadTs, null, null, SlackMessageDirection.GEM, reply, decision.runId)
        }
        slack.postMessage(channel, reply, threadTs)

        if (threadTs != null && decision.shouldRunCodex()) {
            scope.launch {
                codexRunExecutor.execute(
                    decision = decision,
                    user = user,
                    text = text,
                    contextMessages = contextMessages,
                    channel = channel,
                    threadTs = threadTs,
                )
            }
        }
    }

    private fun getOrCreateSession(channel: String, threadTs: String, user: String): SlackThreadSessionRecord? =
        runCatching {
            memory.getOrCreateSession(channelId = channel, threadTs = threadTs, ownerUserId = user)
        }.onFailure {
            logger.warn("Failed to get/create Slack thread session channel={} thread={}: {}", channel, threadTs, it.message ?: it::class.simpleName.orEmpty())
        }.getOrNull()

    private fun closeSession(channel: String, threadTs: String): SlackThreadSessionRecord? =
        runCatching {
            memory.closeActiveSession(channelId = channel, threadTs = threadTs)
        }.onFailure {
            logger.warn("Failed to close Slack thread session channel={} thread={}: {}", channel, threadTs, it.message ?: it::class.simpleName.orEmpty())
        }.getOrNull()

    private fun updateSession(sessionId: String, decision: BrainDecisionResponse) {
        runCatching {
            memory.updateSessionAfterDecision(
                sessionId = sessionId,
                runId = decision.runId,
                decision = decision.decision,
                route = decision.route,
                projectId = decision.projectId,
            )
        }.onFailure {
            logger.warn("Failed to update Slack thread session id={}: {}", sessionId, it.message ?: it::class.simpleName.orEmpty())
        }
    }

    private fun loadContextMessages(sessionId: String): List<BrainContextMessage> =
        runCatching {
            memory.listRecentMessages(sessionId = sessionId).map { message ->
                BrainContextMessage(
                    role = if (message.direction == SlackMessageDirection.GEM.value) "assistant" else "user",
                    text = message.text,
                )
            }
        }.onFailure {
            logger.warn("Failed to load Slack thread context session={}: {}", sessionId, it.message ?: it::class.simpleName.orEmpty())
        }.getOrDefault(emptyList())

    private fun rememberMessage(
        sessionId: String?,
        channel: String,
        threadTs: String,
        eventTs: String?,
        user: String?,
        direction: SlackMessageDirection,
        text: String,
        brainRunId: String? = null,
    ) {
        runCatching {
            memory.storeMessage(
                sessionId = sessionId,
                channelId = channel,
                threadTs = threadTs,
                eventTs = eventTs,
                userId = user,
                direction = direction,
                text = text,
                brainRunId = brainRunId,
            )
        }.onFailure {
            logger.warn("Failed to store Slack thread memory direction={} channel={} thread={}: {}", direction.value, channel, threadTs, it.message ?: it::class.simpleName.orEmpty())
        }
    }
}

private fun BrainDecisionResponse.shouldRunCodex(): Boolean =
    decision in setOf("use_code_agent", "plan_with_llm")

private fun String.isSessionCommand(): Boolean =
    trim().lowercase() in setOf("session", "/session")

private fun String.isResetSessionCommand(): Boolean =
    trim().lowercase() in setOf("reset session", "/reset session")

private fun SlackThreadSessionRecord.toSlackText(): String =
    listOfNotNull(
        "Gem session `${id}`",
        "- status: `$status`",
        "- owner: `${ownerUserId ?: "unknown"}`",
        currentRunId?.let { "- current run: `$it`" },
        lastDecision?.let { "- last decision: `$it`" },
        lastRoute?.let { "- last route: `$it`" },
        lastProjectId?.let { "- last project: `$it`" },
    ).joinToString("\n")
