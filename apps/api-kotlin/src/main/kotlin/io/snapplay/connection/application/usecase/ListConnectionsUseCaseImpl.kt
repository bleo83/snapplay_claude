package io.snapplay.connection.application.usecase

import io.snapplay.common.PageResult
import io.snapplay.connection.application.port.input.ListConnectionsUseCase
import io.snapplay.connection.application.port.output.ConnectionRepository
import io.snapplay.connection.domain.Connection
import io.snapplay.identity.RequestPrincipal
import org.springframework.stereotype.Service

@Service
class ListConnectionsUseCaseImpl(
    private val connectionRepository: ConnectionRepository,
) : ListConnectionsUseCase {
    override fun list(
        principal: RequestPrincipal,
        limit: Int,
        cursor: String?,
    ): PageResult<Connection> = connectionRepository.findAll(principal.organizationId, limit, cursor)
}
