package io.snapplay.organization.application.usecase

import io.snapplay.common.ForbiddenException
import io.snapplay.identity.OrgRole
import io.snapplay.identity.RequestPrincipal
import io.snapplay.organization.application.port.input.CreateOrganizationCommand
import io.snapplay.organization.application.port.input.CreateOrganizationUseCase
import io.snapplay.organization.application.port.output.CreateOrganizationInput
import io.snapplay.organization.application.port.output.OrganizationRepository
import io.snapplay.organization.domain.OrganizationProfile
import io.snapplay.organization.domain.OrganizationType
import org.springframework.stereotype.Service

@Service
class CreateOrganizationUseCaseImpl(
    private val organizationRepository: OrganizationRepository,
) : CreateOrganizationUseCase {
    override fun create(
        command: CreateOrganizationCommand,
        principal: RequestPrincipal,
    ): OrganizationProfile {
        principal.requireAnyRole(OrgRole.ORGANIZATION_ADMIN)

        val callerOrg =
            organizationRepository.findById(principal.organizationId)
                ?: throw ForbiddenException("Caller organization not found")

        if (callerOrg.organizationType != OrganizationType.ORCHESTRATOR) {
            throw ForbiddenException("Only ORCHESTRATOR organizations may create new organizations")
        }

        return organizationRepository.create(
            CreateOrganizationInput(
                legalName = command.legalName,
                displayName = command.displayName,
                organizationType = command.organizationType,
                country = command.country,
                defaultCurrency = command.defaultCurrency,
                timezone = command.timezone,
            ),
        )
    }
}
