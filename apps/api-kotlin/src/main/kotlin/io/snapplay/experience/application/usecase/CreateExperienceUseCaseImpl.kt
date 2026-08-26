package io.snapplay.experience.application.usecase

import io.snapplay.common.ValidationException
import io.snapplay.connection.application.port.output.ConnectionRepository
import io.snapplay.connection.domain.Capability
import io.snapplay.connection.domain.ConnectionStatus
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
    private val connectionRepository: ConnectionRepository,
) : CreateExperienceUseCase {
    override fun create(
        command: CreateExperienceCommand,
        principal: RequestPrincipal,
    ): Experience {
        principal.requireAnyRole(OrgRole.ORGANIZATION_ADMIN, OrgRole.CONTENT_MANAGER, OrgRole.PUBLISHER)

        val connection =
            connectionRepository.find(principal.organizationId, command.connectionId)
                ?: throw ValidationException("Connection ${command.connectionId} not found")

        if (connection.status != ConnectionStatus.ACTIVE) {
            throw ValidationException("Connection ${command.connectionId} is not active (status: ${connection.status})")
        }

        if (Capability.STORE_CATEGORY_DEEPLINK !in connection.capabilities) {
            throw ValidationException("Connection does not support STORE_CATEGORY_DEEPLINK")
        }

        if (command.territory !in connection.territories) {
            throw ValidationException(
                "Territory '${command.territory}' is not covered by connection '${command.connectionId}'. " +
                    "Covered territories: ${connection.territories.joinToString()}",
            )
        }

        val context =
            experienceRepository.findContentContext(principal.organizationId, command.contextTitle)
                ?: throw ValidationException("Unknown content context: '${command.contextTitle}'")

        val contractId =
            experienceRepository.findActiveContractId(principal.organizationId)
                ?: throw ValidationException("No active commercial contract for this organization")

        val productIds =
            if (command.productCount > 0) {
                experienceRepository.findActiveProductIds(command.connectionId, command.productCount)
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
                connectionId = command.connectionId,
                contractId = contractId,
                productIds = productIds,
                handoffMode = command.handoffMode,
                territory = command.territory,
                destination = command.destination,
                startsAt = command.startsAt,
                endsAt = command.endsAt,
            ),
        )
    }
}
