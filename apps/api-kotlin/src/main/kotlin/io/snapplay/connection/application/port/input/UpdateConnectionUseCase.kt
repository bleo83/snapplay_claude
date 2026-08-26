package io.snapplay.connection.application.port.input

import io.snapplay.connection.domain.Capability
import io.snapplay.connection.domain.Connection
import io.snapplay.connection.domain.ConnectionStatus
import io.snapplay.identity.RequestPrincipal
import java.util.UUID

data class UpdateConnectionCommand(
    val territories: List<String>?,
    val capabilities: Set<Capability>?,
    val status: ConnectionStatus?,
)

fun interface UpdateConnectionUseCase {
    fun update(
        command: UpdateConnectionCommand,
        principal: RequestPrincipal,
        id: UUID,
    ): Connection
}
