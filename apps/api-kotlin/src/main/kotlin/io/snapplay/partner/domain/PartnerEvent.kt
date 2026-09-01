package io.snapplay.partner.domain

import java.time.Instant
import java.util.UUID

enum class PartnerEventStatus { RECEIVED, PROCESSED, REJECTED }

data class PartnerEvent(
    val id: UUID,
    val eventId: String,
    val eventType: String,
    val connectionId: UUID,
    val providerOrderRef: String?,
    val payload: String,
    val signatureTimestamp: Instant,
    val status: PartnerEventStatus,
    val errorDetail: String? = null,
)

data class OutboxEvent(
    val id: UUID,
    val aggregateType: String,
    val aggregateId: UUID,
    val eventType: String,
    val schemaVersion: String,
    val payload: String,
    val occurredAt: Instant,
)

/** Slim projection used by the partner bounded context to look up an active handoff session. */
data class HandoffSessionRef(
    val id: UUID,
    val connectionId: UUID,
)
