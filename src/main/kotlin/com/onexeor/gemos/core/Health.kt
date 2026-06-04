package com.onexeor.gemos.core

import kotlinx.serialization.Serializable
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

private val startedAt = TimeSource.Monotonic.markNow()

@Serializable
data class HealthResponse(
    val service: String,
    val status: String = "ok",
    val version: String = "0.1.0",
    val uptimeSeconds: Double = startedAt.elapsedNow().toDouble(DurationUnit.SECONDS),
)

