package io.snapplay.experience.application.usecase

import io.snapplay.experience.application.port.input.CreateExperienceCommand
import io.snapplay.experience.application.port.input.CreateExperienceUseCase
import io.snapplay.experience.application.port.output.CreateExperienceInput
import io.snapplay.experience.application.port.output.ExperienceRepository
import io.snapplay.experience.domain.Experience
import io.snapplay.identity.OrgRole
import io.snapplay.identity.RequestPrincipal
import org.springframework.stereotype.Service

@Service
class CreateExperienceUseCaseImpl(
    private val experienceRepository: ExperienceRepository,
) : CreateExperienceUseCase {
    override fun create(
        command: CreateExperienceCommand,
        principal: RequestPrincipal,
    ): Experience {
        principal.requireAnyRole(OrgRole.ORGANIZATION_ADMIN, OrgRole.CONTENT_MANAGER, OrgRole.PUBLISHER)
        return experienceRepository.create(
            principal.organizationId,
            principal.userId,
            CreateExperienceInput(
                name = command.name,
                contextTitle = command.contextTitle,
                handoffMode = command.handoffMode,
                productCount = command.productCount,
                startsAt = command.startsAt,
                endsAt = command.endsAt,
            ),
        )
    }
}
