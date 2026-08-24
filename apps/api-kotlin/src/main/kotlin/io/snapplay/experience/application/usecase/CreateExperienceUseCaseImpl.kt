package io.snapplay.experience.application.usecase

import io.snapplay.common.ValidationException
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

        val context =
            experienceRepository.findContentContext(principal.organizationId, command.contextTitle)
                ?: throw ValidationException("Unknown content context: '${command.contextTitle}'")

        val connectionId =
            experienceRepository.findActiveConnectionId(principal.organizationId)
                ?: throw ValidationException("No active commerce connection for this organization")

        val contractId =
            experienceRepository.findActiveContractId(principal.organizationId)
                ?: throw ValidationException("No active commercial contract for this organization")

        val productIds =
            if (command.productCount > 0) {
                experienceRepository.findActiveProductIds(connectionId, command.productCount)
            } else {
                emptyList()
            }

        return experienceRepository.create(
            principal.organizationId,
            principal.userId,
            CreateExperienceInput(
                name = command.name,
                contextId = context.id,
                contextTitle = command.contextTitle,
                channelDisplayName = context.channelDisplayName,
                connectionId = connectionId,
                contractId = contractId,
                productIds = productIds,
                handoffMode = command.handoffMode,
                startsAt = command.startsAt,
                endsAt = command.endsAt,
            ),
        )
    }
}
