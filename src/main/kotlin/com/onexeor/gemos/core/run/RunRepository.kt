package com.onexeor.gemos.core.run

import com.onexeor.gemos.core.Settings
import com.onexeor.gemos.core.db.Database
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

class RunRepository(private val settings: Settings) {
    fun migrate() {
        Database.migrate(settings)
    }

    fun createRun(kind: String, userId: String?, inputJson: String): RunRecord =
        withConnection { conn ->
            inTransaction(conn) {
                val id = "run_${UUID.randomUUID()}"
                conn.prepareStatement(
                    """
                    insert into runs (id, kind, status, user_id, input_json)
                    values (?, ?, 'created', ?, cast(? as jsonb))
                    """.trimIndent(),
                ).use { stmt ->
                    stmt.setString(1, id)
                    stmt.setString(2, kind)
                    stmt.setString(3, userId)
                    stmt.setString(4, inputJson)
                    stmt.executeUpdate()
                }
                appendEvent(conn, id, "info", "Run created.", null)
                getRun(conn, id) ?: error("Created run was not readable: $id")
            }
        }

    fun createChildRun(
        parentRunId: String,
        kind: String,
        userId: String?,
        projectId: String?,
        pipelineId: String?,
        provider: String?,
        route: String?,
        inputJson: String,
    ): RunRecord = withConnection { conn ->
        inTransaction(conn) {
            val id = "run_${UUID.randomUUID()}"
            conn.prepareStatement(
                """
                insert into runs (
                    id, parent_run_id, kind, status, user_id, project_id,
                    pipeline_id, provider, route, input_json
                )
                values (?, ?, ?, 'created', ?, ?, ?, ?, ?, cast(? as jsonb))
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, parentRunId)
                stmt.setString(3, kind)
                stmt.setString(4, userId)
                stmt.setString(5, projectId)
                stmt.setString(6, pipelineId)
                stmt.setString(7, provider)
                stmt.setString(8, route)
                stmt.setString(9, inputJson)
                stmt.executeUpdate()
            }
            appendEvent(conn, id, "info", "Child run created.", """{"parentRunId":"$parentRunId"}""")
            appendEvent(conn, parentRunId, "info", "Child run created.", """{"childRunId":"$id","kind":"$kind"}""")
            getRun(conn, id) ?: error("Created child run was not readable: $id")
        }
    }

    fun storeDecision(
        runId: String,
        projectId: String?,
        pipelineId: String?,
        provider: String?,
        route: String?,
        decisionJson: String,
    ): RunRecord = withConnection { conn ->
        inTransaction(conn) {
            conn.prepareStatement(
                """
                update runs
                set project_id = ?,
                    pipeline_id = ?,
                    provider = ?,
                    route = ?,
                    decision_json = cast(? as jsonb)
                where id = ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, projectId)
                stmt.setString(2, pipelineId)
                stmt.setString(3, provider)
                stmt.setString(4, route)
                stmt.setString(5, decisionJson)
                stmt.setString(6, runId)
                stmt.executeUpdate()
            }
            appendEvent(conn, runId, "info", "Brain decision stored.", decisionJson)
            getRun(conn, runId) ?: error("Run not found after decision update: $runId")
        }
    }

    fun markRunning(runId: String): RunRecord = withConnection { conn ->
        inTransaction(conn) {
            conn.prepareStatement(
                """
                update runs
                set status = 'running',
                    started_at = coalesce(started_at, now())
                where id = ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, runId)
                stmt.executeUpdate()
            }
            appendEvent(conn, runId, "info", "Run started.", null)
            getRun(conn, runId) ?: error("Run not found after start: $runId")
        }
    }

    fun completeRun(runId: String, resultJson: String): RunRecord = withConnection { conn ->
        inTransaction(conn) {
            conn.prepareStatement(
                """
                update runs
                set status = 'completed',
                    result_json = cast(? as jsonb),
                    finished_at = now()
                where id = ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, resultJson)
                stmt.setString(2, runId)
                stmt.executeUpdate()
            }
            appendEvent(conn, runId, "info", "Run completed.", resultJson)
            getRun(conn, runId) ?: error("Run not found after completion: $runId")
        }
    }

    fun failRun(runId: String, error: String, payloadJson: String = "{}"): RunRecord = withConnection { conn ->
        inTransaction(conn) {
            conn.prepareStatement(
                """
                update runs
                set status = 'failed',
                    error = ?,
                    finished_at = now()
                where id = ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, error)
                stmt.setString(2, runId)
                stmt.executeUpdate()
            }
            appendEvent(conn, runId, "error", error, payloadJson)
            getRun(conn, runId) ?: error("Run not found after failure: $runId")
        }
    }

    fun appendEvent(runId: String, level: String, message: String, payloadJson: String? = null) {
        withConnection { conn ->
            appendEvent(conn, runId, level, message, payloadJson)
        }
    }

    fun listRuns(limit: Int = 50): List<RunRecord> = withConnection { conn ->
        conn.prepareStatement(
            """
            select * from runs
            order by created_at desc
            limit ?
            """.trimIndent(),
        ).use { stmt ->
            stmt.setInt(1, limit.coerceIn(1, 200))
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(rs.toRunRecord())
                }
            }
        }
    }

    fun getRun(id: String): RunRecord? = withConnection { conn -> getRun(conn, id) }

    fun listChildRuns(parentRunId: String): List<RunRecord> = withConnection { conn ->
        conn.prepareStatement(
            """
            select * from runs
            where parent_run_id = ?
            order by created_at asc
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, parentRunId)
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(rs.toRunRecord())
                }
            }
        }
    }

    fun listEvents(runId: String): List<RunEventRecord> = withConnection { conn ->
        conn.prepareStatement(
            """
            select * from run_events
            where run_id = ?
            order by created_at asc, id asc
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, runId)
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(rs.toRunEventRecord())
                }
            }
        }
    }

    private fun getRun(conn: Connection, id: String): RunRecord? =
        conn.prepareStatement("select * from runs where id = ?").use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.toRunRecord() else null
            }
        }

    private fun appendEvent(conn: Connection, runId: String, level: String, message: String, payloadJson: String?) {
        conn.prepareStatement(
            """
            insert into run_events (run_id, level, message, payload_json)
            values (?, ?, ?, cast(? as jsonb))
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, runId)
            stmt.setString(2, level)
            stmt.setString(3, message)
            stmt.setString(4, payloadJson ?: "{}")
            stmt.executeUpdate()
        }
    }

    private fun <T> withConnection(block: (Connection) -> T): T =
        Database.connection(settings).use(block)

    private fun <T> inTransaction(conn: Connection, block: () -> T): T {
        val originalAutoCommit = conn.autoCommit
        conn.autoCommit = false
        return try {
            val result = block()
            conn.commit()
            result
        } catch (error: Throwable) {
            conn.rollback()
            throw error
        } finally {
            conn.autoCommit = originalAutoCommit
        }
    }

    private fun ResultSet.toRunRecord(): RunRecord =
        RunRecord(
            id = getString("id"),
            parentRunId = getString("parent_run_id"),
            kind = getString("kind"),
            status = getString("status"),
            userId = getString("user_id"),
            projectId = getString("project_id"),
            pipelineId = getString("pipeline_id"),
            provider = getString("provider"),
            route = getString("route"),
            inputJson = getString("input_json"),
            decisionJson = getString("decision_json"),
            resultJson = getString("result_json"),
            error = getString("error"),
            createdAt = getTimestamp("created_at").toInstant().toString(),
            startedAt = getTimestamp("started_at")?.toInstant()?.toString(),
            finishedAt = getTimestamp("finished_at")?.toInstant()?.toString(),
        )

    private fun ResultSet.toRunEventRecord(): RunEventRecord =
        RunEventRecord(
            id = getLong("id"),
            runId = getString("run_id"),
            level = getString("level"),
            message = getString("message"),
            payloadJson = getString("payload_json"),
            createdAt = getTimestamp("created_at").toInstant().toString(),
        )
}
