package io.snapplay.connection.application.port.input

import io.snapplay.connection.domain.Connection
import io.snapplay.connection.domain.Environment
import io.snapplay.identity.RequestPrincipal
import java.util.UUID

data class CreateConnectionCommand(
    val name: String,
    val commerceOrganizationId: UUID,
    val connectorKey: String,
    val environment: Environment,
    val territories: List<String>,
    val capabilities: Map<String, Any>,
    val dataSharingPolicyId: UUID,
)

fun interface CreateConnectionUseCase {
    fun create(
        command: CreateConnectionCommand,
        principal: RequestPrincipal,
    ): Connection
}
