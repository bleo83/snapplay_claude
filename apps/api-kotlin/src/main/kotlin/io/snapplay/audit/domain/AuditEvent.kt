package io.snapplay.audit.domain

import java.time.Instant
import java.util.UUID

enum class AuditActorType { USER, SERVICE_ACCOUNT, SYSTEM }

enum class AuditAction {
    EXPERIENCE_CREATED,
    EXPERIENCE_PUBLISHED,
    EXPERIENCE_PAUSED,
    EXPERIENCE_RETIRED,
    EXPERIENCE_CLONED,
    CONNECTION_CREATED,
    CONNECTION_UPDATED,
    DESTINATION_CHANGED,
    CATALOG_SYNCED,
    WEBHOOK_SECRET_ROTATED,
    ORDER_ADJUSTED,
    SETTLEMENT_APPROVED,
}

data class AuditEvent(
    val id: UUID,
    val actorId: String,
    val actorType: AuditActorType,
    val organizationId: UUID?,
    val role: String?,
    val action: AuditAction,
    val resourceType: String,
    val resourceId: String,
    val beforeState: String?,
    val afterState: String?,
    val reason: String?,
    val requestId: String?,
    val origin: String?,
    val occurredAt: Instant,
)
