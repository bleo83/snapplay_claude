package io.snapplay.audit.infrastructure.persistence

import io.snapplay.audit.application.port.output.AuditEventFilters
import io.snapplay.audit.application.port.output.AuditEventRepository
import io.snapplay.audit.domain.AuditAction
import io.snapplay.audit.domain.AuditActorType
import io.snapplay.audit.domain.AuditEvent
import io.snapplay.common.Cursor
import io.snapplay.common.PageResult
import io.snapplay.common.toPageResult
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcAuditEventRepository(
    private val jdbc: JdbcTemplate,
) : AuditEventRepository {
    override fun save(event: AuditEvent) {
        jdbc.update(
            """
            INSERT INTO audit_events
                (id, actor_id, actor_type, organization_id, role, action, resource_type,
                 resource_id, before_state, after_state, reason, request_id, origin, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?)
            """.trimIndent(),
            event.id,
            event.actorId,
            event.actorType.name,
            event.organizationId,
            event.role,
            event.action.name,
            event.resourceType,
            event.resourceId,
            event.beforeState,
            event.afterState,
            event.reason,
            event.requestId,
            event.origin,
            Timestamp.from(event.occurredAt),
        )
    }

    @Suppress("SqlSourceToSinkFlow") // sql built from static literals; all dynamic values use ? params
    override fun search(
        filters: AuditEventFilters,
        limit: Int,
        cursor: String?,
    ): PageResult<AuditEvent> {
        val params = mutableListOf<Any>()
        val sql =
            buildString {
                append("SELECT * FROM audit_events WHERE 1=1")
                filters.action?.let {
                    append(" AND action = ?")
                    params.add(it.name)
                }
                filters.resourceType?.let {
                    append(" AND resource_type = ?")
                    params.add(it)
                }
                filters.resourceId?.let {
                    append(" AND resource_id = ?")
                    params.add(it)
                }
                filters.actorId?.let {
                    append(" AND actor_id = ?")
                    params.add(it)
                }
                filters.organizationId?.let {
                    append(" AND organization_id = ?")
                    params.add(it)
                }
                cursor?.let { Cursor.decode(it) }?.let { (ts, id) ->
                    append(" AND (occurred_at, id) < (?, ?)")
                    params.add(Timestamp.from(ts))
                    params.add(id)
                }
                append(" ORDER BY occurred_at DESC, id DESC LIMIT ?")
                params.add(limit + 1)
            }

        return jdbc.query(sql, { rs, _ -> rs.toEvent() }, *params.toTypedArray())
            .toPageResult(limit, { it.occurredAt }, { it.id }, { it })
    }

    private fun ResultSet.toEvent() =
        AuditEvent(
            id = UUID.fromString(getString("id")),
            actorId = getString("actor_id"),
            actorType = AuditActorType.valueOf(getString("actor_type")),
            organizationId = getString("organization_id")?.let { UUID.fromString(it) },
            role = getString("role"),
            action = AuditAction.valueOf(getString("action")),
            resourceType = getString("resource_type"),
            resourceId = getString("resource_id"),
            beforeState = getString("before_state"),
            afterState = getString("after_state"),
            reason = getString("reason"),
            requestId = getString("request_id"),
            origin = getString("origin"),
            occurredAt = getTimestamp("occurred_at").toInstant(),
        )
}
