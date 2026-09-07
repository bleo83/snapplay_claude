package io.snapplay.contract.infrastructure.persistence

import io.snapplay.contract.application.port.output.ContractRepository
import io.snapplay.contract.application.port.output.CreateContractInput
import io.snapplay.contract.application.port.output.CreateVersionInput
import io.snapplay.contract.domain.BillableEvent
import io.snapplay.contract.domain.CommercialContract
import io.snapplay.contract.domain.ContractStatus
import io.snapplay.contract.domain.ContractVersion
import io.snapplay.contract.domain.ContractVersionStatus
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcContractRepository(
    private val jdbc: JdbcTemplate,
) : ContractRepository {
    override fun createContract(input: CreateContractInput): CommercialContract {
        val id = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO commercial_contracts (id, name, publisher_organization_id, commerce_organization_id, territories, timezone, close_day_of_month)
            VALUES (?, ?, ?, ?, ?::char(2)[], ?, ?)
            """.trimIndent(),
            id,
            input.name,
            input.publisherOrganizationId,
            input.commerceOrganizationId,
            "{${input.territories.joinToString(",")}}",
            input.timezone,
            input.closeDayOfMonth,
        )
        return findContract(id)!!
    }

    override fun findContract(id: UUID): CommercialContract? =
        jdbc.query("SELECT * FROM commercial_contracts WHERE id = ?", { rs, _ -> rs.toContract() }, id).firstOrNull()

    override fun findContractsByOrg(organizationId: UUID): List<CommercialContract> =
        jdbc.query(
            "SELECT * FROM commercial_contracts WHERE publisher_organization_id = ? OR commerce_organization_id = ? ORDER BY created_at DESC",
            { rs, _ -> rs.toContract() },
            organizationId,
            organizationId,
        )

    override fun activateContract(
        id: UUID,
        activatedAt: Instant,
    ) {
        jdbc.update("UPDATE commercial_contracts SET status = 'ACTIVE', activated_at = ? WHERE id = ?", Timestamp.from(activatedAt), id)
    }

    override fun createVersion(input: CreateVersionInput): ContractVersion {
        val id = UUID.randomUUID()
        val nextVersion =
            (
                jdbc.queryForObject(
                    "SELECT COALESCE(MAX(version), 0) + 1 FROM contract_versions WHERE contract_id = ?",
                    Int::class.java,
                    input.contractId,
                )
            )!!
        jdbc.update(
            """
            INSERT INTO contract_versions (id, contract_id, version, currency, effective_from, effective_to, billable_event, refund_window_days, rules, data_sharing_policy_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            """.trimIndent(),
            id, input.contractId, nextVersion, input.currency,
            Timestamp.from(input.effectiveFrom), input.effectiveTo?.let { Timestamp.from(it) },
            input.billableEvent, input.refundWindowDays, input.rules, input.dataSharingPolicyId,
        )
        return findVersion(id)!!
    }

    override fun findVersion(id: UUID): ContractVersion? =
        jdbc.query("SELECT * FROM contract_versions WHERE id = ?", { rs, _ -> rs.toVersion() }, id).firstOrNull()

    override fun findVersionsByContract(contractId: UUID): List<ContractVersion> =
        jdbc.query("SELECT * FROM contract_versions WHERE contract_id = ? ORDER BY version", { rs, _ -> rs.toVersion() }, contractId)

    override fun findEffectiveVersion(
        contractId: UUID,
        asOf: Instant,
    ): ContractVersion? =
        jdbc.query(
            """
            SELECT * FROM contract_versions
            WHERE contract_id = ? AND status = 'APPROVED' AND effective_from <= ? AND (effective_to IS NULL OR effective_to > ?)
            LIMIT 1
            """.trimIndent(),
            { rs, _ -> rs.toVersion() },
            contractId,
            Timestamp.from(asOf),
            Timestamp.from(asOf),
        ).firstOrNull()

    @Suppress("SqlSourceToSinkFlow") // sql built from static literals; all dynamic values use ? params
    override fun hasOverlappingApprovedVersion(contractId: UUID, from: Instant, to: Instant?, excludeVersionId: UUID?): Boolean {
        val params = mutableListOf<Any>(contractId, Timestamp.from(from))
        val toTs = to?.let { Timestamp.from(it) }
        val sql =
            buildString {
                append("SELECT COUNT(*) FROM contract_versions WHERE contract_id = ? AND status = 'APPROVED'")
                append(" AND effective_from < ?") // from < new.to (or unbounded)
                if (toTs != null) {
                    // existing.effective_from < new.to AND (existing.effective_to IS NULL OR existing.effective_to > new.from)
                    params.set(params.size - 1, toTs) // replace: existing.from < new.to
                    append(" AND (effective_to IS NULL OR effective_to > ?)")
                    params.add(Timestamp.from(from))
                }
                if (excludeVersionId != null) {
                    append(" AND id != ?")
                    params.add(excludeVersionId)
                }
            }
        return (jdbc.queryForObject(sql, Int::class.java, *params.toTypedArray()) ?: 0) > 0
    }

    override fun approveVersion(
        id: UUID,
        approvedBy: UUID,
        approvedAt: Instant,
    ) {
        jdbc.update(
            "UPDATE contract_versions SET status = 'APPROVED', approved_at = ?, approved_by = ? WHERE id = ?",
            Timestamp.from(approvedAt),
            approvedBy,
            id,
        )
    }

    private fun ResultSet.toContract() =
        CommercialContract(
            id = UUID.fromString(getString("id")),
            name = getString("name"),
            publisherOrganizationId = UUID.fromString(getString("publisher_organization_id")),
            commerceOrganizationId = UUID.fromString(getString("commerce_organization_id")),
            territories = (getArray("territories")?.array as? Array<*>)?.map { it.toString() } ?: emptyList(),
            timezone = getString("timezone"),
            closeDayOfMonth = getInt("close_day_of_month"),
            status = ContractStatus.valueOf(getString("status")),
            activatedAt = getTimestamp("activated_at")?.toInstant(),
            createdAt = getTimestamp("created_at").toInstant(),
        )

    private fun ResultSet.toVersion() =
        ContractVersion(
            id = UUID.fromString(getString("id")),
            contractId = UUID.fromString(getString("contract_id")),
            version = getInt("version"),
            status = ContractVersionStatus.valueOf(getString("status")),
            currency = getString("currency"),
            effectiveFrom = getTimestamp("effective_from").toInstant(),
            effectiveTo = getTimestamp("effective_to")?.toInstant(),
            billableEvent = BillableEvent.valueOf(getString("billable_event")),
            refundWindowDays = getInt("refund_window_days"),
            rules = getString("rules"),
            dataSharingPolicyId = UUID.fromString(getString("data_sharing_policy_id")),
            approvedAt = getTimestamp("approved_at")?.toInstant(),
            approvedBy = getString("approved_by")?.let { UUID.fromString(it) },
            createdAt = getTimestamp("created_at").toInstant(),
        )
}
