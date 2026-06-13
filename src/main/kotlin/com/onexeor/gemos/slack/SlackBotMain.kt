package com.onexeor.gemos.slack

import com.onexeor.gemos.brain.BrainDecisionResponse
import com.onexeor.gemos.core.ConfigLoader
import com.onexeor.gemos.core.HealthResponse
import com.onexeor.gemos.core.memory.SlackThreadMemoryRepository
import com.onexeor.gemos.core.run.RunRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import io.ktor.websocket.Frame
import io.ktor.websocket.readText

@Serializable
private data class SlackUrlVerification(
    val challenge: String,
)

@Serializable
internal data class SlackEventEnvelope(
    val type: String,
    val challenge: String? = null,
    val event: SlackEvent? = null,
)

@Serializable
internal data class SlackEvent(
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
private data class SlackSocketConnectionResponse(
    val ok: Boolean,
    val url: String? = null,
    val error: String? = null,
)

@Serializable
private data class SlackSocketMessage(
    val type: String,
    @SerialName("envelope_id")
    val envelopeId: String? = null,
    val payload: SlackEventEnvelope? = null,
    val reason: String? = null,
)

@Serializable
private data class SlackSocketAck(
    @SerialName("envelope_id")
    val envelopeId: String,
)

fun main() {
    val logger = LoggerFactory.getLogger("com.onexeor.gemos.slack.SlackBot")
    val cfg = ConfigLoader.load()
    val slackPort = (System.getenv("SLACK_PORT") ?: "8030").toInt()
    val brainBaseUrl = System.getenv("BRAIN_BASE_URL") ?: "http://brain:${cfg.settings.brainPort}"
    val codexRunnerBaseUrl = System.getenv("CODEX_RUNNER_BASE_URL").orEmpty().trimEnd('/')
    val codexRunnerTimeoutMs = (System.getenv("CODEX_RUNNER_TIMEOUT_MS") ?: "900000").toLong()
    val botToken = System.getenv("SLACK_BOT_TOKEN").orEmpty()
    val appToken = System.getenv("SLACK_APP_TOKEN").orEmpty()
    val socketMode = (System.getenv("SLACK_SOCKET_MODE") ?: "false").toBooleanStrictOrNull() ?: false
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
    val memory = SlackThreadMemoryRepository(cfg.settings)
    runCatching { memory.migrate() }
        .onFailure { logger.error("Slack thread memory migration failed: {}", it.message ?: it::class.simpleName.orEmpty()) }
    val runs = RunRepository(cfg.settings)
    val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = codexRunnerTimeoutMs
            socketTimeoutMillis = codexRunnerTimeoutMs
            connectTimeoutMillis = 30_000
        }
        install(WebSockets)
    }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val slack = SlackApiClient(logger = logger, http = http, botToken = botToken)
    val codexRunExecutor = CodexRunExecutor(
        http = http,
        slack = slack,
        runs = runs,
        scope = scope,
        codexRunnerBaseUrl = codexRunnerBaseUrl,
    )
    val handler = SlackEventHandler(
        logger = logger,
        http = http,
        slack = slack,
        brainBaseUrl = brainBaseUrl,
        codexRunExecutor = codexRunExecutor,
        allowedUsers = allowedUsers,
        memory = memory,
        scope = scope,
    )

    if (socketMode) {
        scope.launch {
            SlackSocketModeRunner(
                logger = logger,
                http = http,
                json = json,
                appToken = appToken,
                handler = handler,
            ).runForever()
        }
    } else {
        logger.info("Slack Socket Mode disabled; HTTP Events API endpoint is active")
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
                    logger.warn("Rejected Slack event: invalid signature")
                    call.respondText("invalid signature", status = HttpStatusCode.Unauthorized)
                    return@post
                }

                val envelope = json.decodeFromString<SlackEventEnvelope>(body)
                if (envelope.type == "url_verification") {
                    logger.info("Accepted Slack URL verification")
                    call.respond(SlackUrlVerification(envelope.challenge.orEmpty()))
                    return@post
                }

                handler.handle(envelope)
                call.respondText("ok")
            }
        }
    }.start(wait = true)
}

private class SlackSocketModeRunner(
    private val logger: org.slf4j.Logger,
    private val http: HttpClient,
    private val json: Json,
    private val appToken: String,
    private val handler: SlackEventHandler,
) {
    suspend fun runForever() {
        if (appToken.isBlank()) {
            logger.error("Slack Socket Mode enabled but SLACK_APP_TOKEN is blank")
            return
        }

        while (true) {
            runCatching {
                val url = openConnection()
                logger.info("Slack Socket Mode connecting")
                http.webSocket(urlString = url) {
                    logger.info("Slack Socket Mode connected")
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        handleSocketMessage(frame.readText())
                    }
                }
            }.onFailure { error ->
                logger.error("Slack Socket Mode connection failed: {}", error.message ?: error::class.simpleName.orEmpty())
            }
            delay(5_000)
        }
    }

    private suspend fun openConnection(): String {
        val response = http.post("https://slack.com/api/apps.connections.open") {
            bearerAuth(appToken)
        }
        val body = response.bodyAsText()
        val parsed = json.decodeFromString<SlackSocketConnectionResponse>(body)
        check(parsed.ok && !parsed.url.isNullOrBlank()) {
            "apps.connections.open failed: ${parsed.error ?: body}"
        }
        return parsed.url
    }

    private suspend fun io.ktor.client.plugins.websocket.DefaultClientWebSocketSession.handleSocketMessage(text: String) {
        val message = json.decodeFromString<SlackSocketMessage>(text)
        when (message.type) {
            "hello" -> logger.info("Slack Socket Mode hello received")
            "disconnect" -> logger.warn("Slack Socket Mode disconnect requested: {}", message.reason ?: "unknown")
            "events_api" -> {
                val envelopeId = message.envelopeId
                if (envelopeId != null) {
                    send(Frame.Text(json.encodeToString(SlackSocketAck(envelopeId))))
                }
                val payload = message.payload
                if (payload == null) {
                    logger.warn("Slack Socket Mode events_api payload missing")
                } else {
                    handler.handle(payload)
                }
            }
            else -> logger.info("Ignored Slack Socket Mode message type={}", message.type)
        }
    }
}

internal fun SlackEvent.shouldIgnore(): Boolean =
    botId != null || subtype == "bot_message" || type !in setOf("message", "app_mention")

internal fun String.stripBotMention(): String =
    replace(Regex("<@[A-Z0-9]+>"), " ")

object SlackResponseFormatter {
    fun format(decision: BrainDecisionResponse): String {
        decision.replyText?.takeIf { it.isNotBlank() }?.let { return it }

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
