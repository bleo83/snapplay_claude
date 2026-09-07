package io.snapplay.identity

import io.snapplay.common.ForbiddenException
import io.snapplay.common.NotFoundException
import java.util.UUID

enum class OrgRole {
    ORGANIZATION_ADMIN,
    CONTENT_MANAGER,
    PUBLISHER,
    CATALOG_VIEWER,
    ANALYST,
    FINANCE,
    AUDITOR,
}

enum class PrincipalType { USER, SERVICE_ACCOUNT }

data class RequestPrincipal(
    val userId: UUID,
    val organizationId: UUID,
    val roles: Set<OrgRole>,
    val principalType: PrincipalType = PrincipalType.USER,
    /** Non-null for connection-scoped service accounts. */
    val connectionId: UUID? = null,
    val scopes: Set<String> = emptySet(),
) {
    fun requireAnyRole(vararg allowed: OrgRole) {
        if (allowed.none { it in roles }) {
            throw ForbiddenException("Required role: ${allowed.joinToString()}")
        }
    }

    fun hasRole(role: OrgRole): Boolean = role in roles

    fun requireScope(scope: String) {
        if (scopes.isNotEmpty() && scope !in scopes) {
            throw ForbiddenException("Required scope: $scope")
        }
    }

    /**
     * Ensures this principal can access [targetConnectionId].
     * Connection-scoped service accounts are restricted to their own connection.
     * Throws [NotFoundException] (not 403) to avoid leaking resource existence cross-tenant.
     */
    fun requireConnectionAccess(targetConnectionId: UUID) {
        if (connectionId != null && connectionId != targetConnectionId) {
            throw NotFoundException("Resource not found")
        }
    }

    /** Returns true if the principal is read-only (Analyst, Auditor, Catalog Viewer). */
    fun isReadOnly(): Boolean = roles.all { it in setOf(OrgRole.ANALYST, OrgRole.AUDITOR, OrgRole.CATALOG_VIEWER) }
}
