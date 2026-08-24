package io.snapplay.organization.application.port.input

import io.snapplay.identity.RequestPrincipal
import io.snapplay.organization.application.port.output.UpdateOrganizationInput
import io.snapplay.organization.domain.OrganizationProfile

interface UpdateOrganizationUseCase {
    fun update(
        principal: RequestPrincipal,
        input: UpdateOrganizationInput,
    ): OrganizationProfile
}
