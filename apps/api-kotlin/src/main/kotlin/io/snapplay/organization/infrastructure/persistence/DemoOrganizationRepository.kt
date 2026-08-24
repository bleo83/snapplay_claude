package io.snapplay.organization.infrastructure.persistence

import io.snapplay.organization.application.port.output.OrganizationRepository
import io.snapplay.organization.application.port.output.UpdateOrganizationInput
import io.snapplay.organization.domain.OrganizationProfile
import io.snapplay.organization.domain.OrganizationStatus
import io.snapplay.organization.domain.OrganizationType
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.UUID

private val DEMO_ORG_ID = UUID.fromString("50d2d7eb-c8fd-42df-bbb0-0ea0e2271090")

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoOrganizationRepository : OrganizationRepository {
    private val org =
        OrganizationProfile(
            id = DEMO_ORG_ID,
            legalName = "Disney Streaming Services Argentina S.A.",
            displayName = "Disney Argentina",
            organizationType = OrganizationType.CONTENT_PROVIDER,
            country = "AR",
            defaultCurrency = "ARS",
            timezone = "America/Argentina/Buenos_Aires",
            status = OrganizationStatus.ACTIVE,
        )

    // Mutable copy for PATCH support in demo mode
    private var current = org

    override fun findById(organizationId: UUID): OrganizationProfile = current

    override fun update(
        organizationId: UUID,
        input: UpdateOrganizationInput,
    ): OrganizationProfile {
        current =
            current.copy(
                legalName = input.legalName,
                displayName = input.displayName,
                country = input.country,
                defaultCurrency = input.defaultCurrency,
                timezone = input.timezone,
            )
        return current
    }
}
