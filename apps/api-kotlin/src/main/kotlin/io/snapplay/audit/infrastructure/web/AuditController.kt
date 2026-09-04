package io.snapplay.audit.infrastructure.web

import io.snapplay.audit.application.port.output.AuditEventFilters
import io.snapplay.audit.application.usecase.AuditService
import io.snapplay.audit.domain.AuditAction
import io.snapplay.audit.domain.AuditActorType
import io.snapplay.audit.domain.AuditEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/v1/audit")
class AuditController(
    private val auditService: AuditService,
) {
    /** Read-only search endpoint for Auditors. No mutation endpoints exist by design. */
    @GetMapping("/events")
    fun search(
        @RequestParam(required = false) action: AuditAction?,
        @RequestParam(required = false) resourceType: String?,
        @RequestParam(required = false) resourceId: String?,
        @RequestParam(required = false) actorId: String?,
        @RequestParam(required = false) organizationId: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) cursor: String?,
    ): AuditEventListResponse {
        val result =
            auditService.search(
                AuditEventFilters(action, resourceType, resourceId, actorId, organizationId),
                limit.coerceIn(1, 200),
                cursor,
            )
        return AuditEventListResponse(
            items = result.items.map { it.toDto() },
            nextCursor = result.nextCursor,
        )
    }

    private fun AuditEvent.toDto() =
        AuditEventDto(
            id = id,
            actorId = actorId,
            actorType = actorType,
            organizationId = organizationId,
            role = role,
            action = action,
            resourceType = resourceType,
            resourceId = resourceId,
            reason = reason,
            requestId = requestId,
            origin = origin,
            occurredAt = occurredAt,
        )
}

data class AuditEventListResponse(
    val items: List<AuditEventDto>,
    val nextCursor: String?,
)

data class AuditEventDto(
    val id: UUID,
    val actorId: String,
    val actorType: AuditActorType,
    val organizationId: UUID?,
    val role: String?,
    val action: AuditAction,
    val resourceType: String,
    val resourceId: String,
    val reason: String?,
    val requestId: String?,
    val origin: String?,
    val occurredAt: Instant,
)
