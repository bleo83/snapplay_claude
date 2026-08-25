package io.snapplay.connection.application.usecase

import io.snapplay.common.ValidationException
import io.snapplay.connection.application.port.input.CreateConnectionCommand
import io.snapplay.connection.application.port.input.CreateConnectionUseCase
import io.snapplay.connection.application.port.output.ConnectionRepository
import io.snapplay.connection.application.port.output.CreateConnectionInput
import io.snapplay.connection.domain.Connection
import io.snapplay.identity.OrgRole
import io.snapplay.identity.RequestPrincipal
import org.springframework.stereotype.Service

@Service
class CreateConnectionUseCaseImpl(
    private val connectionRepository: ConnectionRepository,
) : CreateConnectionUseCase {
    override fun create(
        command: CreateConnectionCommand,
        principal: RequestPrincipal,
    ): Connection {
        principal.requireAnyRole(OrgRole.ORGANIZATION_ADMIN)

        if (command.commerceOrganizationId == principal.organizationId) {
            throw ValidationException("commerceOrganizationId must differ from the caller's organization")
        }

        return connectionRepository.create(
            CreateConnectionInput(
                name = command.name,
                contentOrganizationId = principal.organizationId,
                commerceOrganizationId = command.commerceOrganizationId,
                connectorKey = command.connectorKey,
                environment = command.environment,
                territories = command.territories,
                capabilities = command.capabilities,
                dataSharingPolicyId = command.dataSharingPolicyId,
            ),
        )
    }
}
