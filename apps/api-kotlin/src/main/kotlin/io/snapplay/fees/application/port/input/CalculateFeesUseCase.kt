package io.snapplay.fees.application.port.input

import io.snapplay.fees.domain.FeeInput
import io.snapplay.fees.domain.FeeResult

interface CalculateFeesUseCase {
    /** Calculates fees without persisting. Useful for preview/dry-run. */
    fun calculate(input: FeeInput): FeeResult

    /** Calculates fees AND records each as a ledger EARN entry. Idempotent by unique key. */
    fun calculateAndRecord(input: FeeInput): FeeResult
}
