package io.snapplay.organization

import io.snapplay.common.NotFoundException
import io.snapplay.identity.OrgRole
import io.snapplay.identity.PrincipalResolver
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class UpdateOrganizationRequest(
    @field:Size(min = 2, max = 200) val legalName: String,
    @field:Size(min = 2, max = 200) val displayName: String,
    @field:Pattern(regexp = "^[A-Z]{2}$", message = "Must be a 2-letter ISO country code") val country: String,
    @field:Pattern(regexp = "^[A-Z]{3}$", message = "Must be a 3-letter ISO currency code") val defaultCurrency: String,
    @field:Size(min = 1, max = 100) val timezone: String,
)

@RestController
@RequestMapping("/v1/organization")
class OrganizationController(
    private val principalResolver: PrincipalResolver,
    private val organizationRepository: OrganizationRepository,
) {
    @GetMapping
    fun get(): OrganizationProfile {
        val principal = principalResolver.resolve()
        return organizationRepository.findById(principal.organizationId)
            ?: throw NotFoundException("Organization not found")
    }

    @PatchMapping
    fun update(
        @Valid @RequestBody request: UpdateOrganizationRequest,
    ): OrganizationProfile {
        val principal = principalResolver.resolve()
        principal.requireAnyRole(OrgRole.ORGANIZATION_ADMIN)
        return organizationRepository.update(
            principal.organizationId,
            UpdateOrganizationInput(
                legalName = request.legalName,
                displayName = request.displayName,
                country = request.country,
                defaultCurrency = request.defaultCurrency,
                timezone = request.timezone,
            ),
        )
    }
}
