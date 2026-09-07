package io.snapplay.fees

import io.snapplay.fees.domain.FeeBase
import io.snapplay.fees.domain.FeeEngine
import io.snapplay.fees.domain.FeeInput
import io.snapplay.fees.domain.FeeRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.UUID

private val PARTY = UUID.randomUUID()
private val COUNTERPARTY = UUID.randomUUID()
private val VERSION_ID = UUID.randomUUID()
private val ORDER_ID = UUID.randomUUID()

class FeeEngineTest {
    private fun input(
        orderTotal: Long = 15990L,
        eligibleItemValue: Long? = null,
    ) = FeeInput(
        providerOrderId = ORDER_ID,
        handoffSessionId = null,
        contractVersionId = VERSION_ID,
        orderTotalMinor = orderTotal,
        eligibleItemValueMinor = eligibleItemValue,
        currency = "ARS",
        causativeEvent = "ORDER_DELIVERED",
    )

    private fun rule(
        key: String = "platform_fee",
        base: FeeBase = FeeBase.ORDER_TOTAL,
        basisPoints: Long = 1000,
        fixedAmount: Long? = null,
    ) = FeeRule(key, base, basisPoints, fixedAmount, PARTY, COUNTERPARTY, "Test rule")

    // --- applyRate table tests ---

    @ParameterizedTest(name = "applyRate({0}, {1} bp) = {2}")
    @CsvSource(
        "15990, 1000, 1599",
        "10000, 500, 500",
        "1, 10000, 1",
        "0, 1000, 0",
        "10000, 0, 0",
        "15990, 1, 2",
        "1, 1, 0",
        "100000000, 1000, 10000000",
    )
    fun `applyRate produces correct results`(
        baseMinor: Long,
        basisPoints: Long,
        expected: Long,
    ) {
        assertThat(FeeEngine.applyRate(baseMinor, basisPoints)).isEqualTo(expected)
    }

    // --- FeeEngine.calculate tests ---

    @Test
    fun `ORDER_TOTAL fee calculated correctly`() {
        val result = FeeEngine.calculate(input(15990), listOf(rule(basisPoints = 1000)))

        assertThat(result.calculations).hasSize(1)
        val calc = result.calculations.first()
        assertThat(calc.baseAmountMinor).isEqualTo(15990)
        assertThat(calc.amountMinor).isEqualTo(1599)
        assertThat(calc.rateBasisPoints).isEqualTo(1000)
        assertThat(calc.currency).isEqualTo("ARS")
    }

    @Test
    fun `ELIGIBLE_ITEM_VALUE uses eligible amount not order total`() {
        val result =
            FeeEngine.calculate(
                input(orderTotal = 20000, eligibleItemValue = 12000),
                listOf(rule(base = FeeBase.ELIGIBLE_ITEM_VALUE, basisPoints = 500)),
            )

        assertThat(result.calculations.first().baseAmountMinor).isEqualTo(12000)
        assertThat(result.calculations.first().amountMinor).isEqualTo(600) // 12000 * 5%
    }

    @Test
    fun `ELIGIBLE_ITEM_VALUE defaults to zero when null`() {
        val result =
            FeeEngine.calculate(
                input(orderTotal = 20000, eligibleItemValue = null),
                listOf(rule(base = FeeBase.ELIGIBLE_ITEM_VALUE, basisPoints = 1000)),
            )

        assertThat(result.calculations.first().amountMinor).isEqualTo(0)
    }

    @Test
    fun `FIXED_PER_CONVERTED_ORDER uses fixed amount ignoring rate`() {
        val result =
            FeeEngine.calculate(
                input(orderTotal = 50000),
                listOf(rule(base = FeeBase.FIXED_PER_CONVERTED_ORDER, fixedAmount = 2500)),
            )

        assertThat(result.calculations.first().amountMinor).isEqualTo(2500)
    }

    @Test
    fun `multiple rules produce separate calculations`() {
        val rules =
            listOf(
                rule("platform_fee", FeeBase.ORDER_TOTAL, 1000),
                rule("revenue_share", FeeBase.ORDER_TOTAL, 500),
            )

        val result = FeeEngine.calculate(input(10000), rules)

        assertThat(result.calculations).hasSize(2)
        assertThat(result.calculations.map { it.ruleKey }).containsExactly("platform_fee", "revenue_share")
        assertThat(result.calculations[0].amountMinor).isEqualTo(1000) // 10%
        assertThat(result.calculations[1].amountMinor).isEqualTo(500) // 5%
        assertThat(result.totalFeesMinor).isEqualTo(1500)
    }

    @Test
    fun `zero order total produces zero fees`() {
        val result = FeeEngine.calculate(input(0), listOf(rule(basisPoints = 1000)))

        assertThat(result.calculations.first().amountMinor).isEqualTo(0)
        assertThat(result.totalFeesMinor).isEqualTo(0)
    }

    @Test
    fun `all amounts are Long minor units — no float or double`() {
        // Compile-time guarantee: FeeCalculation.amountMinor is Long, not Double.
        // This test verifies the value is exact integer arithmetic (no floating-point drift).
        val result = FeeEngine.calculate(input(15990), listOf(rule(basisPoints = 1000)))
        assertThat(result.calculations.first().amountMinor).isEqualTo(1599L)
        // 15990 * 1000 / 10000 = 1599 exactly — no rounding error from floats
    }

    @Test
    fun `calculation is explainable from inputs, rule and version`() {
        val result =
            FeeEngine.calculate(
                input(15990),
                listOf(rule("platform_fee", FeeBase.ORDER_TOTAL, 1000)),
            )

        val calc = result.calculations.first()
        // All inputs traceable
        assertThat(result.input.contractVersionId).isEqualTo(VERSION_ID)
        assertThat(result.input.providerOrderId).isEqualTo(ORDER_ID)
        assertThat(calc.ruleKey).isEqualTo("platform_fee")
        assertThat(calc.base).isEqualTo(FeeBase.ORDER_TOTAL)
        assertThat(calc.baseAmountMinor).isEqualTo(15990)
        assertThat(calc.rateBasisPoints).isEqualTo(1000)
        assertThat(calc.amountMinor).isEqualTo(1599)
    }
}
