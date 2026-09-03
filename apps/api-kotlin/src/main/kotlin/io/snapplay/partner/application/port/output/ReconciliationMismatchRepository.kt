package io.snapplay.partner.application.port.output

import io.snapplay.partner.domain.ReconciliationMismatch

interface ReconciliationMismatchRepository {
    fun save(mismatch: ReconciliationMismatch)
}
