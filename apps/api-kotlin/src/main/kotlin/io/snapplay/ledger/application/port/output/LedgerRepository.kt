package io.snapplay.ledger.application.port.output

import io.snapplay.ledger.domain.LedgerEntry
import io.snapplay.ledger.domain.PartyBalance
import java.time.Instant
import java.util.UUID

interface LedgerRepository {
    /** Append-only insert. Returns false on duplicate (unique constraint). */
    fun append(entry: LedgerEntry): Boolean

    fun findById(id: UUID): LedgerEntry?

    fun findByOrderId(providerOrderId: UUID): List<LedgerEntry>

    fun findByOriginalEntryId(originalEntryId: UUID): List<LedgerEntry>

    /** Marks an entry as REVERSED. Does NOT change amount — reversal is a separate entry. */
    fun markReversed(id: UUID)

    fun markEarned(id: UUID)

    /** Aggregates balance by party and currency for a period. */
    fun getBalance(
        partyId: UUID,
        currency: String,
        from: Instant,
        to: Instant,
    ): PartyBalance
}
