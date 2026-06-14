package com.onexeor.gemos.slack

import com.onexeor.gemos.brain.BrainContextMessage
import com.onexeor.gemos.brain.BrainDecisionResponse
import com.onexeor.gemos.core.run.RunRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class CodexRunExecutor(
    private val http: HttpClient,
    private val slack: SlackApiClient,
    private val runs: RunRepository,
    private val scope: CoroutineScope,
    private val codexRunnerBaseUrl: String,
) {
    suspend fun execute(
        decision: BrainDecisionResponse,
        user: String,
        text: String,
        contextMessages: List<BrainContextMessage>,
        channel: String,
        threadTs: String,
    ) {
        val runId = decision.childRun?.id ?: decision.runId ?: return
        if (codexRunnerBaseUrl.isBlank()) {
            runs.appendEvent(runId, "warn", "Codex runner is not configured.", null)
            slack.postMessage(channel, "Codex runner is not configured yet. Set `CODEX_RUNNER_BASE_URL` to enable execution.", threadTs)
            return
        }

        val status = SlackRunStatusReporter(
            logger = org.slf4j.LoggerFactory.getLogger(CodexRunExecutor::class.java),
            slack = slack,
            channel = channel,
            threadTs = threadTs,
            runId = runId,
        )
        val heartbeat = status.start(scope)

        runCatching {
            runs.markRunning(runId)
            runs.appendEvent(runId, "info", "Calling Codex host runner.", null)
            status.markRunning()

            val response = http.post("$codexRunnerBaseUrl/codex/execute") {
                contentType(ContentType.Application.Json)
                setBody(
                    CodexRunnerRequest(
                        runId = runId,
                        user = user,
                        text = decision.executorText ?: text,
                        contextMessages = contextMessages,
                        projectId = decision.projectId,
                    ),
                )
            }.body<CodexRunnerResponse>()

            val payload = buildJsonObject {
                put("exitCode", response.exitCode)
                put("durationMs", response.durationMs)
                put("output", response.output.take(8000))
                put("stderr", response.stderr.take(4000))
            }.toString()

            if (response.ok) {
                runs.completeRun(runId, payload)
                heartbeat.cancel()
                status.finish(success = true)
                val reply = response.output.take(3000).ifBlank { "Codex run `$runId` completed." }
                slack.postMessage(channel, reply, threadTs)
            } else {
                runs.failRun(runId, "Codex runner failed with exit ${response.exitCode}.", payload)
                heartbeat.cancel()
                status.finish(success = false)
                slack.postMessage(channel, "Codex run `$runId` failed with exit `${response.exitCode}`.\n${response.stderr.take(2000)}", threadTs)
            }
        }.onFailure { error ->
            val message = error.message ?: error::class.simpleName.orEmpty()
            runs.failRun(runId, "Codex runner request failed: $message")
            heartbeat.cancel()
            status.finish(success = false)
            slack.postMessage(channel, "Codex run `$runId` failed: $message", threadTs)
        }
    }
}

@Serializable
private data class CodexRunnerRequest(
    val runId: String,
    val user: String,
    val text: String,
    val contextMessages: List<BrainContextMessage> = emptyList(),
    val projectId: String? = null,
)

@Serializable
private data class CodexRunnerResponse(
    val ok: Boolean,
    val exitCode: Int,
    val output: String = "",
    val stderr: String = "",
    val durationMs: Long = 0,
)
