package io.snapplay.identity

import io.snapplay.common.ForbiddenException
import io.snapplay.common.UnauthorizedException
import io.snapplay.config.SnapPlayProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import java.util.UUID

private val DEMO_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001")
private val DEMO_ORG_ID = UUID.fromString("50d2d7eb-c8fd-42df-bbb0-0ea0e2271090")

@Component
class PrincipalResolver(
    private val props: SnapPlayProperties,
) {
    private val log = LoggerFactory.getLogger(PrincipalResolver::class.java)

    @Autowired(required = false)
    private var jdbcTemplate: JdbcTemplate? = null

    fun resolve(): RequestPrincipal {
        if (props.demo) {
            return RequestPrincipal(
                userId = DEMO_USER_ID,
                organizationId = DEMO_ORG_ID,
                roles = setOf(OrgRole.ORGANIZATION_ADMIN),
            )
        }
        return resolveFromJwt()
    }

    private fun resolveFromJwt(): RequestPrincipal {
        val authentication =
            SecurityContextHolder.getContext().authentication
                ?: throw UnauthorizedException("No authentication context found")

        val jwt =
            authentication.principal as? Jwt
                ?: throw UnauthorizedException("Expected JWT principal")

        // Service accounts use client_credentials grant → have client_id claim
        val clientId = jwt.getClaimAsString("client_id")
        if (clientId != null) {
            return resolveServiceAccount(clientId)
        }

        return resolveUser(jwt)
    }

    private fun resolveServiceAccount(clientId: String): RequestPrincipal {
        val template = jdbcTemplate ?: throw UnauthorizedException("Database not available")

        data class SaRow(val id: UUID, val orgId: UUID, val connectionId: UUID?, val scopes: Set<String>)

        val rows =
            template.query(
                "SELECT id, organization_id, connection_id, scopes FROM service_accounts WHERE client_id = ? AND status = 'ACTIVE' LIMIT 1",
                { rs, _ ->
                    SaRow(
                        id = UUID.fromString(rs.getString("id")),
                        orgId = UUID.fromString(rs.getString("organization_id")),
                        connectionId = rs.getString("connection_id")?.let { UUID.fromString(it) },
                        scopes = (rs.getArray("scopes")?.array as? Array<*>)?.map { it.toString() }?.toSet() ?: emptySet(),
                    )
                },
                clientId,
            )

        if (rows.isEmpty()) {
            log.warn("Service account not found or revoked: client_id={}", clientId)
            throw UnauthorizedException("Invalid or revoked service account")
        }

        val sa = rows.first()
        return RequestPrincipal(
            userId = sa.id,
            organizationId = sa.orgId,
            roles = setOf(OrgRole.ANALYST),
            principalType = PrincipalType.SERVICE_ACCOUNT,
            connectionId = sa.connectionId,
            scopes = sa.scopes,
        )
    }

    private fun resolveUser(jwt: Jwt): RequestPrincipal {
        val userId =
            try {
                UUID.fromString(jwt.subject)
            } catch (e: IllegalArgumentException) {
                log.error("Could not parse JWT token", e)
                throw UnauthorizedException("Invalid user ID in JWT subject: ${jwt.subject}")
            }

        val template = jdbcTemplate ?: throw UnauthorizedException("Database not available")

        val memberships =
            template.query(
                """
                SELECT organization_id, role_key
                FROM organization_members
                WHERE user_id = ?
                  AND status = 'ACTIVE'
                ORDER BY created_at ASC
                """.trimIndent(),
                { rs, _ ->
                    Pair(
                        UUID.fromString(rs.getString("organization_id")),
                        rs.getString("role_key"),
                    )
                },
                userId,
            )

        if (memberships.isEmpty()) {
            log.warn("No active organization membership found for user {}", userId)
            throw ForbiddenException("No active organization membership found")
        }

        val organizationId = memberships.first().first
        val roles =
            memberships
                .filter { it.first == organizationId }
                .mapNotNull { (_, roleKey) ->
                    runCatching { OrgRole.valueOf(roleKey) }.getOrNull()
                }.toSet()

        return RequestPrincipal(
            userId = userId,
            organizationId = organizationId,
            roles = roles,
        )
    }
}
