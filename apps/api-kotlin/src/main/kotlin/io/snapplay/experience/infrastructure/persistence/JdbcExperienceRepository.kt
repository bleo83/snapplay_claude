package io.snapplay.experience.infrastructure.persistence

import io.snapplay.common.Cursor
import io.snapplay.common.PageResult
import io.snapplay.experience.application.port.output.ContentContextResult
import io.snapplay.experience.application.port.output.CreateExperienceInput
import io.snapplay.experience.application.port.output.ExperienceRepository
import io.snapplay.experience.domain.Experience
import io.snapplay.experience.domain.ExperienceStatus
import io.snapplay.experience.domain.HandoffMode
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcExperienceRepository(
    private val jdbc: JdbcTemplate,
) : ExperienceRepository {
    private data class ExperienceRow(
        val experience: Experience,
        val createdAt: Instant,
    )

    private fun mapRow(rs: ResultSet): ExperienceRow {
        val productIds = (rs.getArray("product_ids")?.array as? kotlin.Array<*>) ?: emptyArray<Any>()
        return ExperienceRow(
            experience =
                Experience(
                    id = UUID.fromString(rs.getString("id")),
                    name = rs.getString("name"),
                    contextTitle = rs.getString("context_title"),
                    channel = rs.getString("channel_display_name"),
                    version = rs.getInt("current_version"),
                    status = ExperienceStatus.valueOf(rs.getString("status")),
                    handoffMode = HandoffMode.valueOf(rs.getString("handoff_mode") ?: "STORE_DEEPLINK"),
                    productCount = productIds.size,
                    startsAt = rs.getTimestamp("effective_from")?.toInstant() ?: Instant.now(),
                    endsAt = rs.getTimestamp("effective_to")?.toInstant(),
                ),
            createdAt = rs.getTimestamp("created_at").toInstant(),
        )
    }

    override fun findAll(
        organizationId: UUID,
        limit: Int,
        cursor: String?,
    ): PageResult<Experience> {
        val decoded = cursor?.let { Cursor.decode(it) }

        val sql =
            buildString {
                append(
                    """
                    SELECT e.id, e.name, e.status, e.current_version, e.created_at,
                           cc.title        AS context_title,
                           ch.display_name AS channel_display_name,
                           ev.handoff_mode, ev.product_ids,
                           ev.effective_from, ev.effective_to
                    FROM experiences e
                    JOIN content_contexts cc ON cc.id = e.content_context_id
                    JOIN channels         ch ON ch.id = cc.channel_id
                    LEFT JOIN experience_versions ev
                           ON ev.experience_id = e.id
                          AND ev.version = e.current_version
                    WHERE e.organization_id = ?
                    """.trimIndent(),
                )
                if (decoded != null) {
                    append("\nAND (e.created_at < ? OR (e.created_at = ? AND e.id < ?::uuid))")
                }
                append("\nORDER BY e.created_at DESC, e.id DESC")
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

        val rows = jdbc.query(sql, { rs, _ -> mapRow(rs) }, *params.toTypedArray())

        val hasMore = rows.size > limit
        val page = if (hasMore) rows.take(limit) else rows
        val nextCursor = if (hasMore) Cursor.encode(page.last().createdAt, page.last().experience.id) else null

        return PageResult(items = page.map { it.experience }, nextCursor = nextCursor)
    }

    override fun findContentContext(
        organizationId: UUID,
        contextTitle: String,
    ): ContentContextResult? =
        jdbc
            .query(
                """
                SELECT cc.id, ch.display_name AS channel_display_name
                FROM content_contexts cc
                JOIN channels ch ON ch.id = cc.channel_id
                WHERE cc.organization_id = ?
                  AND cc.title = ?
                LIMIT 1
                """.trimIndent(),
                { rs, _ -> ContentContextResult(UUID.fromString(rs.getString("id")), rs.getString("channel_display_name")) },
                organizationId,
                contextTitle,
            ).firstOrNull()

    override fun findActiveConnectionId(organizationId: UUID): UUID? =
        jdbc
            .query(
                "SELECT id FROM connections WHERE content_organization_id = ? AND status = 'ACTIVE' LIMIT 1",
                { rs, _ -> UUID.fromString(rs.getString("id")) },
                organizationId,
            ).firstOrNull()

    override fun findActiveContractId(organizationId: UUID): UUID? =
        jdbc
            .query(
                "SELECT id FROM commercial_contracts WHERE publisher_organization_id = ? AND status = 'ACTIVE' LIMIT 1",
                { rs, _ -> UUID.fromString(rs.getString("id")) },
                organizationId,
            ).firstOrNull()

    override fun findActiveProductIds(
        connectionId: UUID,
        limit: Int,
    ): List<UUID> =
        jdbc.query(
            "SELECT id FROM catalog_products WHERE connection_id = ? AND status = 'ACTIVE' LIMIT ?",
            { rs, _ -> UUID.fromString(rs.getString("id")) },
            connectionId,
            limit,
        )

    @Transactional
    override fun create(
        organizationId: UUID,
        actorId: UUID,
        input: CreateExperienceInput,
    ): Experience {
        val experienceId = UUID.randomUUID()

        jdbc.update(
            """
            INSERT INTO experiences
                (id, organization_id, name, content_context_id, connection_id, contract_id, status, current_version)
            VALUES (?, ?, ?, ?, ?, ?, 'DRAFT', 1)
            """.trimIndent(),
            experienceId,
            organizationId,
            input.name,
            input.contextId,
            input.connectionId,
            input.contractId,
        )

        val productIdArray =
            jdbc.dataSource!!.connection.use { conn ->
                conn.createArrayOf("uuid", input.productIds.toTypedArray())
            }
        jdbc.update(
            """
            INSERT INTO experience_versions
                (experience_id, version, status, handoff_mode, product_ids, eligibility, presentation, effective_from, effective_to)
            VALUES (?, 1, 'DRAFT', ?, ?, '{"countries":["AR"]}', '{"locale":"es-AR"}', ?, ?)
            """.trimIndent(),
            experienceId,
            input.handoffMode.name,
            productIdArray,
            Timestamp.from(input.startsAt),
            input.endsAt?.let { Timestamp.from(it) },
        )

        jdbc.update(
            """
            INSERT INTO audit_log
                (organization_id, actor_user_id, action, resource_type, resource_id, request_id, after_redacted)
            VALUES (?, ?, 'experience.created', 'experience', ?, ?, '{"version":1,"status":"DRAFT"}'::jsonb)
            """.trimIndent(),
            organizationId,
            actorId,
            experienceId.toString(),
            UUID.randomUUID().toString(),
        )

        return Experience(
            id = experienceId,
            name = input.name,
            contextTitle = input.contextTitle,
            channel = input.channelDisplayName,
            version = 1,
            status = ExperienceStatus.DRAFT,
            handoffMode = input.handoffMode,
            productCount = input.productIds.size,
            startsAt = input.startsAt,
            endsAt = input.endsAt,
        )
    }
}
