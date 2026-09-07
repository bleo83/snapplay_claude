package io.snapplay.links.infrastructure.persistence

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.snapplay.links.application.port.output.SmartLinkResolver
import io.snapplay.links.domain.ResolvedSmartLink
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcSmartLinkResolver(
    private val jdbc: JdbcTemplate,
) : SmartLinkResolver {
    private val objectMapper = jacksonObjectMapper()

    override fun resolveByShortCode(shortCode: String): ResolvedSmartLink? =
        jdbc.query(
            """
            SELECT sl.id        AS smart_link_id,
                   sl.short_code,
                   ev.id        AS version_id,
                   e.connection_id,
                   ev.store_selection,
                   ev.eligibility,
                   ev.handoff_mode,
                   dsp.mode     AS data_sharing_mode
            FROM smart_links sl
            JOIN experiences e ON e.id = sl.experience_id
            JOIN experience_versions ev
                ON ev.experience_id = e.id
               AND ev.version = e.current_version
            JOIN connections c ON c.id = e.connection_id
            JOIN data_sharing_policies dsp ON dsp.id = c.data_sharing_policy_id
            WHERE sl.short_code = ?
              AND sl.status = 'ACTIVE'
              AND (sl.expires_at IS NULL OR sl.expires_at > now())
              AND e.status = 'PUBLISHED'
              AND ev.status = 'PUBLISHED'
              AND ev.effective_from <= now()
              AND (ev.effective_to IS NULL OR ev.effective_to > now())
              AND sl.killed_at IS NULL
              AND c.killed_at IS NULL
            LIMIT 1
            """.trimIndent(),
            { rs, _ ->
                val storeSelection: Map<String, String> =
                    objectMapper.readValue(rs.getString("store_selection") ?: "{}")

                @Suppress("UNCHECKED_CAST")
                val eligibility: Map<String, Any> =
                    objectMapper.readValue(rs.getString("eligibility") ?: "{}")
                val territory = (eligibility["countries"] as? List<String>)?.firstOrNull() ?: ""
                ResolvedSmartLink(
                    smartLinkId = UUID.fromString(rs.getString("smart_link_id")),
                    shortCode = rs.getString("short_code"),
                    experienceVersionId = UUID.fromString(rs.getString("version_id")),
                    connectionId = UUID.fromString(rs.getString("connection_id")),
                    providerStoreId = storeSelection["provider_store_id"] ?: "",
                    providerCategoryId = storeSelection["provider_category_id"] ?: "",
                    handoffMode = rs.getString("handoff_mode"),
                    territory = territory,
                    dataSharingMode = rs.getString("data_sharing_mode"),
                )
            },
            shortCode,
        ).firstOrNull()
}
