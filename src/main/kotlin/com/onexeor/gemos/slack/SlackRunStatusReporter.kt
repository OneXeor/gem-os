package com.onexeor.gemos.slack

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class SlackRunStatusReporter(
    private val logger: org.slf4j.Logger,
    private val slack: SlackApiClient,
    private val channel: String,
    private val threadTs: String,
    private val runId: String,
) {
    private val startedAtMs = System.currentTimeMillis()
    private var heartbeatTs: String? = null
    private var currentStatus: String = QUEUED_STATUS

    fun start(scope: CoroutineScope): Job =
        scope.launch {
            slack.setAssistantStatus(channel, threadTs, currentStatus)
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                heartbeat()
            }
        }

    suspend fun markRunning() {
        currentStatus = RUNNING_STATUS
        slack.setAssistantStatus(channel, threadTs, currentStatus)
    }

    suspend fun finish(success: Boolean) {
        runCatching {
            slack.setAssistantStatus(channel, threadTs, "")
            val existingTs = heartbeatTs ?: return
            if (success) {
                slack.deleteMessage(channel, existingTs)
            } else {
                slack.updateMessage(channel, existingTs, format(System.currentTimeMillis() - startedAtMs, final = true))
            }
        }.onFailure {
            logger.debug("Slack run status cleanup failed: {}", it.message ?: it::class.simpleName.orEmpty())
        }
    }

    private suspend fun heartbeat() {
        slack.setAssistantStatus(channel, threadTs, currentStatus)
        val elapsedMs = System.currentTimeMillis() - startedAtMs
        if (elapsedMs < HEARTBEAT_VISIBLE_AFTER_MS) return

        val text = format(elapsedMs)
        val existingTs = heartbeatTs
        heartbeatTs = if (existingTs == null) {
            slack.postMessage(channel, text, threadTs)
        } else {
            slack.updateMessage(channel, existingTs, text)
            existingTs
        }
    }

    private fun format(elapsedMs: Long, final: Boolean = false): String {
        val elapsedSeconds = (elapsedMs / 1000).coerceAtLeast(1)
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
        val elapsed = if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
        val header = if (final) ":warning: *Codex run stopped*" else ":hourglass_flowing_sand: *Codex is working...*"
        return "$header `$runId`\n*now:* thinking or running tools\n_elapsed: ${elapsed}_"
    }

    companion object {
        private const val QUEUED_STATUS = "is queued..."
        private const val RUNNING_STATUS = "is running Codex..."
        private const val HEARTBEAT_INTERVAL_MS = 20_000L
        private const val HEARTBEAT_VISIBLE_AFTER_MS = 25_000L
    }
}
