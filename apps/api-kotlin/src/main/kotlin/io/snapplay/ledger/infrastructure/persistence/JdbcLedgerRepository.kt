package io.snapplay.ledger.infrastructure.persistence

import io.snapplay.ledger.application.port.output.LedgerRepository
import io.snapplay.ledger.domain.EntryStatus
import io.snapplay.ledger.domain.EntryType
import io.snapplay.ledger.domain.LedgerEntry
import io.snapplay.ledger.domain.PartyBalance
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcLedgerRepository(
    private val jdbc: JdbcTemplate,
) : LedgerRepository {
    override fun append(entry: LedgerEntry): Boolean =
        try {
            jdbc.update(
                """
                INSERT INTO ledger_entries
                    (id, entry_type, status, provider_order_id, handoff_session_id, contract_version_id,
                     rule_key, causative_event, party_id, counterparty_id, base_amount_minor,
                     amount_minor, currency, original_entry_id, reason, actor_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                entry.id, entry.entryType.name, entry.status.name, entry.providerOrderId,
                entry.handoffSessionId, entry.contractVersionId, entry.ruleKey, entry.causativeEvent,
                entry.partyId, entry.counterpartyId, entry.baseAmountMinor, entry.amountMinor,
                entry.currency, entry.originalEntryId, entry.reason, entry.actorId,
                Timestamp.from(entry.createdAt),
            )
            true
        } catch (_: DuplicateKeyException) {
            false
        }

    override fun findById(id: UUID): LedgerEntry? = jdbc.query("SELECT * FROM ledger_entries WHERE id = ?", { rs, _ -> rs.toEntry() }, id).firstOrNull()

    override fun findByOrderId(providerOrderId: UUID): List<LedgerEntry> =
        jdbc.query(
            "SELECT * FROM ledger_entries WHERE provider_order_id = ? ORDER BY created_at",
            { rs, _ -> rs.toEntry() },
            providerOrderId,
        )

    override fun findByOriginalEntryId(originalEntryId: UUID): List<LedgerEntry> =
        jdbc.query(
            "SELECT * FROM ledger_entries WHERE original_entry_id = ?",
            { rs, _ -> rs.toEntry() },
            originalEntryId,
        )

    override fun markReversed(id: UUID) {
        jdbc.update("UPDATE ledger_entries SET status = 'REVERSED' WHERE id = ?", id)
    }

    override fun markEarned(id: UUID) {
        jdbc.update("UPDATE ledger_entries SET status = 'EARNED' WHERE id = ?", id)
    }

    override fun getBalance(
        partyId: UUID,
        currency: String,
        from: Instant,
        to: Instant,
    ): PartyBalance {
        val row =
            jdbc.queryForMap(
                """
                SELECT
                    COALESCE(SUM(amount_minor), 0) AS total,
                    COALESCE(SUM(amount_minor) FILTER (WHERE status = 'PENDING'), 0) AS pending,
                    COALESCE(SUM(amount_minor) FILTER (WHERE status = 'EARNED'), 0) AS earned,
                    COALESCE(SUM(amount_minor) FILTER (WHERE status = 'SETTLED'), 0) AS settled
                FROM ledger_entries
                WHERE party_id = ? AND currency = ? AND created_at >= ? AND created_at < ?
                """.trimIndent(),
                partyId,
                currency,
                Timestamp.from(from),
                Timestamp.from(to),
            )
        return PartyBalance(
            partyId = partyId,
            currency = currency,
            totalMinor = (row["total"] as Number).toLong(),
            pendingMinor = (row["pending"] as Number).toLong(),
            earnedMinor = (row["earned"] as Number).toLong(),
            settledMinor = (row["settled"] as Number).toLong(),
        )
    }

    private fun ResultSet.toEntry() =
        LedgerEntry(
            id = UUID.fromString(getString("id")),
            entryType = EntryType.valueOf(getString("entry_type")),
            status = EntryStatus.valueOf(getString("status")),
            providerOrderId = UUID.fromString(getString("provider_order_id")),
            handoffSessionId = getString("handoff_session_id")?.let { UUID.fromString(it) },
            contractVersionId = UUID.fromString(getString("contract_version_id")),
            ruleKey = getString("rule_key"),
            causativeEvent = getString("causative_event"),
            partyId = UUID.fromString(getString("party_id")),
            counterpartyId = UUID.fromString(getString("counterparty_id")),
            baseAmountMinor = getLong("base_amount_minor"),
            amountMinor = getLong("amount_minor"),
            currency = getString("currency"),
            originalEntryId = getString("original_entry_id")?.let { UUID.fromString(it) },
            reason = getString("reason"),
            actorId = getString("actor_id")?.let { UUID.fromString(it) },
            createdAt = getTimestamp("created_at").toInstant(),
        )
}
