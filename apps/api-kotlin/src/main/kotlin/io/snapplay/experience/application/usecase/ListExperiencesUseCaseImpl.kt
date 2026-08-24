package io.snapplay.experience.application.usecase

import io.snapplay.experience.application.port.input.ListExperiencesUseCase
import io.snapplay.experience.application.port.output.ExperienceRepository
import io.snapplay.experience.domain.Experience
import io.snapplay.identity.RequestPrincipal
import org.springframework.stereotype.Service

@Service
class ListExperiencesUseCaseImpl(
    private val experienceRepository: ExperienceRepository,
) : ListExperiencesUseCase {

    override fun list(principal: RequestPrincipal): List<Experience> =
        experienceRepository.findAll(principal.organizationId)
}
