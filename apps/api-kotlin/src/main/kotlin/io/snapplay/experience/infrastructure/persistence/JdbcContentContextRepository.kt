package io.snapplay.experience.infrastructure.persistence

import io.snapplay.common.Cursor
import io.snapplay.common.PageResult
import io.snapplay.common.toPageResult
import io.snapplay.experience.application.port.output.ContentContextRepository
import io.snapplay.experience.application.port.output.CreateContentContextInput
import io.snapplay.experience.domain.ContentContext
import io.snapplay.experience.domain.ContentContextType
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcContentContextRepository(
    private val jdbc: JdbcTemplate,
) : ContentContextRepository {
    private data class ContextRow(val context: ContentContext, val createdAt: Instant)

    private fun mapRow(rs: ResultSet): ContextRow {
        val context =
            ContentContext(
                id = UUID.fromString(rs.getString("id")),
                channelId = UUID.fromString(rs.getString("channel_id")),
                channelDisplayName = rs.getString("channel_display_name"),
                externalRef = rs.getString("external_ref"),
                contextType = ContentContextType.valueOf(rs.getString("context_type")),
                title = rs.getString("title"),
                createdAt = rs.getTimestamp("created_at").toInstant(),
            )
        return ContextRow(context, context.createdAt)
    }

    override fun findAll(
        organizationId: UUID,
        channelId: UUID?,
        limit: Int,
        cursor: String?,
    ): PageResult<ContentContext> {
        val decoded = cursor?.let { Cursor.decode(it) }

        // sql is built exclusively from static string literals; all dynamic values use ? params — no injection risk
        @Suppress("SqlSourceToSinkFlow")
        val sql =
            buildString {
                append(
                    """
                    SELECT cc.id, cc.channel_id, cc.external_ref, cc.context_type, cc.title, cc.created_at,
                           ch.display_name AS channel_display_name
                    FROM content_contexts cc
                    JOIN channels ch ON ch.id = cc.channel_id
                    WHERE cc.organization_id = ?
                    """.trimIndent(),
                )
                if (channelId != null) append("\nAND cc.channel_id = ?")
                if (decoded != null) append("\nAND (cc.created_at < ? OR (cc.created_at = ? AND cc.id < ?::uuid))")
                append("\nORDER BY cc.created_at DESC, cc.id DESC")
                append("\nLIMIT ?")
            }

        val params =
            buildList {
                add(organizationId)
                if (channelId != null) add(channelId)
                if (decoded != null) {
                    add(Timestamp.from(decoded.first))
                    add(Timestamp.from(decoded.first))
                    add(decoded.second.toString())
                }
                add(limit + 1)
            }

        return jdbc
            .query(sql, { rs, _ -> mapRow(rs) }, *params.toTypedArray())
            .toPageResult(limit, { it.createdAt }, { it.context.id }, { it.context })
    }

    override fun create(input: CreateContentContextInput): ContentContext {
        val id = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO content_contexts (id, organization_id, channel_id, external_ref, context_type, title)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            input.organizationId,
            input.channelId,
            input.externalRef,
            input.contextType.name,
            input.title,
        )
        return jdbc
            .query(
                """
                SELECT cc.id, cc.channel_id, cc.external_ref, cc.context_type, cc.title, cc.created_at,
                       ch.display_name AS channel_display_name
                FROM content_contexts cc
                JOIN channels ch ON ch.id = cc.channel_id
                WHERE cc.id = ?
                """.trimIndent(),
                { rs, _ -> mapRow(rs) },
                id,
            ).first().context
    }
}
