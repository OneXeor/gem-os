package com.onexeor.gemos.scheduler

import com.onexeor.gemos.core.ConfigLoader
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SchedulerStatusResponse(
    val service: String,
    val status: String,
    val pipelinesTotal: Int,
    val pipelinesEnabled: Int,
    val enabledPipelineIds: List<String>,
)

fun main(args: Array<String>) {
    val cfg = ConfigLoader.load()
    val enabled = cfg.pipelines.pipelines.filter { it.enabled }
    val status = SchedulerStatusResponse(
        service = "scheduler",
        status = "ok",
        pipelinesTotal = cfg.pipelines.pipelines.size,
        pipelinesEnabled = enabled.size,
        enabledPipelineIds = enabled.map { it.id },
    )

    val json = Json { prettyPrint = true }
    if ("--list" in args) {
        println(json.encodeToString(cfg.pipelines.pipelines.map { it.id }))
    } else {
        println(json.encodeToString(status))
    }
}
