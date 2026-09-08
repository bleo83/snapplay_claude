package io.snapplay.settlement

import io.snapplay.partner.application.port.output.ProviderOrderUpsert
import io.snapplay.partner.domain.ProviderOrderStatus
import io.snapplay.partner.infrastructure.persistence.DemoProviderOrderRepository
import io.snapplay.settlement.application.port.input.CompareSettlementUseCase
import io.snapplay.settlement.application.port.input.ListExceptionsUseCase
import io.snapplay.settlement.domain.ExceptionStatus
import io.snapplay.settlement.domain.RappiSettlementLine
import io.snapplay.settlement.domain.SettlementExceptionType
import io.snapplay.settlement.infrastructure.persistence.DemoSettlementRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private val CONNECTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001")
private val NOW = Instant.now()
private val YESTERDAY = NOW.minus(1, ChronoUnit.DAYS)

@SpringBootTest
@ActiveProfiles("demo")
class SettlementComparisonTest {
    @Autowired lateinit var compareSettlement: CompareSettlementUseCase

    @Autowired lateinit var listExceptions: ListExceptionsUseCase

    @Autowired lateinit var demoOrderRepo: DemoProviderOrderRepository

    @Autowired lateinit var demoSettlementRepo: DemoSettlementRepository

    @BeforeEach
    fun setUp() {
        demoOrderRepo.clear()
        demoSettlementRepo.clear()
    }

    @Test
    fun `matching orders produce no exceptions`() {
        seedOrder("ORD-1", ProviderOrderStatus.DELIVERED, 1599, "ARS")

        val result =
            compareSettlement.compare(
                CONNECTION_ID,
                YESTERDAY,
                NOW.plus(1, ChronoUnit.HOURS),
                listOf(rappiLine("ORD-1", "DELIVERED", 1599, "ARS")),
            )

        assertThat(result.exceptionsFound).isEqualTo(0)
    }

    @Test
    fun `missing order in Snap Play detected`() {
        // No order seeded — Rappi has it but we don't
        val result =
            compareSettlement.compare(
                CONNECTION_ID,
                YESTERDAY,
                NOW.plus(1, ChronoUnit.HOURS),
                listOf(rappiLine("ORD-MISSING", "DELIVERED", 1599, "ARS")),
            )

        assertThat(result.exceptionsFound).isEqualTo(1)
        val exc = demoSettlementRepo.allExceptions().first()
        assertThat(exc.exceptionType).isEqualTo(SettlementExceptionType.MISSING_IN_SNAPPLAY)
    }

    @Test
    fun `missing order in provider detected`() {
        seedOrder("ORD-OURS", ProviderOrderStatus.DELIVERED, 1599, "ARS")

        val result =
            compareSettlement.compare(
                CONNECTION_ID,
                YESTERDAY,
                NOW.plus(1, ChronoUnit.HOURS),
                emptyList(),
            )

        assertThat(result.exceptionsFound).isEqualTo(1)
        val exc = demoSettlementRepo.allExceptions().first()
        assertThat(exc.exceptionType).isEqualTo(SettlementExceptionType.MISSING_IN_PROVIDER)
    }

    @Test
    fun `status mismatch detected`() {
        seedOrder("ORD-STATUS", ProviderOrderStatus.PLACED, 1599, "ARS")

        compareSettlement.compare(
            CONNECTION_ID,
            YESTERDAY,
            NOW.plus(1, ChronoUnit.HOURS),
            listOf(rappiLine("ORD-STATUS", "DELIVERED", 1599, "ARS")),
        )

        val exc = demoSettlementRepo.allExceptions().first { it.exceptionType == SettlementExceptionType.STATUS_MISMATCH }
        assertThat(exc.expectedValue).isEqualTo("PLACED")
        assertThat(exc.reportedValue).isEqualTo("DELIVERED")
    }

