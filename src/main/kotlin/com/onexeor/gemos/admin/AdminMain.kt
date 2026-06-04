package com.onexeor.gemos.admin

import com.onexeor.gemos.core.ConfigLoader
import com.onexeor.gemos.core.HealthResponse
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class ProviderDefaultsResponse(
    val orchestrationMode: String,
    val plannerDefault: String,
    val plannerFallback: String?,
    val chatDefault: String,
    val codeAgentDefault: String,
)

@Serializable
private data class AdminStatusResponse(
    val service: String,
    val env: String,
    val gem: String,
    val projects: Int,
    val pipelines: Int,
    val brain: BrainStatusResponse,
    val providers: ProviderDefaultsResponse,
)

@Serializable
private data class BrainStatusResponse(
    val mode: String,
    val port: Int,
)

fun main() {
    val cfg = ConfigLoader.load()
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    embeddedServer(Netty, host = "0.0.0.0", port = cfg.settings.adminPort) {
        install(ContentNegotiation) {
            json(Json { prettyPrint = false })
        }
        install(MicrometerMetrics) {
            registry = prometheus
        }
        routing {
            get("/health") {
                call.respond(HealthResponse(service = "admin"))
            }
            get("/status") {
                val current = ConfigLoader.load()
                call.respond(
                    AdminStatusResponse(
                        service = "admin",
                        env = current.settings.gemEnv,
                        gem = current.identity.gem.name,
                        projects = current.projects.projects.size,
                        pipelines = current.pipelines.pipelines.size,
                        brain = BrainStatusResponse(
                            mode = current.providers.providers.orchestration.mode,
                            port = current.settings.brainPort,
                        ),
                        providers = ProviderDefaultsResponse(
                            orchestrationMode = current.providers.providers.orchestration.mode,
                            plannerDefault = current.providers.providers.orchestration.plannerDefault,
                            plannerFallback = current.providers.providers.orchestration.plannerFallback,
                            chatDefault = current.providers.providers.chat.default,
                            codeAgentDefault = current.providers.providers.codeAgent.default,
                        ),
                    ),
                )
            }
            get("/projects") {
                call.respond(ConfigLoader.load().projects)
            }
            get("/pipelines") {
                call.respond(ConfigLoader.load().pipelines)
            }
            get("/metrics") {
                call.respondText(prometheus.scrape())
            }
        }
    }.start(wait = true)
}
