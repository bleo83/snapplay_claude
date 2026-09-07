package io.snapplay.contract.infrastructure.persistence

import io.snapplay.contract.application.port.output.ContractRepository
import io.snapplay.contract.application.port.output.CreateContractInput
import io.snapplay.contract.application.port.output.CreateVersionInput
import io.snapplay.contract.domain.BillableEvent
import io.snapplay.contract.domain.CommercialContract
import io.snapplay.contract.domain.ContractStatus
import io.snapplay.contract.domain.ContractVersion
import io.snapplay.contract.domain.ContractVersionStatus
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoContractRepository : ContractRepository {
    private val contracts = ConcurrentHashMap<UUID, CommercialContract>()
    private val versions = ConcurrentHashMap<UUID, ContractVersion>()

    fun clear() {
        contracts.clear()
        versions.clear()
    }

    fun allContracts(): Collection<CommercialContract> = contracts.values

    fun allVersions(): Collection<ContractVersion> = versions.values

    override fun createContract(input: CreateContractInput): CommercialContract {
        val contract =
            CommercialContract(
                id = UUID.randomUUID(),
                name = input.name,
                publisherOrganizationId = input.publisherOrganizationId,
                commerceOrganizationId = input.commerceOrganizationId,
                territories = input.territories,
                timezone = input.timezone,
                closeDayOfMonth = input.closeDayOfMonth,
                status = ContractStatus.DRAFT,
                activatedAt = null,
                createdAt = Instant.now(),
            )
        contracts[contract.id] = contract
        return contract
    }

    override fun findContract(id: UUID): CommercialContract? = contracts[id]

    override fun findContractsByOrg(organizationId: UUID): List<CommercialContract> =
        contracts.values.filter {
            it.publisherOrganizationId == organizationId || it.commerceOrganizationId == organizationId
        }

    override fun activateContract(
        id: UUID,
        activatedAt: Instant,
    ) {
        contracts.computeIfPresent(id) { _, c -> c.copy(status = ContractStatus.ACTIVE, activatedAt = activatedAt) }
    }

    override fun createVersion(input: CreateVersionInput): ContractVersion {
        val nextVersion = versions.values.count { it.contractId == input.contractId } + 1
        val version =
            ContractVersion(
                id = UUID.randomUUID(),
                contractId = input.contractId,
                version = nextVersion,
                status = ContractVersionStatus.DRAFT,
                currency = input.currency,
                effectiveFrom = input.effectiveFrom,
                effectiveTo = input.effectiveTo,
                billableEvent = BillableEvent.valueOf(input.billableEvent),
                refundWindowDays = input.refundWindowDays,
                rules = input.rules,
                dataSharingPolicyId = input.dataSharingPolicyId,
                approvedAt = null,
                approvedBy = null,
                createdAt = Instant.now(),
            )
        versions[version.id] = version
        return version
    }

    override fun findVersion(id: UUID): ContractVersion? = versions[id]

    override fun findVersionsByContract(contractId: UUID): List<ContractVersion> =
        versions.values.filter { it.contractId == contractId }.sortedBy { it.version }

    override fun findEffectiveVersion(
        contractId: UUID,
        asOf: Instant,
    ): ContractVersion? =
        versions.values.firstOrNull {
            it.contractId == contractId &&
                it.status == ContractVersionStatus.APPROVED &&
                !asOf.isBefore(it.effectiveFrom) &&
                (it.effectiveTo == null || asOf.isBefore(it.effectiveTo))
        }

    override fun hasOverlappingApprovedVersion(
        contractId: UUID,
        from: Instant,
        to: Instant?,
        excludeVersionId: UUID?,
    ): Boolean =
        versions.values.any {
            it.contractId == contractId &&
                it.status == ContractVersionStatus.APPROVED &&
                it.id != excludeVersionId &&
                periodsOverlap(it.effectiveFrom, it.effectiveTo, from, to)
        }

    override fun approveVersion(
        id: UUID,
        approvedBy: UUID,
        approvedAt: Instant,
    ) {
        versions.computeIfPresent(id) { _, v ->
            v.copy(status = ContractVersionStatus.APPROVED, approvedAt = approvedAt, approvedBy = approvedBy)
        }
    }

    private fun periodsOverlap(
        aFrom: Instant,
        aTo: Instant?,
        bFrom: Instant,
        bTo: Instant?,
    ): Boolean {
        val aEnd = aTo ?: Instant.MAX
        val bEnd = bTo ?: Instant.MAX
        return aFrom.isBefore(bEnd) && bFrom.isBefore(aEnd)
    }
}
