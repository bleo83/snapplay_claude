package io.snapplay.fees.domain

import java.util.UUID

enum class FeeBase {
    /** Fee = rate applied to order_total_minor */
    ORDER_TOTAL,

    /** Fee = rate applied to eligible_item_value_minor */
    ELIGIBLE_ITEM_VALUE,

    /** Fee = fixed amount per converted order, ignoring rate */
    FIXED_PER_CONVERTED_ORDER,
}

data class FeeRule(
    val ruleKey: String,
    val base: FeeBase,
    /** Rate in basis points: 1000 bp = 10%. Ignored for FIXED_PER_CONVERTED_ORDER. */
    val rateBasisPoints: Long,
    /** Fixed fee in minor units. Only used when base = FIXED_PER_CONVERTED_ORDER. */
    val fixedAmountMinor: Long?,
    val partyId: UUID,
    val counterpartyId: UUID,
    val description: String,
)

data class FeeInput(
    val providerOrderId: UUID,
    val handoffSessionId: UUID?,
    val contractVersionId: UUID,
    val orderTotalMinor: Long,
    val eligibleItemValueMinor: Long?,
    val currency: String,
    val causativeEvent: String,
)

data class FeeCalculation(
    val ruleKey: String,
    val base: FeeBase,
    val baseAmountMinor: Long,
    val rateBasisPoints: Long,
    val amountMinor: Long,
    val partyId: UUID,
    val counterpartyId: UUID,
    val currency: String,
    val description: String,
)

data class FeeResult(
    val input: FeeInput,
    val calculations: List<FeeCalculation>,
    /** Sum of all fee amounts. */
    val totalFeesMinor: Long,
)

/**
 * Pure, deterministic fee calculator. No framework dependencies — all inputs and outputs are value objects.
 *
 * Rounding policy: half-up (banker's rounding) via `(base * bp + 5000) / 10000`.
 * All arithmetic uses Long (minor units) — never float/double.
 */
object FeeEngine {
    fun calculate(
        input: FeeInput,
        rules: List<FeeRule>,
    ): FeeResult {
        val calculations =
            rules.map { rule ->
                val (baseAmount, amount) =
                    when (rule.base) {
                        FeeBase.ORDER_TOTAL ->
                            input.orderTotalMinor to applyRate(input.orderTotalMinor, rule.rateBasisPoints)
                        FeeBase.ELIGIBLE_ITEM_VALUE -> {
                            val base = input.eligibleItemValueMinor ?: 0L
                            base to applyRate(base, rule.rateBasisPoints)
                        }
                        FeeBase.FIXED_PER_CONVERTED_ORDER ->
                            (rule.fixedAmountMinor ?: 0L) to (rule.fixedAmountMinor ?: 0L)
                    }

                FeeCalculation(
                    ruleKey = rule.ruleKey,
                    base = rule.base,
                    baseAmountMinor = baseAmount,
                    rateBasisPoints = rule.rateBasisPoints,
                    amountMinor = amount,
                    partyId = rule.partyId,
                    counterpartyId = rule.counterpartyId,
                    currency = input.currency,
                    description = rule.description,
                )
            }

        return FeeResult(
            input = input,
            calculations = calculations,
            totalFeesMinor = calculations.sumOf { it.amountMinor },
        )
    }

    /**
     * Applies a basis-point rate to a minor-unit amount with half-up rounding.
     * `applyRate(1599, 1000)` = `applyRate(15.99 ARS, 10%)` = 160 (1.60 ARS).
     */
    fun applyRate(
        baseMinor: Long,
        basisPoints: Long,
    ): Long = (baseMinor * basisPoints + 5000) / 10000
}
