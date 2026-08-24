package io.snapplay.organization.application.usecase

import io.snapplay.common.NotFoundException
import io.snapplay.identity.RequestPrincipal
import io.snapplay.organization.application.port.input.GetOrganizationUseCase
import io.snapplay.organization.application.port.output.OrganizationRepository
import io.snapplay.organization.domain.OrganizationProfile
import org.springframework.stereotype.Service

@Service
class GetOrganizationUseCaseImpl(
    private val organizationRepository: OrganizationRepository,
) : GetOrganizationUseCase {
    override fun get(principal: RequestPrincipal): OrganizationProfile =
        organizationRepository.findById(principal.organizationId)
            ?: throw NotFoundException("Organization not found")
}
