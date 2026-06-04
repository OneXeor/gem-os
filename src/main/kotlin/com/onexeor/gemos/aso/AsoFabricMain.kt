package com.onexeor.gemos.aso

import com.onexeor.gemos.core.ConfigLoader
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

@Serializable
private data class AsoStubResponse(
    val pipeline: String,
    val mode: String,
    val status: String,
    val projectId: String,
    val projectName: String,
    val createdAt: String,
)

fun main() {
    val cfg = ConfigLoader.load()
    val project = cfg.projects.projects.firstOrNull { it.id == "aso-fabric" }
    val result = AsoStubResponse(
        pipeline = "aso-monitor",
        mode = "report-only",
        status = "stub",
        projectId = project?.id ?: "missing",
        projectName = project?.name ?: "missing",
        createdAt = Instant.now().toString(),
    )
    val json = Json { prettyPrint = true }
    println(json.encodeToString(result))
}
