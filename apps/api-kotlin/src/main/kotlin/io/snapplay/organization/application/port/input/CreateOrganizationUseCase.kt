package io.snapplay.organization.application.port.input

import io.snapplay.identity.RequestPrincipal
import io.snapplay.organization.domain.OrganizationProfile
import io.snapplay.organization.domain.OrganizationType

data class CreateOrganizationCommand(
    val legalName: String,
    val displayName: String,
    val organizationType: OrganizationType,
    val country: String,
    val defaultCurrency: String,
    val timezone: String,
)

fun interface CreateOrganizationUseCase {
    fun create(
        command: CreateOrganizationCommand,
        principal: RequestPrincipal,
    ): OrganizationProfile
}
