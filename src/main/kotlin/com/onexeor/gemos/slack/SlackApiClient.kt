package com.onexeor.gemos.slack

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal class SlackApiClient(
    private val logger: org.slf4j.Logger,
    private val http: HttpClient,
    private val botToken: String,
) {
    suspend fun postMessage(channel: String, text: String, threadTs: String? = null): String? {
        if (botToken.isBlank()) {
            logger.warn("Skipped Slack reply because SLACK_BOT_TOKEN is blank")
            return null
        }

        val response = http.post("https://slack.com/api/chat.postMessage") {
            bearerAuth(botToken)
            contentType(ContentType.Application.Json)
            setBody(SlackPostMessageRequest(channel = channel, text = text, threadTs = threadTs))
        }.body<SlackApiResponse>()

        return if (response.ok) {
            logger.info("Posted Slack reply to channel={}", channel)
            response.ts
        } else {
            logger.error("Slack chat.postMessage failed for channel={}: {}", channel, response.error ?: "unknown error")
            null
        }
    }

    suspend fun updateMessage(channel: String, ts: String, text: String) {
        if (botToken.isBlank()) return

        val response = http.post("https://slack.com/api/chat.update") {
            bearerAuth(botToken)
            contentType(ContentType.Application.Json)
            setBody(SlackUpdateMessageRequest(channel = channel, ts = ts, text = text))
        }.body<SlackApiResponse>()

        if (!response.ok) {
            logger.warn("Slack chat.update failed for channel={}: {}", channel, response.error ?: "unknown error")
        }
    }

    suspend fun deleteMessage(channel: String, ts: String) {
        if (botToken.isBlank()) return

        val response = http.post("https://slack.com/api/chat.delete") {
            bearerAuth(botToken)
            contentType(ContentType.Application.Json)
            setBody(SlackDeleteMessageRequest(channel = channel, ts = ts))
        }.body<SlackApiResponse>()

        if (!response.ok) {
            logger.debug("Slack chat.delete failed for channel={}: {}", channel, response.error ?: "unknown error")
        }
    }

    suspend fun setAssistantStatus(channel: String, threadTs: String, status: String) {
        if (botToken.isBlank()) return

        runCatching {
            http.post("https://slack.com/api/assistant.threads.setStatus") {
                bearerAuth(botToken)
                contentType(ContentType.Application.Json)
                setBody(SlackAssistantStatusRequest(channelId = channel, threadTs = threadTs, status = status))
            }.body<SlackApiResponse>()
        }.onFailure {
            logger.debug("Slack assistant.threads.setStatus failed: {}", it.message ?: it::class.simpleName.orEmpty())
        }
    }
}

@Serializable
private data class SlackPostMessageRequest(
    val channel: String,
    val text: String,
    @SerialName("thread_ts")
    val threadTs: String? = null,
    val mrkdwn: Boolean = true,
)

@Serializable
private data class SlackUpdateMessageRequest(
    val channel: String,
    val ts: String,
    val text: String,
    val mrkdwn: Boolean = true,
)

@Serializable
private data class SlackDeleteMessageRequest(
    val channel: String,
    val ts: String,
)

@Serializable
private data class SlackAssistantStatusRequest(
    @SerialName("channel_id")
    val channelId: String,
    @SerialName("thread_ts")
    val threadTs: String,
    val status: String,
)

@Serializable
private data class SlackApiResponse(
    val ok: Boolean,
    val error: String? = null,
    val ts: String? = null,
)
