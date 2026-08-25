package io.snapplay.connection.application.usecase

import io.snapplay.common.NotFoundException
import io.snapplay.connection.application.port.input.UpdateConnectionCommand
import io.snapplay.connection.application.port.input.UpdateConnectionUseCase
import io.snapplay.connection.application.port.output.ConnectionRepository
import io.snapplay.connection.application.port.output.UpdateConnectionInput
import io.snapplay.connection.domain.Connection
import io.snapplay.identity.OrgRole
import io.snapplay.identity.RequestPrincipal
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UpdateConnectionUseCaseImpl(
    private val connectionRepository: ConnectionRepository,
) : UpdateConnectionUseCase {
    override fun update(
        command: UpdateConnectionCommand,
        principal: RequestPrincipal,
        id: UUID,
    ): Connection {
        principal.requireAnyRole(OrgRole.ORGANIZATION_ADMIN)

        connectionRepository.find(principal.organizationId, id)
            ?: throw NotFoundException("Connection $id not found")

        return connectionRepository.update(
            principal.organizationId,
            id,
            UpdateConnectionInput(
                territories = command.territories,
                capabilities = command.capabilities,
                status = command.status,
            ),
        )
    }
}
