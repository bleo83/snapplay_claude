package io.snapplay.organization.application.port.input

import io.snapplay.identity.RequestPrincipal
import io.snapplay.organization.domain.OrganizationProfile

interface GetOrganizationUseCase {
    fun get(principal: RequestPrincipal): OrganizationProfile
}
