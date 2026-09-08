package io.snapplay.retention.domain

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Data classification and retention rules.
 * Each class has an owner, purpose, and approved retention period.
 */
enum class DataClass(
    val tableName: String,
    val owner: String,
    val purpose: String,
    val retention: Duration,
    val anonymizable: Boolean,
) {
    HANDOFF_SESSION(
        "handoff_sessions",
        "Analytics",
        "Attribution tracking",
        Duration.ofDays(90),
        true,
    ),
    PARTNER_EVENT(
        "partner_events",
        "Integration",
        "Webhook audit + dedup",
        Duration.ofDays(90),
        true,
    ),
    POLLING_TOKEN(
        "handoff_polling_tokens",
        "Integration",
        "Disney polling fallback",
        Duration.ofDays(7),
        false,
    ),
    OUTBOX_EVENT(
        "outbox_events",
        "Platform",
        "Async processing",
        Duration.ofDays(30),
        false,
    ),
    AUDIT_EVENT(
        "audit_events",
        "Compliance",
        "Regulatory audit trail",
        Duration.ofDays(2555),
        false,
    ),
    LEDGER_ENTRY(
        "ledger_entries",
        "Finance",
        "Financial evidence",
        Duration.ofDays(2555),
        false,
    ),
    MILESTONE_DELIVERY(
        "milestone_deliveries",
        "Integration",
        "Disney notification log",
        Duration.ofDays(90),
        false,
    ),
}

data class RetentionAction(
    val dataClass: DataClass,
    val action: String,
    val rowsAffected: Int,
    val executedAt: Instant,
)

data class DataExport(
    val id: UUID,
    val exportedBy: UUID,
    val dataClass: String,
    val filters: String,
    val rowCount: Int,
    val checksum: String,
    val expiresAt: Instant,
    val createdAt: Instant,
)
