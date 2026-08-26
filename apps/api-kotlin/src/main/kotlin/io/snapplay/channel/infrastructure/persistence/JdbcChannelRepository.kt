package io.snapplay.channel.infrastructure.persistence

import io.snapplay.channel.application.port.output.ChannelRepository
import io.snapplay.channel.application.port.output.CreateChannelInput
import io.snapplay.channel.domain.Channel
import io.snapplay.channel.domain.ChannelStatus
import io.snapplay.common.Cursor
import io.snapplay.common.PageResult
import io.snapplay.common.toPageResult
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcChannelRepository(
    private val jdbc: JdbcTemplate,
) : ChannelRepository {
    private data class ChannelRow(val channel: Channel, val createdAt: Instant)

    private fun mapRow(rs: ResultSet): ChannelRow {
        val channel =
            Channel(
                id = UUID.fromString(rs.getString("id")),
                channelKey = rs.getString("channel_key"),
                displayName = rs.getString("display_name"),
                status = ChannelStatus.valueOf(rs.getString("status")),
                createdAt = rs.getTimestamp("created_at").toInstant(),
            )
        return ChannelRow(channel, channel.createdAt)
    }

    override fun findAll(
        organizationId: UUID,
        limit: Int,
        cursor: String?,
    ): PageResult<Channel> {
        val decoded = cursor?.let { Cursor.decode(it) }

        // sql is built exclusively from static string literals; all dynamic values use ? params — no injection risk
        @Suppress("SqlSourceToSinkFlow")
        val sql =
            buildString {
                append(
                    """
                    SELECT id, channel_key, display_name, status, created_at
                    FROM channels
                    WHERE organization_id = ?
                    """.trimIndent(),
                )
                if (decoded != null) {
                    append("\nAND (created_at < ? OR (created_at = ? AND id < ?::uuid))")
                }
                append("\nORDER BY created_at DESC, id DESC")
                append("\nLIMIT ?")
            }

        val params =
            buildList {
                add(organizationId)
                if (decoded != null) {
                    add(Timestamp.from(decoded.first))
                    add(Timestamp.from(decoded.first))
                    add(decoded.second.toString())
                }
                add(limit + 1)
            }

        return jdbc
            .query(sql, { rs, _ -> mapRow(rs) }, *params.toTypedArray())
            .toPageResult(limit, { it.createdAt }, { it.channel.id }, { it.channel })
    }

    override fun create(input: CreateChannelInput): Channel {
        val id = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO channels (id, organization_id, channel_key, display_name, status)
            VALUES (?, ?, ?, ?, 'ACTIVE')
            """.trimIndent(),
            id,
            input.organizationId,
            input.channelKey,
            input.displayName,
        )
        return jdbc
            .query(
                "SELECT id, channel_key, display_name, status, created_at FROM channels WHERE id = ?",
                { rs, _ -> mapRow(rs) },
                id,
            ).first().channel
    }
}
