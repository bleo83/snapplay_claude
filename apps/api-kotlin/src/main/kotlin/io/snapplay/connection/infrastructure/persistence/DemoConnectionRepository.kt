package io.snapplay.connection.infrastructure.persistence

import io.snapplay.common.Cursor
import io.snapplay.common.NotFoundException
import io.snapplay.common.PageResult
import io.snapplay.common.toPageResult
import io.snapplay.connection.application.port.output.ConnectionRepository
import io.snapplay.connection.application.port.output.CreateConnectionInput
import io.snapplay.connection.application.port.output.UpdateConnectionInput
import io.snapplay.connection.domain.Connection
import io.snapplay.connection.domain.ConnectionStatus
import io.snapplay.connection.domain.Environment
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

private val DEMO_CONNECTION_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
private val DEMO_CONTENT_ORG_ID = UUID.fromString("50d2d7eb-c8fd-42df-bbb0-0ea0e2271090")
private val DEMO_COMMERCE_ORG_ID = UUID.fromString("11111111-1111-1111-1111-111111111111")
private val DEMO_POLICY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222")

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoConnectionRepository : ConnectionRepository {
    private val connections =
        mutableListOf(
            Connection(
                id = DEMO_CONNECTION_ID,
                name = "Disney Argentina ↔ Rappi SANDBOX",
                contentOrganizationId = DEMO_CONTENT_ORG_ID,
                commerceOrganizationId = DEMO_COMMERCE_ORG_ID,
                connectorKey = "rappi",
                environment = Environment.SANDBOX,
                status = ConnectionStatus.ACTIVE,
                territories = listOf("AR"),
                capabilities = mapOf("catalog_sync" to true, "order_api" to true),
                dataSharingPolicyId = DEMO_POLICY_ID,
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            ),
        )

    override fun find(
        organizationId: UUID,
        id: UUID,
    ): Connection? = connections.firstOrNull { it.id == id && (it.contentOrganizationId == organizationId || it.commerceOrganizationId == organizationId) }

    override fun findAll(
        organizationId: UUID,
        limit: Int,
        cursor: String?,
    ): PageResult<Connection> {
        val decoded = cursor?.let { Cursor.decode(it) }
        val filtered =
            connections
                .filter { it.contentOrganizationId == organizationId || it.commerceOrganizationId == organizationId }
                .sortedWith(compareByDescending<Connection> { it.createdAt }.thenByDescending { it.id })
                .let { list ->
                    if (decoded != null) {
                        list.dropWhile { it.createdAt > decoded.first || (it.createdAt == decoded.first && it.id >= decoded.second) }
                    } else {
                        list
                    }
                }
        return filtered.toPageResult(limit, { it.createdAt }, { it.id }, { it })
    }

    override fun create(input: CreateConnectionInput): Connection {
        val connection =
            Connection(
                id = UUID.randomUUID(),
                name = input.name,
                contentOrganizationId = input.contentOrganizationId,
                commerceOrganizationId = input.commerceOrganizationId,
                connectorKey = input.connectorKey,
                environment = input.environment,
                status = ConnectionStatus.PENDING,
                territories = input.territories,
                capabilities = input.capabilities,
                dataSharingPolicyId = input.dataSharingPolicyId,
                createdAt = Instant.now(),
            )
        connections.add(connection)
        return connection
    }

    override fun update(
        organizationId: UUID,
        id: UUID,
        input: UpdateConnectionInput,
    ): Connection {
        val index =
            connections.indexOfFirst {
                it.id == id && (it.contentOrganizationId == organizationId || it.commerceOrganizationId == organizationId)
            }
        if (index == -1) throw NotFoundException("Connection $id not found")

        val existing = connections[index]
        val updated =
            existing.copy(
                territories = input.territories ?: existing.territories,
                capabilities = input.capabilities ?: existing.capabilities,
                status = input.status ?: existing.status,
            )
        connections[index] = updated
        return updated
    }
}
