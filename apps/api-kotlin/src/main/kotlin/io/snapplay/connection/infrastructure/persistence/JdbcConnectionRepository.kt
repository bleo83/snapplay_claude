package io.snapplay.connection.infrastructure.persistence

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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
import org.postgresql.util.PGobject
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcConnectionRepository(
    private val jdbc: JdbcTemplate,
) : ConnectionRepository {
    private val objectMapper = jacksonObjectMapper()

    private data class ConnectionRow(val connection: Connection, val createdAt: Instant)

    private fun mapRow(rs: ResultSet): ConnectionRow {
        @Suppress("UNCHECKED_CAST")
        val territories =
            (rs.getArray("territories")?.array as? Array<*>)
                ?.map { it.toString().trim() }
                ?: emptyList()

        val capabilitiesJson = rs.getString("capabilities") ?: "{}"
        val capabilities: Map<String, Any> = objectMapper.readValue(capabilitiesJson)

        return ConnectionRow(
            connection =
                Connection(
                    id = UUID.fromString(rs.getString("id")),
                    name = rs.getString("name"),
                    contentOrganizationId = UUID.fromString(rs.getString("content_organization_id")),
                    commerceOrganizationId = UUID.fromString(rs.getString("commerce_organization_id")),
                    connectorKey = rs.getString("connector_key"),
                    environment = Environment.valueOf(rs.getString("environment")),
                    status = ConnectionStatus.valueOf(rs.getString("status")),
                    territories = territories,
                    capabilities = capabilities,
                    dataSharingPolicyId = UUID.fromString(rs.getString("data_sharing_policy_id")),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                ),
            createdAt = rs.getTimestamp("created_at").toInstant(),
        )
    }

    override fun find(
        organizationId: UUID,
        id: UUID,
    ): Connection? =
        jdbc.query(
            """
            SELECT id, name, content_organization_id, commerce_organization_id,
                   connector_key, environment, status, territories, capabilities,
                   data_sharing_policy_id, created_at
            FROM connections
            WHERE id = ?
              AND (content_organization_id = ? OR commerce_organization_id = ?)
            """.trimIndent(),
            { rs, _ -> mapRow(rs) },
            id,
            organizationId,
            organizationId,
        ).firstOrNull()?.connection

    override fun findAll(
        organizationId: UUID,
        limit: Int,
        cursor: String?,
    ): PageResult<Connection> {
        val decoded = cursor?.let { Cursor.decode(it) }

        // sql is built exclusively from static string literals; all dynamic values use ? params — no injection risk
        @Suppress("SqlSourceToSinkFlow")
        val sql =
            buildString {
                append(
                    """
                    SELECT id, name, content_organization_id, commerce_organization_id,
                           connector_key, environment, status, territories, capabilities,
                           data_sharing_policy_id, created_at
                    FROM connections
                    WHERE (content_organization_id = ? OR commerce_organization_id = ?)
                    """.trimIndent(),
                )
                if (decoded != null) {
                    append("\nAND (created_at < ? OR (created_at = ? AND id < ?::uuid))")
                }
                append("\nORDER BY created_at DESC, id DESC")
                append("\nLIMIT ?")
            }

        val params =
            buildList {
                add(organizationId)
                add(organizationId)
                if (decoded != null) {
                    add(Timestamp.from(decoded.first))
                    add(Timestamp.from(decoded.first))
                    add(decoded.second.toString())
                }
                add(limit + 1)
            }

        return jdbc
            .query(sql, { rs, _ -> mapRow(rs) }, *params.toTypedArray())
            .toPageResult(limit, { it.createdAt }, { it.connection.id }, { it.connection })
    }

    override fun create(input: CreateConnectionInput): Connection {
        val id = UUID.randomUUID()

        val territoriesArray =
            jdbc.dataSource!!.connection.use { conn ->
                conn.createArrayOf("char", input.territories.toTypedArray())
            }

        val capabilitiesPg =
            PGobject().apply {
                type = "jsonb"
                value = objectMapper.writeValueAsString(input.capabilities)
            }

        jdbc.update(
            """
            INSERT INTO connections
                (id, name, content_organization_id, commerce_organization_id,
                 connector_key, environment, status, territories, capabilities, data_sharing_policy_id)
            VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?)
            """.trimIndent(),
            id,
            input.name,
            input.contentOrganizationId,
            input.commerceOrganizationId,
            input.connectorKey,
            input.environment.name,
            territoriesArray,
            capabilitiesPg,
            input.dataSharingPolicyId,
        )

        return find(input.contentOrganizationId, id)!!
    }

    override fun update(
        organizationId: UUID,
        id: UUID,
        input: UpdateConnectionInput,
    ): Connection {
        // sql is built exclusively from static string literals; all dynamic values use ? params — no injection risk
        @Suppress("SqlSourceToSinkFlow")
        val sql =
            buildString {
                append("UPDATE connections SET updated_at = NOW()")
                if (input.territories != null) append(", territories = ?")
                if (input.capabilities != null) append(", capabilities = ?")
                if (input.status != null) append(", status = ?")
                append("\nWHERE id = ?")
                append("\n  AND (content_organization_id = ? OR commerce_organization_id = ?)")
            }

        val params =
            buildList {
                if (input.territories != null) {
                    add(
                        jdbc.dataSource!!.connection.use { conn ->
                            conn.createArrayOf("char", input.territories.toTypedArray())
                        },
                    )
                }
                if (input.capabilities != null) {
                    add(
                        PGobject().apply {
                            type = "jsonb"
                            value = objectMapper.writeValueAsString(input.capabilities)
                        },
                    )
                }
                if (input.status != null) add(input.status.name)
                add(id)
                add(organizationId)
                add(organizationId)
            }

        val updated = jdbc.update(sql, *params.toTypedArray())
        if (updated == 0) throw NotFoundException("Connection $id not found")
        return find(organizationId, id)!!
    }
}
