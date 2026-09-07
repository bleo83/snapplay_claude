package io.snapplay.contract.domain

import java.time.Instant
import java.util.UUID

enum class ContractStatus { DRAFT, ACTIVE, EXPIRED, TERMINATED }

enum class ContractVersionStatus { DRAFT, IN_REVIEW, APPROVED }

enum class BillableEvent {
    ORDER_PLACED,
    ORDER_CONFIRMED,
    DELIVERED,
    REFUND_WINDOW_ELAPSED,
    PROVIDER_SETTLED,
}

data class CommercialContract(
    val id: UUID,
    val name: String,
    val publisherOrganizationId: UUID,
    val commerceOrganizationId: UUID,
    val territories: List<String>,
    val timezone: String,
    val closeDayOfMonth: Int,
    val status: ContractStatus,
    val activatedAt: Instant?,
    val createdAt: Instant,
)

data class ContractVersion(
    val id: UUID,
    val contractId: UUID,
    val version: Int,
    val status: ContractVersionStatus,
    val currency: String,
    val effectiveFrom: Instant,
    val effectiveTo: Instant?,
    val billableEvent: BillableEvent,
    val refundWindowDays: Int,
    val rules: String,
    val dataSharingPolicyId: UUID,
    val approvedAt: Instant?,
    val approvedBy: UUID?,
    val createdAt: Instant,
)
