package io.snapplay.contract.application.usecase

import io.snapplay.common.NotFoundException
import io.snapplay.common.ValidationException
import io.snapplay.contract.application.port.input.ActivateContractUseCase
import io.snapplay.contract.application.port.input.ApproveContractVersionUseCase
import io.snapplay.contract.application.port.input.CreateContractUseCase
import io.snapplay.contract.application.port.input.CreateContractVersionUseCase
import io.snapplay.contract.application.port.input.GetEffectiveVersionUseCase
import io.snapplay.contract.application.port.input.ListContractsUseCase
import io.snapplay.contract.application.port.output.ContractRepository
import io.snapplay.contract.application.port.output.CreateContractInput
import io.snapplay.contract.application.port.output.CreateVersionInput
import io.snapplay.contract.domain.CommercialContract
import io.snapplay.contract.domain.ContractStatus
import io.snapplay.contract.domain.ContractVersion
import io.snapplay.contract.domain.ContractVersionStatus
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class CreateContractUseCaseImpl(
    private val repo: ContractRepository,
) : CreateContractUseCase {
    override fun create(
        name: String,
        publisherOrganizationId: UUID,
        commerceOrganizationId: UUID,
        territories: List<String>,
        timezone: String,
        closeDayOfMonth: Int,
    ): CommercialContract =
        repo.createContract(
            CreateContractInput(name, publisherOrganizationId, commerceOrganizationId, territories, timezone, closeDayOfMonth),
        )
}

@Service
class ActivateContractUseCaseImpl(
    private val repo: ContractRepository,
) : ActivateContractUseCase {
    override fun activate(contractId: UUID): CommercialContract {
        val contract =
            repo.findContract(contractId) ?: throw NotFoundException("Contract $contractId not found")
        if (contract.status != ContractStatus.DRAFT) {
            throw ValidationException("Contract is ${contract.status}, only DRAFT contracts can be activated")
        }
        // Must have at least one APPROVED version
        val versions = repo.findVersionsByContract(contractId)
        if (versions.none { it.status == ContractVersionStatus.APPROVED }) {
            throw ValidationException("Contract has no approved versions — cannot activate")
        }
        repo.activateContract(contractId, Instant.now())
        return repo.findContract(contractId)!!
    }
}

@Service
class ListContractsUseCaseImpl(
    private val repo: ContractRepository,
) : ListContractsUseCase {
    override fun list(organizationId: UUID): List<CommercialContract> = repo.findContractsByOrg(organizationId)
}

@Service
class CreateContractVersionUseCaseImpl(
    private val repo: ContractRepository,
) : CreateContractVersionUseCase {
    override fun create(
        contractId: UUID,
        currency: String,
        effectiveFrom: Instant,
        effectiveTo: Instant?,
        billableEvent: String,
        refundWindowDays: Int,
        rules: String,
        dataSharingPolicyId: UUID,
    ): ContractVersion {
        repo.findContract(contractId) ?: throw NotFoundException("Contract $contractId not found")

        if (repo.hasOverlappingApprovedVersion(contractId, effectiveFrom, effectiveTo)) {
            throw ValidationException("An approved version already overlaps with the requested effective period")
        }

        return repo.createVersion(
            CreateVersionInput(contractId, currency, effectiveFrom, effectiveTo, billableEvent, refundWindowDays, rules, dataSharingPolicyId),
        )
    }
}

@Service
class ApproveContractVersionUseCaseImpl(
    private val repo: ContractRepository,
) : ApproveContractVersionUseCase {
    override fun approve(
        versionId: UUID,
        approvedBy: UUID,
    ): ContractVersion {
        val version =
            repo.findVersion(versionId) ?: throw NotFoundException("Contract version $versionId not found")

        if (version.status == ContractVersionStatus.APPROVED) {
            throw ValidationException("Version is already approved and immutable")
        }
        if (version.status != ContractVersionStatus.DRAFT && version.status != ContractVersionStatus.IN_REVIEW) {
            throw ValidationException("Version is ${version.status}, expected DRAFT or IN_REVIEW")
        }

        // Final overlap check at approval time
        if (repo.hasOverlappingApprovedVersion(version.contractId, version.effectiveFrom, version.effectiveTo, excludeVersionId = versionId)) {
            throw ValidationException("An approved version already overlaps with this version's effective period")
        }

        repo.approveVersion(versionId, approvedBy, Instant.now())
        return repo.findVersion(versionId)!!
    }
}

@Service
class GetEffectiveVersionUseCaseImpl(
    private val repo: ContractRepository,
) : GetEffectiveVersionUseCase {
    override fun getEffective(
        contractId: UUID,
        asOf: Instant,
    ): ContractVersion? = repo.findEffectiveVersion(contractId, asOf)
}
