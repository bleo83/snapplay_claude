package io.snapplay.identity

import io.snapplay.common.ForbiddenException
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

data class RequestPrincipal(
    val userId: UUID,
    val organizationId: UUID,
    val roles: Set<OrgRole>,
) {
    fun requireAnyRole(vararg allowed: OrgRole) {
        if (allowed.none { it in roles }) {
            throw ForbiddenException("Required role: ${allowed.joinToString()}")
        }
    }

    fun hasRole(role: OrgRole): Boolean = role in roles
}
