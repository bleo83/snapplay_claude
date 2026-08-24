package io.snapplay.links.infrastructure.persistence

import io.snapplay.config.SnapPlayProperties
import io.snapplay.links.application.port.output.SmartLinkRepository
import io.snapplay.links.domain.SmartLink
import io.snapplay.links.domain.SmartLinkStatus
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcSmartLinkRepository(
    private val jdbc: JdbcTemplate,
    private val props: SnapPlayProperties,
) : SmartLinkRepository {

    private val rowMapper = RowMapper { rs: ResultSet, _ ->
        val shortCode = rs.getString("short_code")
        SmartLink(
            id = UUID.fromString(rs.getString("id")),
            shortCode = shortCode,
            url = "${props.publicBaseUrl}/r/$shortCode",
            experienceName = rs.getString("experience_name"),
            placementKey = rs.getString("placement_key"),
            status = SmartLinkStatus.valueOf(rs.getString("status")),
            scans = rs.getInt("scans"),
            conversions = rs.getInt("conversions"),
        )
    }

    override fun findAll(organizationId: UUID): List<SmartLink> =
        jdbc.query(
            """
            SELECT sl.id, sl.short_code, sl.placement_key, sl.status,
                   e.name AS experience_name,
                   COUNT(hs.id)                                             AS scans,
                   COUNT(hs.id) FILTER (WHERE hs.status = 'CONVERTED')     AS conversions
            FROM smart_links sl
            JOIN experiences e ON e.id = sl.experience_id
            LEFT JOIN handoff_sessions hs ON hs.smart_link_id = sl.id
            WHERE sl.organization_id = ?
            GROUP BY sl.id, sl.short_code, sl.placement_key, sl.status, e.name
            ORDER BY sl.created_at DESC
            """.trimIndent(),
            rowMapper,
            organizationId,
        )
}
