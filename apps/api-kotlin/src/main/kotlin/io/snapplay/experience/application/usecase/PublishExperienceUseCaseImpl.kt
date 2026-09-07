package io.snapplay.experience.application.usecase

import io.snapplay.common.NotFoundException
import io.snapplay.common.ValidationException
import io.snapplay.experience.application.port.input.PublishExperienceUseCase
import io.snapplay.experience.application.port.input.ValidateExperienceUseCase
import io.snapplay.experience.application.port.output.ExperienceRepository
import io.snapplay.experience.domain.Experience
import io.snapplay.identity.OrgRole
import io.snapplay.identity.RequestPrincipal
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PublishExperienceUseCaseImpl(
    private val experienceRepository: ExperienceRepository,
    private val validateUseCase: ValidateExperienceUseCase,
) : PublishExperienceUseCase {
    override fun publish(
        id: UUID,
        principal: RequestPrincipal,
    ): Experience {
        principal.requireAnyRole(OrgRole.ORGANIZATION_ADMIN, OrgRole.PUBLISHER)

        experienceRepository.findById(principal.organizationId, id)
            ?: throw NotFoundException("Experience $id not found")

        // Re-run all validations at publish time — fail with structured errors
        val result = validateUseCase.validate(id, principal)
        if (!result.valid) {
            val messages = result.errors.joinToString("; ") { "${it.field}: ${it.message}" }
            throw ValidationException("Publish blocked: $messages")
        }

        return experienceRepository.publish(principal.organizationId, principal.userId, id)
    }
}
