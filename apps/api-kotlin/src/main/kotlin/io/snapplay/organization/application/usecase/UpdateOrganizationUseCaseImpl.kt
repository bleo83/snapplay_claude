package io.snapplay.organization.application.usecase

import io.snapplay.identity.OrgRole
import io.snapplay.identity.RequestPrincipal
import io.snapplay.organization.application.port.input.UpdateOrganizationUseCase
import io.snapplay.organization.application.port.output.OrganizationRepository
import io.snapplay.organization.application.port.output.UpdateOrganizationInput
import io.snapplay.organization.domain.OrganizationProfile
import org.springframework.stereotype.Service

@Service
class UpdateOrganizationUseCaseImpl(
    private val organizationRepository: OrganizationRepository,
) : UpdateOrganizationUseCase {
    override fun update(
        principal: RequestPrincipal,
        input: UpdateOrganizationInput,
    ): OrganizationProfile {
        principal.requireAnyRole(OrgRole.ORGANIZATION_ADMIN)
        return organizationRepository.update(principal.organizationId, input)
    }
}
