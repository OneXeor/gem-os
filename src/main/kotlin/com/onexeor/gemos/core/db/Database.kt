package com.onexeor.gemos.core.db

import com.onexeor.gemos.core.Settings
import org.flywaydb.core.Flyway
import java.net.URI
import java.sql.Connection
import java.sql.DriverManager

object Database {
    fun migrate(settings: Settings) {
        val target = target(settings.databaseUrl)
        Flyway.configure()
            .dataSource(target.jdbcUrl, target.user, target.password)
            .baselineOnMigrate(true)
            .load()
            .migrate()
    }

    fun connection(settings: Settings): Connection {
        val target = target(settings.databaseUrl)
        return DriverManager.getConnection(target.jdbcUrl, target.user, target.password)
    }

    fun jdbcUrl(url: String): String = target(url).jdbcUrl

    private fun target(url: String): DatabaseTarget = when {
        url.startsWith("jdbc:") -> DatabaseTarget(jdbcUrl = url)
        url.startsWith("postgresql+psycopg://") -> parsePostgresUri(url.replaceFirst("postgresql+psycopg://", "postgresql://"))
        url.startsWith("postgresql://") -> parsePostgresUri(url)
        else -> DatabaseTarget(jdbcUrl = url)
    }

    private fun parsePostgresUri(url: String): DatabaseTarget {
        val uri = URI(url)
        val userInfo = uri.userInfo?.split(":", limit = 2).orEmpty()
        val user = userInfo.getOrNull(0)
        val password = userInfo.getOrNull(1)
        val host = uri.host ?: error("Database URL is missing host: $url")
        val port = if (uri.port == -1) "" else ":${uri.port}"
        val query = uri.query?.let { "?$it" }.orEmpty()
        return DatabaseTarget(
            jdbcUrl = "jdbc:postgresql://$host$port${uri.path}$query",
            user = user,
            password = password,
        )
    }
}

private data class DatabaseTarget(
    val jdbcUrl: String,
    val user: String? = null,
    val password: String? = null,
)
