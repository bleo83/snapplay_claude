package io.snapplay.fees

import io.snapplay.fees.application.port.input.CalculateFeesUseCase
import io.snapplay.fees.domain.FeeBase
import io.snapplay.fees.domain.FeeInput
import io.snapplay.fees.domain.FeeRule
import io.snapplay.fees.infrastructure.persistence.DemoFeeRuleResolver
import io.snapplay.ledger.infrastructure.persistence.DemoLedgerRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

private val PARTY = UUID.randomUUID()
private val COUNTERPARTY = UUID.randomUUID()
private val VERSION_ID = UUID.randomUUID()

@SpringBootTest
@ActiveProfiles("demo")
class CalculateFeesUseCaseTest {
    @Autowired lateinit var calculateFees: CalculateFeesUseCase

    @Autowired lateinit var demoRuleResolver: DemoFeeRuleResolver

    @Autowired lateinit var demoLedger: DemoLedgerRepository

    @BeforeEach
    fun setUp() {
        demoRuleResolver.clear()
        demoLedger.clear()
    }

    private fun input(orderTotal: Long = 15990) =
        FeeInput(
            providerOrderId = UUID.randomUUID(),
            handoffSessionId = UUID.randomUUID(),
            contractVersionId = VERSION_ID,
            orderTotalMinor = orderTotal,
            eligibleItemValueMinor = null,
            currency = "ARS",
            causativeEvent = "ORDER_DELIVERED",
        )

    @Test
    fun `calculateAndRecord creates ledger entries`() {
        demoRuleResolver.setRules(
            VERSION_ID,
            listOf(
                FeeRule("platform_fee", FeeBase.ORDER_TOTAL, 1000, null, PARTY, COUNTERPARTY, "10% platform fee"),
                FeeRule("revenue_share", FeeBase.ORDER_TOTAL, 500, null, COUNTERPARTY, PARTY, "5% rev share"),
            ),
        )

        val result = calculateFees.calculateAndRecord(input())

        assertThat(result.calculations).hasSize(2)
        assertThat(demoLedger.all()).hasSize(2)

        val platformEntry = demoLedger.all().first { it.ruleKey == "platform_fee" }
        assertThat(platformEntry.amountMinor).isEqualTo(1599)
        assertThat(platformEntry.partyId).isEqualTo(PARTY)
    }

    @Test
    fun `reprocessing same order does not duplicate ledger entries`() {
        demoRuleResolver.setRules(
            VERSION_ID,
            listOf(FeeRule("platform_fee", FeeBase.ORDER_TOTAL, 1000, null, PARTY, COUNTERPARTY, "Fee")),
        )

        val fixedInput = input()
        calculateFees.calculateAndRecord(fixedInput)
        calculateFees.calculateAndRecord(fixedInput) // duplicate

        // Only one entry per (order, rule_key, causative_event)
        val entries = demoLedger.all().filter { it.providerOrderId == fixedInput.providerOrderId }
        assertThat(entries).hasSize(1)
    }

    @Test
    fun `missing rules throws NotFoundException`() {
        // No rules configured for VERSION_ID
        assertThatThrownBy { calculateFees.calculate(input()) }
            .hasMessageContaining("No fee rules")
    }

    @Test
    fun `calculate without record does not write to ledger`() {
        demoRuleResolver.setRules(
            VERSION_ID,
            listOf(FeeRule("platform_fee", FeeBase.ORDER_TOTAL, 1000, null, PARTY, COUNTERPARTY, "Fee")),
        )

        val result = calculateFees.calculate(input())

        assertThat(result.calculations).hasSize(1)
        assertThat(demoLedger.all()).isEmpty()
    }
}
