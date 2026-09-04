package io.snapplay.audit.application.usecase

import io.snapplay.audit.application.port.output.AuditEventFilters
import io.snapplay.audit.application.port.output.AuditEventRepository
import io.snapplay.audit.domain.AuditAction
import io.snapplay.audit.domain.AuditActorType
import io.snapplay.audit.domain.AuditEvent
import io.snapplay.common.PageResult
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

private val REDACT_KEYS = setOf("secret", "password", "token", "key", "credential")

@Service
class AuditService(
    private val repo: AuditEventRepository,
) {
    private val log = LoggerFactory.getLogger(AuditService::class.java)

    fun log(
        actorId: String,
        actorType: AuditActorType,
        organizationId: UUID? = null,
        role: String? = null,
        action: AuditAction,
        resourceType: String,
        resourceId: String,
        beforeState: String? = null,
        afterState: String? = null,
        reason: String? = null,
        origin: String? = null,
    ): AuditEvent {
        val event =
            AuditEvent(
                id = UUID.randomUUID(),
                actorId = actorId,
                actorType = actorType,
                organizationId = organizationId,
                role = role,
                action = action,
                resourceType = resourceType,
                resourceId = resourceId,
                beforeState = beforeState?.let { redact(it) },
                afterState = afterState?.let { redact(it) },
                reason = reason,
                requestId = MDC.get("request_id"),
                origin = origin,
                occurredAt = Instant.now(),
            )
        repo.save(event)
        log.info("Audit: {} {} {}/{} by {}", action, event.id, resourceType, resourceId, actorId)
        return event
    }

    fun search(
        filters: AuditEventFilters,
        limit: Int,
        cursor: String?,
    ): PageResult<AuditEvent> = repo.search(filters, limit, cursor)

    companion object {
        /** Redacts values of JSON keys that look sensitive. Simple regex-based approach. */
        fun redact(json: String): String {
            var result = json
            for (key in REDACT_KEYS) {
                // Matches "key": "value" patterns and replaces value with "[REDACTED]"
                result =
                    result.replace(Regex(""""($key)":\s*"[^"]*"""", RegexOption.IGNORE_CASE)) {
                        """"${it.groupValues[1]}":"[REDACTED]""""
                    }
            }
            return result
        }
    }
}
