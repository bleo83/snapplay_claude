package io.snapplay.contract.application.port.output

import io.snapplay.contract.domain.CommercialContract
import io.snapplay.contract.domain.ContractVersion
import java.time.Instant
import java.util.UUID

data class CreateContractInput(
    val name: String,
    val publisherOrganizationId: UUID,
    val commerceOrganizationId: UUID,
    val territories: List<String>,
    val timezone: String,
    val closeDayOfMonth: Int,
)

data class CreateVersionInput(
    val contractId: UUID,
    val currency: String,
    val effectiveFrom: Instant,
    val effectiveTo: Instant?,
    val billableEvent: String,
    val refundWindowDays: Int,
    val rules: String,
    val dataSharingPolicyId: UUID,
)

interface ContractRepository {
    fun createContract(input: CreateContractInput): CommercialContract

    fun findContract(id: UUID): CommercialContract?

    fun findContractsByOrg(organizationId: UUID): List<CommercialContract>

    fun activateContract(
        id: UUID,
        activatedAt: Instant,
    )

    fun createVersion(input: CreateVersionInput): ContractVersion

    fun findVersion(id: UUID): ContractVersion?

    fun findVersionsByContract(contractId: UUID): List<ContractVersion>

    /** Finds the APPROVED version effective at [asOf] for the given contract. */
    fun findEffectiveVersion(
        contractId: UUID,
        asOf: Instant,
    ): ContractVersion?

    /** Checks if any APPROVED version overlaps with [from, to) for this contract. */
    fun hasOverlappingApprovedVersion(
        contractId: UUID,
        from: Instant,
        to: Instant?,
        excludeVersionId: UUID? = null,
    ): Boolean

    fun approveVersion(
        id: UUID,
        approvedBy: UUID,
        approvedAt: Instant,
    )
}
