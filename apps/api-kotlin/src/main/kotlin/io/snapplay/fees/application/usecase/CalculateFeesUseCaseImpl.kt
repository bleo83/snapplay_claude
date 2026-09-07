package io.snapplay.fees.application.usecase

import io.snapplay.common.NotFoundException
import io.snapplay.fees.application.port.input.CalculateFeesUseCase
import io.snapplay.fees.application.port.output.FeeRuleResolver
import io.snapplay.fees.domain.FeeEngine
import io.snapplay.fees.domain.FeeInput
import io.snapplay.fees.domain.FeeResult
import io.snapplay.ledger.application.port.input.RecordEarnUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CalculateFeesUseCaseImpl(
    private val ruleResolver: FeeRuleResolver,
    private val recordEarn: RecordEarnUseCase,
) : CalculateFeesUseCase {
    private val log = LoggerFactory.getLogger(CalculateFeesUseCaseImpl::class.java)

    override fun calculate(input: FeeInput): FeeResult {
        val rules = ruleResolver.resolveRules(input.contractVersionId)
        if (rules.isEmpty()) {
            throw NotFoundException("No fee rules found for contract_version ${input.contractVersionId}")
        }
        return FeeEngine.calculate(input, rules)
    }

    override fun calculateAndRecord(input: FeeInput): FeeResult {
        val result = calculate(input)

        for (calc in result.calculations) {
            runCatching {
                recordEarn.recordEarn(
                    providerOrderId = input.providerOrderId,
                    handoffSessionId = input.handoffSessionId,
                    contractVersionId = input.contractVersionId,
                    ruleKey = calc.ruleKey,
                    causativeEvent = input.causativeEvent,
                    partyId = calc.partyId,
                    counterpartyId = calc.counterpartyId,
                    baseAmountMinor = calc.baseAmountMinor,
                    amountMinor = calc.amountMinor,
                    currency = calc.currency,
                )
            }.onFailure {
                log.warn("Failed to record ledger entry for rule={} order={}", calc.ruleKey, input.providerOrderId, it)
            }
        }

        log.info(
            "Calculated {} fees for order={} total={}",
            result.calculations.size,
            input.providerOrderId,
            result.totalFeesMinor,
        )
        return result
    }
}
