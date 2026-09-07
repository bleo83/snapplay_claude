package io.snapplay.contract.infrastructure.web

import io.snapplay.contract.application.port.input.ActivateContractUseCase
import io.snapplay.contract.application.port.input.ApproveContractVersionUseCase
import io.snapplay.contract.application.port.input.CreateContractUseCase
import io.snapplay.contract.application.port.input.CreateContractVersionUseCase
import io.snapplay.contract.application.port.input.GetEffectiveVersionUseCase
import io.snapplay.contract.application.port.input.ListContractsUseCase
import io.snapplay.contract.domain.CommercialContract
import io.snapplay.contract.domain.ContractVersion
import io.snapplay.identity.PrincipalResolver
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/v1/contracts")
class ContractController(
    private val principalResolver: PrincipalResolver,
    private val createContractUseCase: CreateContractUseCase,
    private val activateContractUseCase: ActivateContractUseCase,
    private val listContractsUseCase: ListContractsUseCase,
    private val createVersionUseCase: CreateContractVersionUseCase,
    private val approveVersionUseCase: ApproveContractVersionUseCase,
    private val getEffectiveVersionUseCase: GetEffectiveVersionUseCase,
) {
    @GetMapping
    fun list(): List<CommercialContract> {
        val principal = principalResolver.resolve()
        return listContractsUseCase.list(principal.organizationId)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestBody request: CreateContractRequest,
    ): CommercialContract {
        val principal = principalResolver.resolve()
        return createContractUseCase.create(
            name = request.name,
            publisherOrganizationId = principal.organizationId,
            commerceOrganizationId = request.commerceOrganizationId,
            territories = request.territories,
            timezone = request.timezone,
            closeDayOfMonth = request.closeDayOfMonth,
        )
    }

    @PatchMapping("/{id}/activate")
    fun activate(
        @PathVariable id: UUID,
    ): CommercialContract = activateContractUseCase.activate(id)

    @PostMapping("/{contractId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    fun createVersion(
        @PathVariable contractId: UUID,
        @RequestBody request: CreateVersionRequest,
    ): ContractVersion =
        createVersionUseCase.create(
            contractId = contractId,
            currency = request.currency,
            effectiveFrom = request.effectiveFrom,
            effectiveTo = request.effectiveTo,
            billableEvent = request.billableEvent,
            refundWindowDays = request.refundWindowDays,
            rules = request.rules,
            dataSharingPolicyId = request.dataSharingPolicyId,
        )

    @PatchMapping("/versions/{versionId}/approve")
    fun approveVersion(
        @PathVariable versionId: UUID,
    ): ContractVersion {
        val principal = principalResolver.resolve()
        return approveVersionUseCase.approve(versionId, principal.userId)
    }

    @GetMapping("/{contractId}/versions/effective")
    fun getEffective(
        @PathVariable contractId: UUID,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        at: Instant?,
    ): ContractVersion? = getEffectiveVersionUseCase.getEffective(contractId, at ?: Instant.now())
}

data class CreateContractRequest(
    val name: String,
    val commerceOrganizationId: UUID,
    val territories: List<String>,
    val timezone: String = "America/Argentina/Buenos_Aires",
    val closeDayOfMonth: Int = 1,
)

data class CreateVersionRequest(
    val currency: String,
    val effectiveFrom: Instant,
    val effectiveTo: Instant? = null,
    val billableEvent: String,
    val refundWindowDays: Int = 0,
    val rules: String = "{}",
    val dataSharingPolicyId: UUID,
)
