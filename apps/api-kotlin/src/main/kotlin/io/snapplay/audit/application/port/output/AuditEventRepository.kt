package io.snapplay.audit.application.port.output

import io.snapplay.audit.domain.AuditAction
import io.snapplay.audit.domain.AuditEvent
import io.snapplay.common.PageResult
import java.util.UUID

data class AuditEventFilters(
    val action: AuditAction? = null,
    val resourceType: String? = null,
    val resourceId: String? = null,
    val actorId: String? = null,
    val organizationId: UUID? = null,
)

interface AuditEventRepository {
    /** Append-only insert. No update or delete methods exist by design. */
    fun save(event: AuditEvent)

    fun search(
        filters: AuditEventFilters,
        limit: Int,
        cursor: String?,
    ): PageResult<AuditEvent>
}
