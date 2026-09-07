package io.snapplay.ledger.application.port.input

import io.snapplay.ledger.domain.LedgerEntry
import io.snapplay.ledger.domain.PartyBalance
import java.time.Instant
import java.util.UUID

interface RecordEarnUseCase {
    /** Records an EARN entry for a delivered order. Idempotent by unique key. */
    fun recordEarn(
        providerOrderId: UUID,
        handoffSessionId: UUID?,
        contractVersionId: UUID,
        ruleKey: String,
        causativeEvent: String,
        partyId: UUID,
        counterpartyId: UUID,
        baseAmountMinor: Long,
        amountMinor: Long,
        currency: String,
    ): LedgerEntry
}

interface RecordReversalUseCase {
    /**
     * Creates a signed-opposite REVERSAL entry linked to [originalEntryId].
     * For partial refunds, [amountMinor] can be less than the original.
     * Prevents double-reversal of the same entry.
     */
    fun recordReversal(
        originalEntryId: UUID,
        amountMinor: Long,
        causativeEvent: String,
        reason: String,
    ): LedgerEntry
}

interface RecordAdjustmentUseCase {
    /** Manual correction with required reason and actor. */
    fun recordAdjustment(
        originalEntryId: UUID,
        amountMinor: Long,
        reason: String,
        actorId: UUID,
    ): LedgerEntry
}

interface GetBalanceUseCase {
    fun getBalance(
        partyId: UUID,
        currency: String,
        from: Instant,
        to: Instant,
    ): PartyBalance
}

interface ListOrderEntriesUseCase {
    fun list(providerOrderId: UUID): List<LedgerEntry>
}
