package com.onexeor.gemos.core

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addFileSource
import com.sksamuel.hoplite.yaml.YamlParser
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists

@Serializable
data class Settings(
    val gemHome: String,
    val gemEnv: String,
    val adminPort: Int,
    val brainPort: Int,
    val providerRouterPort: Int,
    val databaseUrl: String,
    val redisUrl: String,
    val qdrantUrl: String,
    val embeddingsBaseUrl: String,
    val embeddingsModel: String,
    val embeddingsVectorSize: Int,
    val litellmBaseUrl: String,
)

@Serializable
data class GemIdentity(
    val name: String,
    val role: String,
    val principles: List<String> = emptyList(),
)

@Serializable
data class UserProfile(
    val displayName: String,
    val slackUserIds: List<String> = emptyList(),
    val timezone: String = "Europe/Warsaw",
    val preferences: Map<String, String> = emptyMap(),
)

@Serializable
data class IdentityConfig(
    val gem: GemIdentity,
    val users: Map<String, UserProfile> = emptyMap(),
)

@Serializable
data class ProviderPolicy(
    val defaultChatProvider: String = "litellm",
    val defaultCodeProvider: String = "codex",
)

@Serializable
data class OrchestrationPolicy(
    val mode: String = "capability-router",
    val plannerDefault: String = "codex",
    val plannerFallback: String? = null,
    val defaultChatRoute: String = "litellm",
    val defaultCodeRoute: String = "codex",
    val defaultPipelineRoute: String = "scheduler",
)

@Serializable
data class ProjectConfig(
    val id: String,
    val name: String,
    val owner: String,
    val type: String,
    val status: String = "planned",
    val aliases: List<String> = emptyList(),
    val repos: List<String> = emptyList(),
    val pipelines: List<String> = emptyList(),
    val providerPolicy: ProviderPolicy = ProviderPolicy(),
)

@Serializable
data class ProjectsConfig(
    val projects: List<ProjectConfig> = emptyList(),
)

@Serializable
data class ChatProviderOption(
    val baseUrlEnv: String? = null,
    val apiKeyEnv: String? = null,
    val defaultModel: String = "default",
)

@Serializable
data class CodeProviderOption(
    val mode: String,
    val command: String? = null,
    val auth: String = "host",
    val enabled: Boolean = true,
    val costProfile: String = "default",
    val notes: String? = null,
    val nonInteractive: Boolean = true,
    val defaultArgs: List<String> = emptyList(),
    val defaultModel: String = "latest",
)

@Serializable
data class ChatProviders(
    val default: String = "litellm",
    val options: Map<String, ChatProviderOption> = emptyMap(),
)

@Serializable
data class CodeAgentProviders(
    val default: String = "codex",
    val options: Map<String, CodeProviderOption> = emptyMap(),
)

@Serializable
data class ProvidersRoot(
    val orchestration: OrchestrationPolicy = OrchestrationPolicy(),
    val chat: ChatProviders = ChatProviders(),
    val codeAgent: CodeAgentProviders = CodeAgentProviders(),
)

@Serializable
data class ProvidersConfig(
    val providers: ProvidersRoot = ProvidersRoot(),
)

@Serializable
data class PipelineSchedule(
    val cron: String,
    val timezone: String = "Europe/Warsaw",
)

@Serializable
data class PipelineProvider(
    val chat: String? = null,
    val codeAgent: String? = null,
)

@Serializable
data class PipelineExecution(
    val type: String = "internal",
    val repo: String? = null,
    val workingDirectory: String? = null,
    val command: List<String> = emptyList(),
)

@Serializable
data class PipelineOutputs(
    val slack: Boolean = true,
    val admin: Boolean = true,
)

@Serializable
data class PipelineSafety(
    val requireBranch: Boolean = false,
    val requireTests: Boolean = false,
    val requireHumanApprovalForPr: Boolean = true,
)

@Serializable
data class PipelineConfig(
    val id: String,
    val name: String,
    val stage: String,
    val enabled: Boolean = false,
    val projectIds: List<String> = emptyList(),
    val schedule: PipelineSchedule? = null,
    val mode: String = "manual-only",
    val provider: PipelineProvider = PipelineProvider(),
    val execution: PipelineExecution = PipelineExecution(),
    val outputs: PipelineOutputs = PipelineOutputs(),
    val safety: PipelineSafety = PipelineSafety(),
)

@Serializable
data class PipelinesConfig(
    val pipelines: List<PipelineConfig> = emptyList(),
)

@Serializable
data class GemConfig(
    val settings: Settings,
    val identity: IdentityConfig,
    val projects: ProjectsConfig,
    val providers: ProvidersConfig,
    val pipelines: PipelinesConfig,
)

object ConfigLoader {
    fun load(gemHomeOverride: String? = null): GemConfig {
        val gemHome = gemHomeOverride
            ?: System.getenv("GEM_HOME")
            ?: File(".").absolutePath
        val root = Path(gemHome).absolute()

        return GemConfig(
            settings = Settings(
                gemHome = root.toString(),
                gemEnv = System.getenv("GEM_ENV") ?: "local",
                adminPort = (System.getenv("ADMIN_PORT") ?: "8000").toInt(),
                brainPort = (System.getenv("BRAIN_PORT") ?: "8020").toInt(),
                providerRouterPort = (System.getenv("PROVIDER_ROUTER_PORT") ?: "8010").toInt(),
                databaseUrl = System.getenv("DATABASE_URL")
                    ?: "postgresql://gem:gem@postgres:5432/gem",
                redisUrl = System.getenv("REDIS_URL") ?: "redis://redis:6379/0",
                qdrantUrl = System.getenv("QDRANT_URL") ?: "http://qdrant:6333",
                embeddingsBaseUrl = System.getenv("EMBEDDINGS_BASE_URL") ?: "http://bge-m3:7997",
                embeddingsModel = System.getenv("EMBEDDINGS_MODEL") ?: "BAAI/bge-m3",
                embeddingsVectorSize = (System.getenv("EMBEDDINGS_VECTOR_SIZE") ?: "1024").toInt(),
                litellmBaseUrl = System.getenv("LITELLM_BASE_URL") ?: "http://litellm:4000",
            ),
            identity = readYaml(root.toString(), "identity"),
            projects = readYaml(root.toString(), "projects"),
            providers = readYaml(root.toString(), "providers"),
            pipelines = readYaml(root.toString(), "pipelines"),
        )
    }

    @OptIn(ExperimentalHoplite::class)
    private inline fun <reified T : Any> readYaml(root: String, name: String): T {
        val real = Path(root, "config", "$name.yaml")
        val example = Path(root, "config", "$name.example.yaml")
        val source = when {
            real.exists() -> real.toString()
            example.exists() -> example.toString()
            else -> error("Missing config file: config/$name.yaml or config/$name.example.yaml")
        }

        return ConfigLoaderBuilder.default()
            .withExplicitSealedTypes()
            .addParser("yaml", YamlParser())
            .addFileSource(source)
            .build()
            .loadConfigOrThrow()
    }
}
