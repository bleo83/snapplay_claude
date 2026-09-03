package io.snapplay.partner.application.port.output

import io.snapplay.partner.domain.ReconciliationCheckpoint
import java.time.Instant
import java.util.UUID

interface ReconciliationCheckpointRepository {
    fun findByConnectionId(connectionId: UUID): ReconciliationCheckpoint?

    /** Upserts the checkpoint, advancing only after successful persistence. */
    fun advance(
        connectionId: UUID,
        reconciledAt: Instant,
    )
}
