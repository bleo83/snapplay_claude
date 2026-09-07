package io.snapplay.ledger

import io.snapplay.common.ValidationException
import io.snapplay.ledger.application.port.input.GetBalanceUseCase
import io.snapplay.ledger.application.port.input.ListOrderEntriesUseCase
import io.snapplay.ledger.application.port.input.RecordAdjustmentUseCase
import io.snapplay.ledger.application.port.input.RecordEarnUseCase
import io.snapplay.ledger.application.port.input.RecordReversalUseCase
import io.snapplay.ledger.domain.EntryStatus
import io.snapplay.ledger.domain.EntryType
import io.snapplay.ledger.infrastructure.persistence.DemoLedgerRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private val ORDER_ID = UUID.randomUUID()
private val SESSION_ID = UUID.randomUUID()
private val CONTRACT_VERSION_ID = UUID.randomUUID()
private val PARTY = UUID.randomUUID()
private val COUNTERPARTY = UUID.randomUUID()
private val ACTOR = UUID.randomUUID()

@SpringBootTest
@ActiveProfiles("demo")
class LedgerTest {
    @Autowired lateinit var recordEarn: RecordEarnUseCase

    @Autowired lateinit var recordReversal: RecordReversalUseCase

    @Autowired lateinit var recordAdjustment: RecordAdjustmentUseCase

    @Autowired lateinit var getBalance: GetBalanceUseCase

    @Autowired lateinit var listEntries: ListOrderEntriesUseCase

    @Autowired lateinit var demoRepo: DemoLedgerRepository

    @BeforeEach
    fun setUp() = demoRepo.clear()

    private fun earn(
        amount: Long = 1000,
        orderId: UUID = ORDER_ID,
    ) = recordEarn.recordEarn(
        providerOrderId = orderId,
        handoffSessionId = SESSION_ID,
        contractVersionId = CONTRACT_VERSION_ID,
        ruleKey = "platform_fee",
        causativeEvent = "ORDER_DELIVERED",
        partyId = PARTY,
        counterpartyId = COUNTERPARTY,
        baseAmountMinor = 10000,
        amountMinor = amount,
        currency = "ARS",
    )

    @Test
    fun `earn entry is created with correct fields`() {
        val entry = earn()
        assertThat(entry.entryType).isEqualTo(EntryType.EARN)
        assertThat(entry.status).isEqualTo(EntryStatus.PENDING)
        assertThat(entry.amountMinor).isEqualTo(1000)
        assertThat(entry.currency).isEqualTo("ARS")
        assertThat(entry.originalEntryId).isNull()
    }

    @Test
    fun `full reversal creates opposite-sign entry and marks original REVERSED`() {
        val original = earn(1000)

        val reversal = recordReversal.recordReversal(original.id, 1000, "ORDER_REFUNDED", "Customer refund")

        assertThat(reversal.entryType).isEqualTo(EntryType.REVERSAL)
        assertThat(reversal.amountMinor).isEqualTo(-1000)
        assertThat(reversal.originalEntryId).isEqualTo(original.id)
        assertThat(reversal.reason).isEqualTo("Customer refund")

        val updated = demoRepo.findById(original.id)!!
        assertThat(updated.status).isEqualTo(EntryStatus.REVERSED)
    }

    @Test
    fun `partial refund creates partial reversal without marking original REVERSED`() {
        val original = earn(1000)

        val reversal = recordReversal.recordReversal(original.id, 500, "ORDER_PARTIAL_REFUND", "Partial refund")

        assertThat(reversal.amountMinor).isEqualTo(-500)

        // Original NOT reversed yet (only partially)
        val updated = demoRepo.findById(original.id)!!
        assertThat(updated.status).isEqualTo(EntryStatus.PENDING)
    }

    @Test
    fun `double reversal beyond original amount is rejected`() {
        val original = earn(1000)
        recordReversal.recordReversal(original.id, 800, "ORDER_PARTIAL_REFUND", "First refund")

        assertThatThrownBy {
            recordReversal.recordReversal(original.id, 300, "ORDER_PARTIAL_REFUND", "Second refund exceeds")
        }.isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("exceed")
    }

    @Test
    fun `late refund after partial creates correct entries`() {
        val original = earn(1000)

        recordReversal.recordReversal(original.id, 500, "ORDER_PARTIAL_REFUND", "Partial")
        recordReversal.recordReversal(original.id, 500, "ORDER_REFUNDED", "Remaining refund")

        val entries = listEntries.list(ORDER_ID)
        assertThat(entries).hasSize(3)
        assertThat(entries.sumOf { it.amountMinor }).isEqualTo(0) // fully cancelled out

        val updated = demoRepo.findById(original.id)!!
        assertThat(updated.status).isEqualTo(EntryStatus.REVERSED)
    }

    @Test
    fun `adjustment requires reason and actor`() {
        val original = earn()

        val adj = recordAdjustment.recordAdjustment(original.id, -200, "Overcharged fee — ticket FIN-42", ACTOR)

        assertThat(adj.entryType).isEqualTo(EntryType.ADJUSTMENT)
        assertThat(adj.amountMinor).isEqualTo(-200)
        assertThat(adj.reason).isEqualTo("Overcharged fee — ticket FIN-42")
        assertThat(adj.actorId).isEqualTo(ACTOR)
    }

    @Test
    fun `balance is reconstructable from entries`() {
        val now = Instant.now()
        earn(1000)
        earn(2000, orderId = UUID.randomUUID())

        val balance = getBalance.getBalance(PARTY, "ARS", now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS))

        assertThat(balance.totalMinor).isEqualTo(3000)
        assertThat(balance.pendingMinor).isEqualTo(3000)
        assertThat(balance.currency).isEqualTo("ARS")
    }

    @Test
    fun `balance does not cross currencies`() {
        earn(1000)

        val usdBalance = getBalance.getBalance(PARTY, "USD", Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.HOURS))
        assertThat(usdBalance.totalMinor).isEqualTo(0)
    }

    @Test
    fun `original entry amount never changes after reversal`() {
        val original = earn(1000)
        recordReversal.recordReversal(original.id, 1000, "ORDER_REFUNDED", "Full refund")

        val reloaded = demoRepo.findById(original.id)!!
        assertThat(reloaded.amountMinor).isEqualTo(1000) // amount unchanged
        assertThat(reloaded.status).isEqualTo(EntryStatus.REVERSED) // only status changed
    }
}
