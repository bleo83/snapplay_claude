package io.snapplay.organization

import java.util.UUID

data class UpdateOrganizationInput(
    val legalName: String,
    val displayName: String,
    val country: String,
    val defaultCurrency: String,
    val timezone: String,
)

interface OrganizationRepository {
    fun findById(organizationId: UUID): OrganizationProfile?

    fun update(
        organizationId: UUID,
        input: UpdateOrganizationInput,
    ): OrganizationProfile
}
