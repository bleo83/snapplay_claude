package io.snapplay.partner.domain

import java.time.Instant
import java.util.UUID

data class ReconciliationCheckpoint(
    val id: UUID,
    val connectionId: UUID,
    val lastReconciledAt: Instant,
)

enum class MismatchType {
    MISSING_IN_SNAPPLAY,
    STATUS_MISMATCH,
    AMOUNT_MISMATCH,
    CURRENCY_MISMATCH,
}

data class ReconciliationMismatch(
    val id: UUID,
    val connectionId: UUID,
    val providerOrderRef: String,
    val mismatchType: MismatchType,
    val snapPlayValue: String?,
    val rappiValue: String?,
    val detectedAt: Instant,
)

/** A single order as seen by Rappi's API. */
data class RappiOrderSnapshot(
    val orderRef: String,
    val status: String,
    val currency: String,
    val totalMinor: Long,
    val placedAt: Instant,
    val deliveredAt: Instant?,
)

data class RappiOrderPage(
    val orders: List<RappiOrderSnapshot>,
    val nextCursor: String?,
)

data class ReconciliationResult(
    val connectionId: UUID,
    val from: Instant,
    val to: Instant,
    val rappiOrdersChecked: Int,
    val missingRecovered: Int,
    val mismatchesDetected: Int,
)
