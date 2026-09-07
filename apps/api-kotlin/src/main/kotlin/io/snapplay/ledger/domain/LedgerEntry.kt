package io.snapplay.ledger.domain

import java.time.Instant
import java.util.UUID

enum class EntryType { EARN, REVERSAL, ADJUSTMENT }

enum class EntryStatus { PENDING, EARNED, REVERSED, SETTLED }

data class LedgerEntry(
    val id: UUID,
    val entryType: EntryType,
    val status: EntryStatus,
    val providerOrderId: UUID,
    val handoffSessionId: UUID?,
    val contractVersionId: UUID,
    val ruleKey: String,
    val causativeEvent: String,
    val partyId: UUID,
    val counterpartyId: UUID,
    val baseAmountMinor: Long,
    /** Signed: positive = credit to party, negative = debit. */
    val amountMinor: Long,
    val currency: String,
    /** For REVERSAL/ADJUSTMENT: the original entry being reversed. */
    val originalEntryId: UUID?,
    val reason: String?,
    val actorId: UUID?,
    val createdAt: Instant,
)

data class PartyBalance(
    val partyId: UUID,
    val currency: String,
    val totalMinor: Long,
    val pendingMinor: Long,
    val earnedMinor: Long,
    val settledMinor: Long,
)
