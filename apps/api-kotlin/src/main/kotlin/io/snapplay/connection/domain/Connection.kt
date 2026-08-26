package io.snapplay.connection.domain

import java.time.Instant
import java.util.UUID

enum class ConnectionStatus {
    PENDING,
    ACTIVE,
    DEGRADED,
    SUSPENDED,
    CLOSED,
}

enum class Environment {
    SANDBOX,
    PRODUCTION,
}

data class Connection(
    val id: UUID,
    val name: String,
    val contentOrganizationId: UUID,
    val commerceOrganizationId: UUID,
    val connectorKey: String,
    val environment: Environment,
    val status: ConnectionStatus,
    val territories: List<String>,
    val capabilities: Set<Capability>,
    val dataSharingPolicyId: UUID,
    val createdAt: Instant,
)
