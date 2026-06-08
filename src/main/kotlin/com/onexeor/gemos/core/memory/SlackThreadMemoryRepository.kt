package com.onexeor.gemos.core.memory

import com.onexeor.gemos.core.Settings
import com.onexeor.gemos.core.db.Database
import java.sql.ResultSet
import java.util.UUID

class SlackThreadMemoryRepository(private val settings: Settings) {
    fun migrate() {
        Database.migrate(settings)
    }

    fun getOrCreateSession(
        channelId: String,
        threadTs: String,
        ownerUserId: String,
    ): SlackThreadSessionRecord =
        Database.connection(settings).use { conn ->
            conn.prepareStatement(
                """
                insert into slack_thread_sessions (
                    id, slack_channel_id, slack_thread_ts, owner_slack_user_id, status
                )
                values (?, ?, ?, ?, 'active')
                on conflict (slack_channel_id, slack_thread_ts)
                where status = 'active'
                do update set
                    updated_at = now(),
                    last_message_at = now()
                returning *
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, "slack_session_${UUID.randomUUID()}")
                stmt.setString(2, channelId)
                stmt.setString(3, threadTs)
                stmt.setString(4, ownerUserId)
                stmt.executeQuery().use { rs ->
                    check(rs.next()) { "Slack thread session was not returned after upsert." }
                    rs.toSessionRecord()
                }
            }
        }

    fun getActiveSession(channelId: String, threadTs: String): SlackThreadSessionRecord? =
        Database.connection(settings).use { conn ->
            conn.prepareStatement(
                """
                select * from slack_thread_sessions
                where slack_channel_id = ?
                  and slack_thread_ts = ?
                  and status = 'active'
                order by updated_at desc
                limit 1
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, channelId)
                stmt.setString(2, threadTs)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.toSessionRecord() else null
                }
            }
        }

    fun closeActiveSession(channelId: String, threadTs: String): SlackThreadSessionRecord? =
        Database.connection(settings).use { conn ->
            conn.prepareStatement(
                """
                update slack_thread_sessions
                set status = 'closed',
                    updated_at = now()
                where slack_channel_id = ?
                  and slack_thread_ts = ?
                  and status = 'active'
                returning *
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, channelId)
                stmt.setString(2, threadTs)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.toSessionRecord() else null
                }
            }
        }

    fun updateSessionAfterDecision(
        sessionId: String,
        runId: String?,
        decision: String,
        route: String,
        projectId: String?,
    ) {
        Database.connection(settings).use { conn ->
            conn.prepareStatement(
                """
                update slack_thread_sessions
                set current_run_id = ?,
                    last_decision = ?,
                    last_route = ?,
                    last_project_id = ?,
                    updated_at = now()
                where id = ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, runId)
                stmt.setString(2, decision)
                stmt.setString(3, route)
                stmt.setString(4, projectId)
                stmt.setString(5, sessionId)
                stmt.executeUpdate()
            }
        }
    }

    fun storeMessage(
        sessionId: String?,
        channelId: String,
        threadTs: String,
        eventTs: String?,
        userId: String?,
        direction: SlackMessageDirection,
        text: String,
        brainRunId: String? = null,
    ) {
        Database.connection(settings).use { conn ->
            conn.prepareStatement(
                """
                insert into slack_thread_messages (
                    slack_session_id, slack_channel_id, slack_thread_ts, slack_event_ts, slack_user_id,
                    direction, text, brain_run_id
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (slack_channel_id, slack_event_ts, direction)
                where slack_event_ts is not null
                do nothing
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, sessionId)
                stmt.setString(2, channelId)
                stmt.setString(3, threadTs)
                stmt.setString(4, eventTs)
                stmt.setString(5, userId)
                stmt.setString(6, direction.value)
                stmt.setString(7, text)
                stmt.setString(8, brainRunId)
                stmt.executeUpdate()
            }
        }
    }

    fun listRecentMessages(sessionId: String, limit: Int = 12): List<SlackThreadMessageRecord> =
        Database.connection(settings).use { conn ->
            conn.prepareStatement(
                """
                select *
                from (
                    select * from slack_thread_messages
                    where slack_session_id = ?
                    order by created_at desc, id desc
                    limit ?
                ) recent
                order by created_at asc, id asc
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, sessionId)
                stmt.setInt(2, limit.coerceIn(1, 50))
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) add(rs.toMessageRecord())
                    }
                }
            }
        }

    private fun ResultSet.toSessionRecord(): SlackThreadSessionRecord =
        SlackThreadSessionRecord(
            id = getString("id"),
            channelId = getString("slack_channel_id"),
            threadTs = getString("slack_thread_ts"),
            ownerUserId = getString("owner_slack_user_id"),
            status = getString("status"),
            currentRunId = getString("current_run_id"),
            lastDecision = getString("last_decision"),
            lastRoute = getString("last_route"),
            lastProjectId = getString("last_project_id"),
            createdAt = getTimestamp("created_at").toInstant().toString(),
            updatedAt = getTimestamp("updated_at").toInstant().toString(),
            lastMessageAt = getTimestamp("last_message_at").toInstant().toString(),
        )

    private fun ResultSet.toMessageRecord(): SlackThreadMessageRecord =
        SlackThreadMessageRecord(
            id = getLong("id"),
            sessionId = getString("slack_session_id"),
            channelId = getString("slack_channel_id"),
            threadTs = getString("slack_thread_ts"),
            eventTs = getString("slack_event_ts"),
            userId = getString("slack_user_id"),
            direction = getString("direction"),
            text = getString("text"),
            brainRunId = getString("brain_run_id"),
            createdAt = getTimestamp("created_at").toInstant().toString(),
        )
}

data class SlackThreadSessionRecord(
    val id: String,
    val channelId: String,
    val threadTs: String,
    val ownerUserId: String?,
    val status: String,
    val currentRunId: String?,
    val lastDecision: String?,
    val lastRoute: String?,
    val lastProjectId: String?,
    val createdAt: String,
    val updatedAt: String,
    val lastMessageAt: String,
)

data class SlackThreadMessageRecord(
    val id: Long,
    val sessionId: String?,
    val channelId: String,
    val threadTs: String,
    val eventTs: String?,
    val userId: String?,
    val direction: String,
    val text: String,
    val brainRunId: String?,
    val createdAt: String,
)

enum class SlackMessageDirection(val value: String) {
    USER("user"),
    GEM("gem"),
}
