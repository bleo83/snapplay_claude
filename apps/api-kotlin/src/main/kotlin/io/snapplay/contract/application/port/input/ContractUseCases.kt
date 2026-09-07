package io.snapplay.contract.application.port.input

import io.snapplay.contract.domain.CommercialContract
import io.snapplay.contract.domain.ContractVersion
import java.time.Instant
import java.util.UUID

interface CreateContractUseCase {
    fun create(
        name: String,
        publisherOrganizationId: UUID,
        commerceOrganizationId: UUID,
        territories: List<String>,
        timezone: String,
        closeDayOfMonth: Int,
    ): CommercialContract
}

interface ActivateContractUseCase {
    fun activate(contractId: UUID): CommercialContract
}

interface ListContractsUseCase {
    fun list(organizationId: UUID): List<CommercialContract>
}

interface CreateContractVersionUseCase {
    fun create(
        contractId: UUID,
        currency: String,
        effectiveFrom: Instant,
        effectiveTo: Instant?,
        billableEvent: String,
        refundWindowDays: Int,
        rules: String,
        dataSharingPolicyId: UUID,
    ): ContractVersion
}

interface ApproveContractVersionUseCase {
    fun approve(
        versionId: UUID,
        approvedBy: UUID,
    ): ContractVersion
}

interface GetEffectiveVersionUseCase {
    fun getEffective(
        contractId: UUID,
        asOf: Instant,
    ): ContractVersion?
}