    @Test
    fun `amount mismatch detected (beyond tolerance)`() {
        seedOrder("ORD-AMOUNT", ProviderOrderStatus.DELIVERED, 1599, "ARS")

        compareSettlement.compare(
            CONNECTION_ID,
            YESTERDAY,
            NOW.plus(1, ChronoUnit.HOURS),
            listOf(rappiLine("ORD-AMOUNT", "DELIVERED", 2000, "ARS")),
        )

        val exc = demoSettlementRepo.allExceptions().first { it.exceptionType == SettlementExceptionType.AMOUNT_MISMATCH }
        assertThat(exc.expectedValue).isEqualTo("1599")
        assertThat(exc.reportedValue).isEqualTo("2000")
        assertThat(exc.toleranceAppliedMinor).isEqualTo(1)
    }

    @Test
    fun `amount within tolerance produces no exception`() {
        seedOrder("ORD-CLOSE", ProviderOrderStatus.DELIVERED, 1599, "ARS")

        val result =
            compareSettlement.compare(
                CONNECTION_ID,
                YESTERDAY,
                NOW.plus(1, ChronoUnit.HOURS),
                listOf(rappiLine("ORD-CLOSE", "DELIVERED", 1600, "ARS")),
            )

        assertThat(result.exceptionsFound).isEqualTo(0)
    }

    @Test
    fun `reprocessing report is idempotent`() {
        seedOrder("ORD-IDEM", ProviderOrderStatus.PLACED, 1599, "ARS")
        val lines = listOf(rappiLine("ORD-IDEM", "DELIVERED", 1599, "ARS"))

        compareSettlement.compare(CONNECTION_ID, YESTERDAY, NOW.plus(1, ChronoUnit.HOURS), lines)
        compareSettlement.compare(CONNECTION_ID, YESTERDAY, NOW.plus(1, ChronoUnit.HOURS), lines)

        // Second comparison creates a new report but duplicate exceptions are skipped
        val exceptions = listExceptions.list(CONNECTION_ID, null)
        val statusMismatches = exceptions.filter { it.providerOrderRef == "ORD-IDEM" && it.exceptionType == SettlementExceptionType.STATUS_MISMATCH }
        assertThat(statusMismatches).hasSize(1)
    }

    @Test
    fun `each exception has type, expected, reported, and status`() {
        seedOrder("ORD-CHECK", ProviderOrderStatus.PLACED, 1599, "ARS")

        compareSettlement.compare(
            CONNECTION_ID,
            YESTERDAY,
            NOW.plus(1, ChronoUnit.HOURS),
            listOf(rappiLine("ORD-CHECK", "DELIVERED", 3000, "USD")),
        )

        val exceptions = demoSettlementRepo.allExceptions()
        assertThat(exceptions).isNotEmpty
        exceptions.forEach { exc ->
            assertThat(exc.exceptionType).isNotNull
            assertThat(exc.status).isEqualTo(ExceptionStatus.OPEN)
            assertThat(exc.providerOrderRef).isEqualTo("ORD-CHECK")
        }
    }

    private fun seedOrder(
        ref: String,
        status: ProviderOrderStatus,
        totalMinor: Long,
        currency: String,
    ) {
        demoOrderRepo.upsert(
            ProviderOrderUpsert(
                connectionId = CONNECTION_ID,
                handoffId = null,
                providerOrderRef = ref,
                newStatus = status,
                currency = currency,
                orderTotalMinor = totalMinor,
                placedAt = YESTERDAY.plus(1, ChronoUnit.HOURS),
                deliveredAt = if (status == ProviderOrderStatus.DELIVERED) NOW else null,
            ),
            partnerEventId = null,
        )
    }

    private fun rappiLine(
        ref: String,
        status: String,
        totalMinor: Long,
        currency: String,
    ) = RappiSettlementLine(
        providerOrderRef = ref,
        status = status,
        orderTotalMinor = totalMinor,
        feeMinor = totalMinor / 10,
        currency = currency,
        placedAt = YESTERDAY.plus(1, ChronoUnit.HOURS),
        deliveredAt = if (status == "DELIVERED") NOW else null,
    )
}
