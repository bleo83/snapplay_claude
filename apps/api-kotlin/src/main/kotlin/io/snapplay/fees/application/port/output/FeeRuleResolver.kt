package io.snapplay.fees.application.port.output

import io.snapplay.fees.domain.FeeRule
import java.util.UUID

interface FeeRuleResolver {
    /** Parses fee rules from the contract version's rules JSONB. */
    fun resolveRules(contractVersionId: UUID): List<FeeRule>
}
