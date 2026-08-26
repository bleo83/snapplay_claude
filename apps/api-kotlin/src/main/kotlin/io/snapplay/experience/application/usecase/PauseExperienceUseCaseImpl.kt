package io.snapplay.experience.application.usecase

import io.snapplay.common.NotFoundException
import io.snapplay.common.ValidationException
import io.snapplay.experience.application.port.input.PauseExperienceUseCase
import io.snapplay.experience.application.port.output.ExperienceRepository
import io.snapplay.experience.domain.Experience
import io.snapplay.experience.domain.ExperienceStatus
import io.snapplay.identity.OrgRole
import io.snapplay.identity.RequestPrincipal
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PauseExperienceUseCaseImpl(
    private val experienceRepository: ExperienceRepository,
) : PauseExperienceUseCase {
    override fun pause(
        id: UUID,
        principal: RequestPrincipal,
    ): Experience {
        principal.requireAnyRole(OrgRole.ORGANIZATION_ADMIN, OrgRole.PUBLISHER)

        val experience =
            experienceRepository.findById(principal.organizationId, id)
                ?: throw NotFoundException("Experience $id not found")

        if (experience.status != ExperienceStatus.PUBLISHED) {
            throw ValidationException(
                "Cannot pause experience in status ${experience.status}. Expected PUBLISHED.",
            )
        }

        return experienceRepository.pause(principal.organizationId, principal.userId, id)
    }
}
