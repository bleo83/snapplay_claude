package io.snapplay.experience.application.usecase

import io.snapplay.common.NotFoundException
import io.snapplay.common.ValidationException
import io.snapplay.experience.application.port.input.CloneExperienceUseCase
import io.snapplay.experience.application.port.output.ExperienceRepository
import io.snapplay.experience.domain.Experience
import io.snapplay.experience.domain.ExperienceStatus
import io.snapplay.identity.OrgRole
import io.snapplay.identity.RequestPrincipal
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CloneExperienceUseCaseImpl(
    private val experienceRepository: ExperienceRepository,
) : CloneExperienceUseCase {
    override fun clone(
        id: UUID,
        principal: RequestPrincipal,
    ): Experience {
        principal.requireAnyRole(OrgRole.ORGANIZATION_ADMIN, OrgRole.CONTENT_MANAGER, OrgRole.PUBLISHER)

        val experience =
            experienceRepository.findById(principal.organizationId, id)
                ?: throw NotFoundException("Experience $id not found")

        if (experience.status != ExperienceStatus.PUBLISHED && experience.status != ExperienceStatus.PAUSED) {
            throw ValidationException(
                "Cannot clone experience in status ${experience.status}. Expected PUBLISHED or PAUSED.",
            )
        }

        return experienceRepository.clone(principal.organizationId, principal.userId, id)
    }
}
