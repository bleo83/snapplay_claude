package io.snapplay.links.infrastructure.persistence

import io.snapplay.common.Cursor
import io.snapplay.common.PageResult
import io.snapplay.common.toPageResult
import io.snapplay.config.SnapPlayProperties
import io.snapplay.links.application.port.output.CreateSmartLinkInput
import io.snapplay.links.application.port.output.SmartLinkRepository
import io.snapplay.links.domain.SmartLink
import io.snapplay.links.domain.SmartLinkStatus
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
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
    private data class SmartLinkRow(
        val smartLink: SmartLink,
        val createdAt: Instant,
    )

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

    private val selectSql =
        """
        SELECT sl.id, sl.short_code, sl.placement_key, sl.status, sl.created_at,
               e.name AS experience_name,
               COUNT(hs.id)                                             AS scans,
               COUNT(hs.id) FILTER (WHERE hs.status = 'CONVERTED')     AS conversions
        FROM smart_links sl
        JOIN experiences e ON e.id = sl.experience_id
        LEFT JOIN handoff_sessions hs ON hs.smart_link_id = sl.id
        """.trimIndent()

    override fun findAll(
        organizationId: UUID,
        limit: Int,
        cursor: String?,
    ): PageResult<SmartLink> {
        val decoded = cursor?.let { Cursor.decode(it) }

        // sql is built exclusively from static string literals; all dynamic values use ? params — no injection risk
        @Suppress("SqlSourceToSinkFlow")
        val sql =
            buildString {
                append(selectSql)
                append("\nWHERE sl.organization_id = ?")
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

        return jdbc
            .query(sql, rowMapper, *params.toTypedArray())
            .toPageResult(limit, { it.createdAt }, { it.smartLink.id }, { it.smartLink })
    }

    override fun findByShortCode(
        organizationId: UUID,
        shortCode: String,
    ): SmartLink? =
        jdbc
            .query(
                """
                $selectSql
                WHERE sl.organization_id = ? AND sl.short_code = ?
                GROUP BY sl.id, sl.short_code, sl.placement_key, sl.status, sl.created_at, e.name
                LIMIT 1
                """.trimIndent(),
                rowMapper,
                organizationId,
                shortCode,
            ).firstOrNull()?.smartLink

    override fun shortCodeExists(shortCode: String): Boolean =
        (jdbc.queryForObject("SELECT COUNT(*) FROM smart_links WHERE short_code = ?", Int::class.java, shortCode) ?: 0) > 0

    @Transactional
    override fun create(
        organizationId: UUID,
        actorId: UUID,
        input: CreateSmartLinkInput,
    ): SmartLink {
        val id = UUID.randomUUID()

        jdbc.update(
            """
            INSERT INTO smart_links (id, organization_id, experience_id, short_code, placement_key, status)
            VALUES (?, ?, ?, ?, ?, 'ACTIVE')
            """.trimIndent(),
            id,
            organizationId,
            input.experienceId,
            input.shortCode,
            input.placementKey,
        )

        jdbc.update(
            """
            INSERT INTO audit_log
                (organization_id, actor_user_id, action, resource_type, resource_id, request_id, after_redacted)
            VALUES (?, ?, 'smart_link.created', 'smart_link', ?, ?, '{"status":"ACTIVE"}'::jsonb)
            """.trimIndent(),
            organizationId,
            actorId,
            id.toString(),
            UUID.randomUUID().toString(),
        )

        return SmartLink(
            id = id,
            shortCode = input.shortCode,
            url = "${props.publicBaseUrl}/r/${input.shortCode}",
            experienceName = input.experienceName,
            placementKey = input.placementKey,
            status = SmartLinkStatus.ACTIVE,
            scans = 0,
            conversions = 0,
        )
    }
}
