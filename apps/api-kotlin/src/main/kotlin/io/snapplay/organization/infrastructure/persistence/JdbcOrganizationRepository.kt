package io.snapplay.organization.infrastructure.persistence

import io.snapplay.common.NotFoundException
import io.snapplay.organization.application.port.output.OrganizationRepository
import io.snapplay.organization.application.port.output.UpdateOrganizationInput
import io.snapplay.organization.domain.OrganizationProfile
import io.snapplay.organization.domain.OrganizationStatus
import io.snapplay.organization.domain.OrganizationType
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcOrganizationRepository(
    private val jdbc: JdbcTemplate,
) : OrganizationRepository {
    private val rowMapper =
        RowMapper { rs: ResultSet, _ ->
            OrganizationProfile(
                id = UUID.fromString(rs.getString("id")),
                legalName = rs.getString("legal_name"),
                displayName = rs.getString("display_name"),
                organizationType = OrganizationType.valueOf(rs.getString("organization_type")),
                country = rs.getString("country"),
                defaultCurrency = rs.getString("default_currency"),
                timezone = rs.getString("timezone"),
                status = OrganizationStatus.valueOf(rs.getString("status")),
            )
        }

    override fun findById(organizationId: UUID): OrganizationProfile? =
        jdbc
            .query(
                """
                SELECT id, legal_name, display_name, organization_type,
                       country, default_currency, timezone, status
                FROM organizations
                WHERE id = ?
                """.trimIndent(),
                rowMapper,
                organizationId,
            ).firstOrNull()

    override fun update(
        organizationId: UUID,
        input: UpdateOrganizationInput,
    ): OrganizationProfile {
        val updated =
            jdbc.update(
                """
                UPDATE organizations
                SET legal_name        = ?,
                    display_name      = ?,
                    country           = ?,
                    default_currency  = ?,
                    timezone          = ?,
                    updated_at        = NOW()
                WHERE id = ?
                """.trimIndent(),
                input.legalName,
                input.displayName,
                input.country,
                input.defaultCurrency,
                input.timezone,
                organizationId,
            )
        if (updated == 0) throw NotFoundException("Organization $organizationId not found")
        return findById(organizationId)!!
    }
}
