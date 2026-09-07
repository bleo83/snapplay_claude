package io.snapplay.fees.infrastructure.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.snapplay.fees.application.port.output.FeeRuleResolver
import io.snapplay.fees.domain.FeeBase
import io.snapplay.fees.domain.FeeRule
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcFeeRuleResolver(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) : FeeRuleResolver {
    override fun resolveRules(contractVersionId: UUID): List<FeeRule> {
        val rulesJson =
            jdbc.query(
                "SELECT rules FROM contract_versions WHERE id = ?",
                { rs, _ -> rs.getString("rules") },
                contractVersionId,
            ).firstOrNull() ?: return emptyList()

        return parseRules(rulesJson)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseRules(rulesJson: String): List<FeeRule> {
        val root: Map<String, Any?> = objectMapper.readValue(rulesJson)
        val fees = (root["fees"] as? List<Map<String, Any?>>) ?: return emptyList()
        return fees.map { raw ->
            FeeRule(
                ruleKey = raw["rule_key"] as? String ?: "",
                base = FeeBase.valueOf(raw["base"] as? String ?: "ORDER_TOTAL"),
                rateBasisPoints = (raw["rate_basis_points"] as? Number)?.toLong() ?: 0L,
                fixedAmountMinor = (raw["fixed_amount_minor"] as? Number)?.toLong(),
                partyId = UUID.fromString(raw["party_id"] as String),
                counterpartyId = UUID.fromString(raw["counterparty_id"] as String),
                description = raw["description"] as? String ?: "",
            )
        }
    }
}
