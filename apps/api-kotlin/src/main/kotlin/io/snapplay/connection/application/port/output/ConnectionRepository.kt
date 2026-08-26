package io.snapplay.connection.application.port.output

import io.snapplay.common.PageResult
import io.snapplay.connection.domain.Capability
import io.snapplay.connection.domain.Connection
import io.snapplay.connection.domain.ConnectionStatus
import io.snapplay.connection.domain.Environment
import java.util.UUID

data class CreateConnectionInput(
    val name: String,
    val contentOrganizationId: UUID,
    val commerceOrganizationId: UUID,
    val connectorKey: String,
    val environment: Environment,
    val territories: List<String>,
    val capabilities: Set<Capability>,
    val dataSharingPolicyId: UUID,
)

data class UpdateConnectionInput(
    val territories: List<String>?,
    val capabilities: Set<Capability>?,
    val status: ConnectionStatus?,
)

interface ConnectionRepository {
    fun find(
        organizationId: UUID,
        id: UUID,
    ): Connection?

    fun findAll(
        organizationId: UUID,
        limit: Int,
        cursor: String?,
    ): PageResult<Connection>

    fun create(input: CreateConnectionInput): Connection

    fun update(
        organizationId: UUID,
        id: UUID,
        input: UpdateConnectionInput,
    ): Connection
}
