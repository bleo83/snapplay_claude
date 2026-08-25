package io.snapplay.connection.application.usecase

import io.snapplay.common.NotFoundException
import io.snapplay.connection.application.port.input.GetConnectionUseCase
import io.snapplay.connection.application.port.output.ConnectionRepository
import io.snapplay.connection.domain.Connection
import io.snapplay.identity.RequestPrincipal
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetConnectionUseCaseImpl(
    private val connectionRepository: ConnectionRepository,
) : GetConnectionUseCase {
    override fun get(
        principal: RequestPrincipal,
        id: UUID,
    ): Connection =
        connectionRepository.find(principal.organizationId, id)
            ?: throw NotFoundException("Connection $id not found")
}
