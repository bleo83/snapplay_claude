package io.snapplay.audit.infrastructure.persistence

import io.snapplay.audit.application.port.output.AuditEventFilters
import io.snapplay.audit.application.port.output.AuditEventRepository
import io.snapplay.audit.domain.AuditEvent
import io.snapplay.common.Cursor
import io.snapplay.common.PageResult
import io.snapplay.common.toPageResult
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.concurrent.CopyOnWriteArrayList

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoAuditEventRepository : AuditEventRepository {
    private val events = CopyOnWriteArrayList<AuditEvent>()

    fun clear() = events.clear()

    fun all(): List<AuditEvent> = events.toList()

    override fun save(event: AuditEvent) {
        events.add(event)
    }

    override fun search(
        filters: AuditEventFilters,
        limit: Int,
        cursor: String?,
    ): PageResult<AuditEvent> {
        val cursorPair = cursor?.let { Cursor.decode(it) }
        return events
            .filter { filters.action == null || it.action == filters.action }
            .filter { filters.resourceType == null || it.resourceType == filters.resourceType }
            .filter { filters.resourceId == null || it.resourceId == filters.resourceId }
            .filter { filters.actorId == null || it.actorId == filters.actorId }
            .filter { filters.organizationId == null || it.organizationId == filters.organizationId }
            .sortedWith(compareByDescending<AuditEvent> { it.occurredAt }.thenByDescending { it.id })
            .filter { e ->
                cursorPair?.let { (ts, id) -> e.occurredAt < ts || (e.occurredAt == ts && e.id < id) } ?: true
            }
            .take(limit + 1)
            .toPageResult(limit, { it.occurredAt }, { it.id }, { it })
    }
}
