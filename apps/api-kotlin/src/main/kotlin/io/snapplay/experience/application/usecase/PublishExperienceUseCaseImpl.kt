package io.snapplay.experience.application.usecase

import io.snapplay.common.NotFoundException
import io.snapplay.common.ValidationException
import io.snapplay.connection.application.port.output.ConnectionRepository
import io.snapplay.connection.domain.Capability
import io.snapplay.connection.domain.ConnectionStatus
import io.snapplay.experience.application.port.input.PublishExperienceUseCase
import io.snapplay.experience.application.port.output.ExperienceRepository
import io.snapplay.experience.domain.Experience
import io.snapplay.experience.domain.ExperienceStatus
import io.snapplay.identity.OrgRole
import io.snapplay.identity.RequestPrincipal
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PublishExperienceUseCaseImpl(
    private val experienceRepository: ExperienceRepository,
    private val connectionRepository: ConnectionRepository,
) : PublishExperienceUseCase {
    override fun publish(
        id: UUID,
        principal: RequestPrincipal,
    ): Experience {
        principal.requireAnyRole(OrgRole.ORGANIZATION_ADMIN, OrgRole.PUBLISHER)

        val experience =
            experienceRepository.findById(principal.organizationId, id)
                ?: throw NotFoundException("Experience $id not found")

        if (experience.status != ExperienceStatus.DRAFT && experience.status != ExperienceStatus.IN_REVIEW) {
            throw ValidationException(
                "Cannot publish experience in status ${experience.status}. Expected DRAFT or IN_REVIEW.",
            )
        }

        // SNA-17: re-validate connection health and capabilities at publish time
        val connection =
            connectionRepository.find(principal.organizationId, experience.connectionId)
                ?: throw ValidationException("Connection ${experience.connectionId} not found")

        if (connection.status != ConnectionStatus.ACTIVE) {
            throw ValidationException("Connection ${experience.connectionId} is not active (status: ${connection.status})")
        }

        if (Capability.STORE_CATEGORY_DEEPLINK !in connection.capabilities) {
            throw ValidationException("Connection does not support STORE_CATEGORY_DEEPLINK")
        }

        experienceRepository.findActiveContractId(principal.organizationId)
            ?: throw ValidationException("No active commercial contract for this organization")

        return experienceRepository.publish(principal.organizationId, principal.userId, id)
    }
}
