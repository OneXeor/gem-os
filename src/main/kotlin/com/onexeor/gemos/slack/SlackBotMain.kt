package com.onexeor.gemos.slack

import com.onexeor.gemos.brain.BrainDecisionResponse
import com.onexeor.gemos.brain.BrainRequest
import com.onexeor.gemos.core.ConfigLoader
import com.onexeor.gemos.core.HealthResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Serializable
private data class SlackUrlVerification(
    val challenge: String,
)

@Serializable
private data class SlackEventEnvelope(
    val type: String,
    val challenge: String? = null,
    val event: SlackEvent? = null,
)

@Serializable
private data class SlackEvent(
    val type: String,
    val user: String? = null,
    val text: String? = null,
    val channel: String? = null,
    @SerialName("event_ts")
    val eventTs: String? = null,
    @SerialName("thread_ts")
    val threadTs: String? = null,
    @SerialName("bot_id")
    val botId: String? = null,
    val subtype: String? = null,
)

@Serializable
private data class SlackPostMessageRequest(
    val channel: String,
    val text: String,
    @SerialName("thread_ts")
    val threadTs: String? = null,
)

@Serializable
private data class SlackApiResponse(
    val ok: Boolean,
    val error: String? = null,
)

fun main() {
    val cfg = ConfigLoader.load()
    val slackPort = (System.getenv("SLACK_PORT") ?: "8030").toInt()
    val brainBaseUrl = System.getenv("BRAIN_BASE_URL") ?: "http://brain:${cfg.settings.brainPort}"
    val botToken = System.getenv("SLACK_BOT_TOKEN").orEmpty()
    val signingSecret = System.getenv("SLACK_SIGNING_SECRET").orEmpty()
    val requireSignature = (System.getenv("SLACK_REQUIRE_SIGNATURE") ?: "true").toBooleanStrictOrNull() ?: true
    val allowedUsers = System.getenv("SLACK_ALLOWED_USERS")
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.toSet()
        .orEmpty()
    val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    embeddedServer(Netty, host = "0.0.0.0", port = slackPort) {
        install(ServerContentNegotiation) {
            json(json)
        }
        install(MicrometerMetrics) {
            registry = prometheus
        }
        routing {
            get("/health") {
                call.respond(HealthResponse(service = "slack-bot"))
            }
            get("/metrics") {
                call.respondText(prometheus.scrape())
            }
            post("/slack/events") {
                val body = call.receiveText()
                if (!SlackSignatureVerifier.isValid(call.request.header("X-Slack-Request-Timestamp"), call.request.header("X-Slack-Signature"), body, signingSecret, requireSignature)) {
                    call.respondText("invalid signature", status = HttpStatusCode.Unauthorized)
                    return@post
                }

                val envelope = json.decodeFromString<SlackEventEnvelope>(body)
                if (envelope.type == "url_verification") {
                    call.respond(SlackUrlVerification(envelope.challenge.orEmpty()))
                    return@post
                }

                val event = envelope.event
                if (envelope.type != "event_callback" || event == null || event.shouldIgnore()) {
                    call.respondText("ok")
                    return@post
                }

                val user = event.user
                val channel = event.channel
                val text = event.text?.stripBotMention()?.trim().orEmpty()
                if (user.isNullOrBlank() || channel.isNullOrBlank() || text.isBlank()) {
                    call.respondText("ok")
                    return@post
                }

                if (allowedUsers.isNotEmpty() && user !in allowedUsers) {
                    sendSlackMessage(http, botToken, channel, "Gem is not enabled for this Slack user yet.", event.threadTs ?: event.eventTs)
                    call.respondText("ok")
                    return@post
                }

                val decision = http.post("$brainBaseUrl/decide") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        BrainRequest(
                            user = user,
                            text = text,
                            threadId = event.threadTs ?: event.eventTs,
                        ),
                    )
                }.body<BrainDecisionResponse>()

                sendSlackMessage(http, botToken, channel, SlackResponseFormatter.format(decision), event.threadTs ?: event.eventTs)
                call.respondText("ok")
            }
        }
    }.start(wait = true)
}

private fun SlackEvent.shouldIgnore(): Boolean =
    botId != null || subtype == "bot_message" || type !in setOf("message", "app_mention")

private fun String.stripBotMention(): String =
    replace(Regex("<@[A-Z0-9]+>"), " ")

private suspend fun sendSlackMessage(
    http: HttpClient,
    botToken: String,
    channel: String,
    text: String,
    threadTs: String?,
) {
    if (botToken.isBlank()) return

    val response = http.post("https://slack.com/api/chat.postMessage") {
        bearerAuth(botToken)
        contentType(ContentType.Application.Json)
        setBody(SlackPostMessageRequest(channel = channel, text = text, threadTs = threadTs))
    }.body<SlackApiResponse>()

    if (!response.ok) {
        error("Slack chat.postMessage failed: ${response.error ?: "unknown error"}")
    }
}

object SlackResponseFormatter {
    fun format(decision: BrainDecisionResponse): String {
        val lines = mutableListOf<String>()
        lines += "Gem created run `${decision.runId ?: "unknown"}`."
        lines += "Decision: `${decision.decision}` via `${decision.route}`."
        decision.projectId?.let { lines += "Project: `$it`." }
        decision.pipelineId?.let { lines += "Pipeline: `$it`." }
        decision.provider?.let { lines += "Provider: `$it`." }
        decision.childRun?.let { child ->
            lines += "Child run: `${child.id}` (`${child.kind}`, `${child.status}`)."
        }
        lines += "Reason: ${decision.reason}"
        return lines.joinToString("\n")
    }
}

object SlackSignatureVerifier {
    fun isValid(
        timestamp: String?,
        signature: String?,
        body: String,
        signingSecret: String,
        requireSignature: Boolean = true,
    ): Boolean {
        if (signingSecret.isBlank()) return !requireSignature
        if (timestamp.isNullOrBlank() || signature.isNullOrBlank()) return false

        val now = Instant.now().epochSecond
        val requestTime = timestamp.toLongOrNull() ?: return false
        if (kotlin.math.abs(now - requestTime) > 60 * 5) return false

        val base = "v0:$timestamp:$body"
        val expected = "v0=" + hmacSha256(signingSecret, base)
        return constantTimeEquals(expected, signature)
    }

    private fun hmacSha256(secret: String, value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(left: String, right: String): Boolean {
        if (left.length != right.length) return false
        var result = 0
        for (i in left.indices) {
            result = result or (left[i].code xor right[i].code)
        }
        return result == 0
    }
}
