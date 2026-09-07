package io.snapplay.ledger.application.usecase

import io.snapplay.common.NotFoundException
import io.snapplay.common.ValidationException
import io.snapplay.ledger.application.port.input.GetBalanceUseCase
import io.snapplay.ledger.application.port.input.ListOrderEntriesUseCase
import io.snapplay.ledger.application.port.input.RecordAdjustmentUseCase
import io.snapplay.ledger.application.port.input.RecordEarnUseCase
import io.snapplay.ledger.application.port.input.RecordReversalUseCase
import io.snapplay.ledger.application.port.output.LedgerRepository
import io.snapplay.ledger.domain.EntryStatus
import io.snapplay.ledger.domain.EntryType
import io.snapplay.ledger.domain.LedgerEntry
import io.snapplay.ledger.domain.PartyBalance
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID
import kotlin.math.abs

@Service
class RecordEarnUseCaseImpl(
    private val repo: LedgerRepository,
) : RecordEarnUseCase {
    override fun recordEarn(
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
    ): LedgerEntry {
        val entry =
            LedgerEntry(
                id = UUID.randomUUID(),
                entryType = EntryType.EARN,
                status = EntryStatus.PENDING,
                providerOrderId = providerOrderId,
                handoffSessionId = handoffSessionId,
                contractVersionId = contractVersionId,
                ruleKey = ruleKey,
                causativeEvent = causativeEvent,
                partyId = partyId,
                counterpartyId = counterpartyId,
                baseAmountMinor = baseAmountMinor,
                amountMinor = amountMinor,
                currency = currency,
                originalEntryId = null,
                reason = null,
                actorId = null,
                createdAt = Instant.now(),
            )
        repo.append(entry)
        return entry
    }
}

@Service
class RecordReversalUseCaseImpl(
    private val repo: LedgerRepository,
) : RecordReversalUseCase {
    override fun recordReversal(
        originalEntryId: UUID,
        amountMinor: Long,
        causativeEvent: String,
        reason: String,
    ): LedgerEntry {
        val original =
            repo.findById(originalEntryId)
                ?: throw NotFoundException("Ledger entry $originalEntryId not found")

        if (original.entryType != EntryType.EARN) {
            throw ValidationException("Can only reverse EARN entries, got ${original.entryType}")
        }

        // Prevent reversing more than the original amount
        val existingReversals = repo.findByOriginalEntryId(originalEntryId)
        val totalReversed = existingReversals.sumOf { abs(it.amountMinor) }
        if (totalReversed + amountMinor > abs(original.amountMinor)) {
            throw ValidationException(
                "Reversal amount $amountMinor would exceed original amount ${abs(original.amountMinor)} (already reversed: $totalReversed)",
            )
        }

        val reversal =
            LedgerEntry(
                id = UUID.randomUUID(),
                entryType = EntryType.REVERSAL,
                status = EntryStatus.PENDING,
                providerOrderId = original.providerOrderId,
                handoffSessionId = original.handoffSessionId,
                contractVersionId = original.contractVersionId,
                ruleKey = original.ruleKey,
                causativeEvent = causativeEvent,
                partyId = original.partyId,
                counterpartyId = original.counterpartyId,
                baseAmountMinor = original.baseAmountMinor,
                amountMinor = -amountMinor,
                currency = original.currency,
                originalEntryId = originalEntryId,
                reason = reason,
                actorId = null,
                createdAt = Instant.now(),
            )
        repo.append(reversal)

        // Mark original as reversed if fully reversed
        if (totalReversed + amountMinor >= abs(original.amountMinor)) {
            repo.markReversed(originalEntryId)
        }

        return reversal
    }
}

@Service
class RecordAdjustmentUseCaseImpl(
    private val repo: LedgerRepository,
) : RecordAdjustmentUseCase {
    override fun recordAdjustment(
        originalEntryId: UUID,
        amountMinor: Long,
        reason: String,
        actorId: UUID,
    ): LedgerEntry {
        require(reason.isNotBlank()) { "Adjustments require a reason" }

        val original =
            repo.findById(originalEntryId)
                ?: throw NotFoundException("Ledger entry $originalEntryId not found")

        val adjustment =
            LedgerEntry(
                id = UUID.randomUUID(),
                entryType = EntryType.ADJUSTMENT,
                status = EntryStatus.PENDING,
                providerOrderId = original.providerOrderId,
                handoffSessionId = original.handoffSessionId,
                contractVersionId = original.contractVersionId,
                ruleKey = original.ruleKey,
                causativeEvent = "MANUAL_ADJUSTMENT",
                partyId = original.partyId,
                counterpartyId = original.counterpartyId,
                baseAmountMinor = original.baseAmountMinor,
                amountMinor = amountMinor,
                currency = original.currency,
                originalEntryId = originalEntryId,
                reason = reason,
                actorId = actorId,
                createdAt = Instant.now(),
            )
        repo.append(adjustment)
        return adjustment
    }
}

@Service
class GetBalanceUseCaseImpl(
    private val repo: LedgerRepository,
) : GetBalanceUseCase {
    override fun getBalance(
        partyId: UUID,
        currency: String,
        from: Instant,
        to: Instant,
    ): PartyBalance = repo.getBalance(partyId, currency, from, to)
}

@Service
class ListOrderEntriesUseCaseImpl(
    private val repo: LedgerRepository,
) : ListOrderEntriesUseCase {
    override fun list(providerOrderId: UUID): List<LedgerEntry> = repo.findByOrderId(providerOrderId)
}
