package io.snapplay.experience.application.usecase

import io.snapplay.common.NotFoundException
import io.snapplay.common.ValidationException
import io.snapplay.experience.application.port.input.RetireExperienceUseCase
import io.snapplay.experience.application.port.output.ExperienceRepository
import io.snapplay.experience.domain.Experience
import io.snapplay.experience.domain.ExperienceStatus
import io.snapplay.identity.OrgRole
import io.snapplay.identity.RequestPrincipal
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class RetireExperienceUseCaseImpl(
    private val experienceRepository: ExperienceRepository,
) : RetireExperienceUseCase {
    override fun retire(
        id: UUID,
        principal: RequestPrincipal,
    ): Experience {
        principal.requireAnyRole(OrgRole.ORGANIZATION_ADMIN, OrgRole.PUBLISHER)

        val experience =
            experienceRepository.findById(principal.organizationId, id)
                ?: throw NotFoundException("Experience $id not found")

        if (experience.status == ExperienceStatus.RETIRED) {
            throw ValidationException("Experience $id is already RETIRED.")
        }

        return experienceRepository.retire(principal.organizationId, principal.userId, id)
    }
}
