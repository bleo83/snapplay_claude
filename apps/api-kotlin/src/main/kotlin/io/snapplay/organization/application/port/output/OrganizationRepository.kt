package io.snapplay.organization.application.port.output

import io.snapplay.organization.domain.OrganizationProfile
import io.snapplay.organization.domain.OrganizationType
import java.util.UUID

data class CreateOrganizationInput(
    val legalName: String,
    val displayName: String,
    val organizationType: OrganizationType,
    val country: String,
    val defaultCurrency: String,
    val timezone: String,
)

data class UpdateOrganizationInput(
    val legalName: String,
    val displayName: String,
    val country: String,
    val defaultCurrency: String,
    val timezone: String,
)

interface OrganizationRepository {
    fun findById(organizationId: UUID): OrganizationProfile?

    fun create(input: CreateOrganizationInput): OrganizationProfile

    fun update(
        organizationId: UUID,
        input: UpdateOrganizationInput,
    ): OrganizationProfile
}
