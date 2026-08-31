package io.snapplay.links.infrastructure.persistence

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.snapplay.links.application.port.output.ScanEventRecorder
import io.snapplay.links.domain.ResolvedSmartLink
import org.postgresql.util.PGobject
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcScanEventRecorder(
    private val jdbc: JdbcTemplate,
) : ScanEventRecorder {
    private val objectMapper = jacksonObjectMapper()

    override fun record(resolved: ResolvedSmartLink) {
        val payload =
            PGobject().apply {
                type = "jsonb"
                value =
                    objectMapper.writeValueAsString(
                        mapOf(
                            "smartLinkId" to resolved.smartLinkId.toString(),
                            "shortCode" to resolved.shortCode,
                            "experienceVersionId" to resolved.experienceVersionId.toString(),
                            "connectionId" to resolved.connectionId.toString(),
                            "providerStoreId" to resolved.providerStoreId,
                            "providerCategoryId" to resolved.providerCategoryId,
                        ),
                    )
            }
        jdbc.update(
            """
            INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, schema_version, payload, occurred_at)
            VALUES ('smart_link', ?, 'scan.created', '1', ?, ?)
            """.trimIndent(),
            resolved.smartLinkId,
            payload,
            java.sql.Timestamp.from(Instant.now()),
        )
    }
}
