package io.snapplay.links.infrastructure.persistence

import io.snapplay.common.Cursor
import io.snapplay.common.PageResult
import io.snapplay.common.toPageResult
import io.snapplay.config.SnapPlayProperties
import io.snapplay.links.application.port.output.SmartLinkRepository
import io.snapplay.links.domain.SmartLink
import io.snapplay.links.domain.SmartLinkStatus
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcSmartLinkRepository(
    private val jdbc: JdbcTemplate,
    private val props: SnapPlayProperties,
) : SmartLinkRepository {
    private data class SmartLinkRow(val smartLink: SmartLink, val createdAt: Instant)

    private val rowMapper =
        RowMapper { rs: ResultSet, _ ->
            val shortCode = rs.getString("short_code")
            SmartLinkRow(
                smartLink =
                    SmartLink(
                        id = UUID.fromString(rs.getString("id")),
                        shortCode = shortCode,
                        url = "${props.publicBaseUrl}/r/$shortCode",
                        experienceName = rs.getString("experience_name"),
                        placementKey = rs.getString("placement_key"),
                        status = SmartLinkStatus.valueOf(rs.getString("status")),
                        scans = rs.getInt("scans"),
                        conversions = rs.getInt("conversions"),
                    ),
                createdAt = rs.getTimestamp("created_at").toInstant(),
            )
        }

    override fun findAll(
        organizationId: UUID,
        limit: Int,
        cursor: String?,
    ): PageResult<SmartLink> {
        val decoded = cursor?.let { Cursor.decode(it) }

        val sql =
            buildString {
                append(
                    """
                    SELECT sl.id, sl.short_code, sl.placement_key, sl.status, sl.created_at,
                           e.name AS experience_name,
                           COUNT(hs.id)                                             AS scans,
                           COUNT(hs.id) FILTER (WHERE hs.status = 'CONVERTED')     AS conversions
                    FROM smart_links sl
                    JOIN experiences e ON e.id = sl.experience_id
                    LEFT JOIN handoff_sessions hs ON hs.smart_link_id = sl.id
                    WHERE sl.organization_id = ?
                    """.trimIndent(),
                )
                if (decoded != null) {
                    append("\nAND (sl.created_at < ? OR (sl.created_at = ? AND sl.id < ?::uuid))")
                }
                append("\nGROUP BY sl.id, sl.short_code, sl.placement_key, sl.status, sl.created_at, e.name")
                append("\nORDER BY sl.created_at DESC, sl.id DESC")
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

        return jdbc.query(sql, rowMapper, *params.toTypedArray())
            .toPageResult(limit, { it.createdAt }, { it.smartLink.id }, { it.smartLink })
    }
}
