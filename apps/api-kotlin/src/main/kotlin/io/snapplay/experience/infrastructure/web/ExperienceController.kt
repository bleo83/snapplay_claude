package io.snapplay.experience.infrastructure.web

import io.snapplay.experience.application.port.input.CreateExperienceCommand
import io.snapplay.experience.application.port.input.CreateExperienceUseCase
import io.snapplay.experience.application.port.input.ListExperiencesUseCase
import io.snapplay.experience.domain.Experience
import io.snapplay.experience.domain.HandoffMode
import io.snapplay.identity.PrincipalResolver
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class CreateExperienceRequest(
    @field:Size(min = 3, max = 160) val name: String,
    @field:Size(min = 2, max = 160) val contextTitle: String,
    @field:Size(min = 2, max = 80) val channel: String,
    val handoffMode: HandoffMode,
    @field:Min(0) @field:Max(100) val productCount: Int,
    val startsAt: Instant,
    val endsAt: Instant? = null,
)

data class ExperienceListResponse(
    val items: List<Experience>,
)

@RestController
@RequestMapping("/v1/experiences")
class ExperienceController(
    private val principalResolver: PrincipalResolver,
    private val listExperiencesUseCase: ListExperiencesUseCase,
    private val createExperienceUseCase: CreateExperienceUseCase,
) {
    @GetMapping
    fun list(): ExperienceListResponse {
        val principal = principalResolver.resolve()
        return ExperienceListResponse(listExperiencesUseCase.list(principal))
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
                handoffMode = request.handoffMode,
                productCount = request.productCount,
                startsAt = request.startsAt,
                endsAt = request.endsAt,
            ),
            principal,
        )
    }
}
