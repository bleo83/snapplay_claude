package io.snapplay.organization.infrastructure.web

import io.snapplay.identity.PrincipalResolver
import io.snapplay.organization.application.port.input.GetOrganizationUseCase
import io.snapplay.organization.application.port.input.UpdateOrganizationUseCase
import io.snapplay.organization.application.port.output.UpdateOrganizationInput
import io.snapplay.organization.domain.OrganizationProfile
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
    private val getOrganizationUseCase: GetOrganizationUseCase,
    private val updateOrganizationUseCase: UpdateOrganizationUseCase,
) {
    @GetMapping
    fun get(): OrganizationProfile = getOrganizationUseCase.get(principalResolver.resolve())

    @PatchMapping
    fun update(
        @Valid @RequestBody request: UpdateOrganizationRequest,
    ): OrganizationProfile =
        updateOrganizationUseCase.update(
            principalResolver.resolve(),
            UpdateOrganizationInput(
                legalName = request.legalName,
                displayName = request.displayName,
                country = request.country,
                defaultCurrency = request.defaultCurrency,
                timezone = request.timezone,
            ),
        )
}
