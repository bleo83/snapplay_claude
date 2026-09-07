package io.snapplay.fees.infrastructure.persistence

import io.snapplay.fees.application.port.output.FeeRuleResolver
import io.snapplay.fees.domain.FeeRule
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoFeeRuleResolver : FeeRuleResolver {
    private val rulesByVersion = ConcurrentHashMap<UUID, List<FeeRule>>()

    fun clear() = rulesByVersion.clear()

    fun setRules(
        contractVersionId: UUID,
        rules: List<FeeRule>,
    ) {
        rulesByVersion[contractVersionId] = rules
    }

    override fun resolveRules(contractVersionId: UUID): List<FeeRule> = rulesByVersion[contractVersionId] ?: emptyList()
}
