package com.onexeor.gemos.core.memory

import com.onexeor.gemos.core.Settings
import com.onexeor.gemos.core.db.Database

class SlackThreadMemoryRepository(private val settings: Settings) {
    fun migrate() {
        Database.migrate(settings)
    }

    fun storeMessage(
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
                    slack_channel_id, slack_thread_ts, slack_event_ts, slack_user_id,
                    direction, text, brain_run_id
                )
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict (slack_channel_id, slack_event_ts, direction)
                where slack_event_ts is not null
                do nothing
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, channelId)
                stmt.setString(2, threadTs)
                stmt.setString(3, eventTs)
                stmt.setString(4, userId)
                stmt.setString(5, direction.value)
                stmt.setString(6, text)
                stmt.setString(7, brainRunId)
                stmt.executeUpdate()
            }
        }
    }
}

enum class SlackMessageDirection(val value: String) {
    USER("user"),
    GEM("gem"),
}
