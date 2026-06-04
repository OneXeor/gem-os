package com.onexeor.gemos.core.run

import kotlinx.serialization.Serializable

@Serializable
data class RunRecord(
    val id: String,
    val parentRunId: String? = null,
    val kind: String,
    val status: String,
    val userId: String? = null,
    val projectId: String? = null,
    val pipelineId: String? = null,
    val provider: String? = null,
    val route: String? = null,
    val inputJson: String? = null,
    val decisionJson: String? = null,
    val resultJson: String? = null,
    val error: String? = null,
    val createdAt: String,
    val startedAt: String? = null,
    val finishedAt: String? = null,
)

@Serializable
data class ChildRunSummary(
    val id: String,
    val kind: String,
    val status: String,
    val route: String? = null,
    val provider: String? = null,
    val pipelineId: String? = null,
)

@Serializable
data class RunEventRecord(
    val id: Long,
    val runId: String,
    val level: String,
    val message: String,
    val payloadJson: String? = null,
    val createdAt: String,
)
