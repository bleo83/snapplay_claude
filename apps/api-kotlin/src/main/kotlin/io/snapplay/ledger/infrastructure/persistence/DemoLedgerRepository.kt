package io.snapplay.ledger.infrastructure.persistence

import io.snapplay.ledger.application.port.output.LedgerRepository
import io.snapplay.ledger.domain.EntryStatus
import io.snapplay.ledger.domain.LedgerEntry
import io.snapplay.ledger.domain.PartyBalance
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoLedgerRepository : LedgerRepository {
    private val entries = ConcurrentHashMap<UUID, LedgerEntry>()

    fun clear() = entries.clear()

    fun all(): Collection<LedgerEntry> = entries.values

    override fun append(entry: LedgerEntry): Boolean {
        // Check uniqueness by composite key
        val duplicate =
            entries.values.any {
                it.providerOrderId == entry.providerOrderId &&
                    it.ruleKey == entry.ruleKey &&
                    it.causativeEvent == entry.causativeEvent &&
                    it.entryType == entry.entryType &&
                    it.originalEntryId == entry.originalEntryId
            }
        if (duplicate) return false
        entries[entry.id] = entry
        return true
    }

    override fun findById(id: UUID): LedgerEntry? = entries[id]

    override fun findByOrderId(providerOrderId: UUID): List<LedgerEntry> =
        entries.values.filter { it.providerOrderId == providerOrderId }.sortedBy { it.createdAt }

    override fun findByOriginalEntryId(originalEntryId: UUID): List<LedgerEntry> = entries.values.filter { it.originalEntryId == originalEntryId }

    override fun markReversed(id: UUID) {
        entries.computeIfPresent(id) { _, e -> e.copy(status = EntryStatus.REVERSED) }
    }

    override fun markEarned(id: UUID) {
        entries.computeIfPresent(id) { _, e -> e.copy(status = EntryStatus.EARNED) }
    }

    override fun getBalance(
        partyId: UUID,
        currency: String,
        from: Instant,
        to: Instant,
    ): PartyBalance {
        val filtered =
            entries.values.filter {
                it.partyId == partyId && it.currency == currency && !it.createdAt.isBefore(from) && it.createdAt.isBefore(to)
            }
        return PartyBalance(
            partyId = partyId,
            currency = currency,
            totalMinor = filtered.sumOf { it.amountMinor },
            pendingMinor = filtered.filter { it.status == EntryStatus.PENDING }.sumOf { it.amountMinor },
            earnedMinor = filtered.filter { it.status == EntryStatus.EARNED }.sumOf { it.amountMinor },
            settledMinor = filtered.filter { it.status == EntryStatus.SETTLED }.sumOf { it.amountMinor },
        )
    }
}
