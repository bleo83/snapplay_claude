package io.snapplay.identity

import io.snapplay.common.ForbiddenException
import io.snapplay.common.NotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.UUID

class RbacTest {
    private val orgId = UUID.randomUUID()
    private val connectionA = UUID.randomUUID()
    private val connectionB = UUID.randomUUID()

    private fun principal(
        vararg roles: OrgRole,
        connectionId: UUID? = null,
        scopes: Set<String> = emptySet(),
        type: PrincipalType = PrincipalType.USER,
    ) = RequestPrincipal(
        userId = UUID.randomUUID(),
        organizationId = orgId,
        roles = roles.toSet(),
        principalType = type,
        connectionId = connectionId,
        scopes = scopes,
    )

    // --- Role checks ---

    @Test
    fun `ORGANIZATION_ADMIN can publish`() {
        val p = principal(OrgRole.ORGANIZATION_ADMIN)
        p.requireAnyRole(OrgRole.ORGANIZATION_ADMIN, OrgRole.PUBLISHER)
        // no exception
    }

    @Test
    fun `PUBLISHER can publish`() {
        val p = principal(OrgRole.PUBLISHER)
        p.requireAnyRole(OrgRole.ORGANIZATION_ADMIN, OrgRole.PUBLISHER)
    }

    @Test
    fun `CONTENT_MANAGER cannot publish`() {
        val p = principal(OrgRole.CONTENT_MANAGER)
        assertThatThrownBy { p.requireAnyRole(OrgRole.ORGANIZATION_ADMIN, OrgRole.PUBLISHER) }
            .isInstanceOf(ForbiddenException::class.java)
    }

    @Test
    fun `AUDITOR is read-only`() {
        val p = principal(OrgRole.AUDITOR)
        assertThat(p.isReadOnly()).isTrue()
    }

    @Test
    fun `ANALYST is read-only`() {
        val p = principal(OrgRole.ANALYST)
        assertThat(p.isReadOnly()).isTrue()
    }

    @Test
    fun `FINANCE is not read-only`() {
        val p = principal(OrgRole.FINANCE)
        assertThat(p.isReadOnly()).isFalse()
    }

    @ParameterizedTest
    @EnumSource(OrgRole::class, names = ["ORGANIZATION_ADMIN", "PUBLISHER", "CONTENT_MANAGER", "FINANCE"])
    fun `mutation roles are not read-only`(role: OrgRole) {
        assertThat(principal(role).isReadOnly()).isFalse()
    }

    @ParameterizedTest
    @EnumSource(OrgRole::class, names = ["ANALYST", "AUDITOR", "CATALOG_VIEWER"])
    fun `viewer roles are read-only`(role: OrgRole) {
        assertThat(principal(role).isReadOnly()).isTrue()
    }

    // --- Scope enforcement ---

    @Test
    fun `scope enforcement passes when scope is present`() {
        val p = principal(OrgRole.ANALYST, scopes = setOf("orders:read", "analytics:read"))
        p.requireScope("orders:read")
        // no exception
    }

    @Test
    fun `scope enforcement fails when scope is missing`() {
        val p = principal(OrgRole.ANALYST, scopes = setOf("analytics:read"))
        assertThatThrownBy { p.requireScope("orders:write") }
            .isInstanceOf(ForbiddenException::class.java)
    }

    @Test
    fun `empty scopes set means unrestricted (user tokens)`() {
        val p = principal(OrgRole.ORGANIZATION_ADMIN, scopes = emptySet())
        p.requireScope("anything")
        // no exception — empty means all scopes allowed
    }

    // --- Connection-scoped access ---

    @Test
    fun `connection-scoped principal can access own connection`() {
        val p = principal(OrgRole.ANALYST, connectionId = connectionA)
        p.requireConnectionAccess(connectionA)
        // no exception
    }

    @Test
    fun `connection-scoped principal cannot access other connection — returns 404 not 403`() {
        val p = principal(OrgRole.ANALYST, connectionId = connectionA)
        assertThatThrownBy { p.requireConnectionAccess(connectionB) }
            .isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `org-wide principal (no connectionId) can access any connection`() {
        val p = principal(OrgRole.ORGANIZATION_ADMIN, connectionId = null)
        p.requireConnectionAccess(connectionA)
        p.requireConnectionAccess(connectionB)
        // no exception
    }

    // --- Service account type ---

    @Test
    fun `service account has SERVICE_ACCOUNT type`() {
        val p = principal(OrgRole.ANALYST, type = PrincipalType.SERVICE_ACCOUNT, connectionId = connectionA)
        assertThat(p.principalType).isEqualTo(PrincipalType.SERVICE_ACCOUNT)
        assertThat(p.connectionId).isEqualTo(connectionA)
    }
}
