package com.onexeor.gemos.brain

import com.onexeor.gemos.core.ConfigLoader
import com.onexeor.gemos.core.HealthResponse
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.serialization.json.Json

fun main() {
    val cfg = ConfigLoader.load()
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    embeddedServer(Netty, host = "0.0.0.0", port = cfg.settings.brainPort) {
        install(ContentNegotiation) {
            json(Json { prettyPrint = false })
        }
        install(MicrometerMetrics) {
            registry = prometheus
        }
        routing {
            get("/health") {
                call.respond(HealthResponse(service = "brain"))
            }
            post("/decide") {
                val request = call.receive<BrainRequest>()
                call.respond(BrainDecider.decide(ConfigLoader.load(), request))
            }
            get("/metrics") {
                call.respondText(prometheus.scrape())
            }
        }
    }.start(wait = true)
}
