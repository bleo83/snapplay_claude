package io.snapplay.experience.infrastructure.web

import io.snapplay.experience.application.port.input.CreateExperienceCommand
import io.snapplay.experience.application.port.input.CreateExperienceUseCase
import io.snapplay.experience.application.port.input.ListExperiencesUseCase
import io.snapplay.experience.domain.CommerceDestination
import io.snapplay.experience.domain.Experience
import io.snapplay.experience.domain.HandoffMode
import io.snapplay.identity.PrincipalResolver
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class CommerceDestinationRequest(
    @field:NotBlank val providerStoreId: String,
    @field:NotBlank val providerCategoryId: String,
)

data class CreateExperienceRequest(
    @field:Size(min = 3, max = 160) val name: String,
    @field:Size(min = 2, max = 160) val contextTitle: String,
    val connectionId: UUID,
    @field:Pattern(regexp = "^[A-Z]{2}$", message = "Must be a 2-letter ISO country code") val territory: String,
    @field:Valid val destination: CommerceDestinationRequest,
    val handoffMode: HandoffMode,
    @field:Min(0) @field:Max(100) val productCount: Int,
    val startsAt: Instant,
    val endsAt: Instant? = null,
)

data class ExperienceListResponse(
    val items: List<Experience>,
    val nextCursor: String?,
)

@RestController
@RequestMapping("/v1/experiences")
class ExperienceController(
    private val principalResolver: PrincipalResolver,
    private val listExperiencesUseCase: ListExperiencesUseCase,
    private val createExperienceUseCase: CreateExperienceUseCase,
) {
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam cursor: String? = null,
    ): ExperienceListResponse {
        val safeLimit = limit.coerceIn(1, 200)
        val principal = principalResolver.resolve()
        val result = listExperiencesUseCase.list(principal, safeLimit, cursor)
        return ExperienceListResponse(items = result.items, nextCursor = result.nextCursor)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CreateExperienceRequest,
    ): Experience {
        val principal = principalResolver.resolve()
        return createExperienceUseCase.create(
            CreateExperienceCommand(
                name = request.name,
                contextTitle = request.contextTitle,
                connectionId = request.connectionId,
                territory = request.territory,
                destination =
                    CommerceDestination(
                        providerStoreId = request.destination.providerStoreId,
                        providerCategoryId = request.destination.providerCategoryId,
                    ),
                handoffMode = request.handoffMode,
                productCount = request.productCount,
                startsAt = request.startsAt,
                endsAt = request.endsAt,
            ),
            principal,
        )
    }
}
