package com.onexeor.gemos.provider

import com.onexeor.gemos.core.ConfigLoader
import com.onexeor.gemos.core.HealthResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class LiteLlmHealthResponse(
    val status: String,
    val baseUrl: String,
    val httpStatus: Int? = null,
    val error: String? = null,
)

@Serializable
private data class CodeAgentProviderResponse(
    val id: String,
    val mode: String,
    val command: String?,
    val auth: String,
    val nonInteractive: Boolean,
    val defaultArgs: List<String>,
    val defaultModel: String,
    val route: String = "direct",
)

@Serializable
private data class CodeAgentProvidersResponse(
    val orchestrationMode: String,
    val plannerDefault: String,
    val plannerFallback: String?,
    val default: String,
    val providers: List<CodeAgentProviderResponse>,
)

fun main() {
    val cfg = ConfigLoader.load()
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val client = HttpClient(CIO) {
        install(ClientContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    embeddedServer(Netty, host = "0.0.0.0", port = cfg.settings.providerRouterPort) {
        install(ContentNegotiation) {
            json(Json { prettyPrint = false })
        }
        install(MicrometerMetrics) {
            registry = prometheus
        }
        routing {
            get("/health") {
                call.respond(HealthResponse(service = "provider-router"))
            }
            get("/providers") {
                call.respond(ConfigLoader.load().providers)
            }
            get("/providers/code-agents") {
                val providers = ConfigLoader.load().providers.providers
                val codeAgents = providers.codeAgent
                call.respond(
                    CodeAgentProvidersResponse(
                        orchestrationMode = providers.orchestration.mode,
                        plannerDefault = providers.orchestration.plannerDefault,
                        plannerFallback = providers.orchestration.plannerFallback,
                        default = codeAgents.default,
                        providers = codeAgents.options.map { (id, option) ->
                            CodeAgentProviderResponse(
                                id = id,
                                mode = option.mode,
                                command = option.command,
                                auth = option.auth,
                                nonInteractive = option.nonInteractive,
                                defaultArgs = option.defaultArgs,
                                defaultModel = option.defaultModel,
                            )
                        },
                    ),
                )
            }
            get("/providers/litellm/health") {
                val current = ConfigLoader.load()
                val baseUrl = current.settings.litellmBaseUrl.trimEnd('/')
                val apiKey = System.getenv("LITELLM_API_KEY").orEmpty()
                val result = runCatching {
                    runBlocking {
                        val response = client.get("$baseUrl/health/liveliness") {
                            if (apiKey.isNotBlank()) {
                                header(HttpHeaders.Authorization, "Bearer $apiKey")
                            }
                        }
                        LiteLlmHealthResponse(
                            status = if (response.status.isSuccess()) "ok" else "degraded",
                            baseUrl = baseUrl,
                            httpStatus = response.status.value,
                        )
                    }
                }.getOrElse { error ->
                    LiteLlmHealthResponse(
                        status = "unreachable",
                        baseUrl = baseUrl,
                        error = error.message ?: error::class.simpleName.orEmpty(),
                    )
                }
                call.respond(result)
            }
            get("/metrics") {
                call.respondText(prometheus.scrape())
            }
        }
    }.start(wait = true)
}
