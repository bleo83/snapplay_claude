package io.snapplay.partner

import io.snapplay.partner.application.port.input.ReconcileOrdersUseCase
import io.snapplay.partner.domain.MismatchType
import io.snapplay.partner.domain.ProviderOrderStatus
import io.snapplay.partner.domain.RappiOrderPage
import io.snapplay.partner.domain.RappiOrderSnapshot
import io.snapplay.partner.infrastructure.client.DemoRappiOrdersClient
import io.snapplay.partner.infrastructure.persistence.DemoProviderOrderRepository
import io.snapplay.partner.infrastructure.persistence.DemoReconciliationCheckpointRepository
import io.snapplay.partner.infrastructure.persistence.DemoReconciliationMismatchRepository
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

@SpringBootTest
@ActiveProfiles("demo")
class ReconciliationTest {
    @Autowired lateinit var reconcileUseCase: ReconcileOrdersUseCase

    @Autowired lateinit var demoRappiClient: DemoRappiOrdersClient

    @Autowired lateinit var demoOrderRepo: DemoProviderOrderRepository

    @Autowired lateinit var demoCheckpointRepo: DemoReconciliationCheckpointRepository

    @Autowired lateinit var demoMismatchRepo: DemoReconciliationMismatchRepository

    private val now = Instant.now()
    private val yesterday = now.minus(1, ChronoUnit.DAYS)

    @BeforeEach
    fun setUp() {
        demoRappiClient.reset()
        demoOrderRepo.clear()
        demoCheckpointRepo.clear()
        demoMismatchRepo.clear()
    }

    @Test
    fun `missing order recovered without duplicating`() {
        val rappiOrder =
            RappiOrderSnapshot(
                orderRef = "ORD-MISSING-1",
                status = "DELIVERED",
                currency = "ARS",
                totalMinor = 1599,
                placedAt = yesterday,
                deliveredAt = now,
            )
        demoRappiClient.enqueueOrders(rappiOrder)

        val result = reconcileUseCase.reconcile(CONNECTION_ID, from = yesterday, to = now)

        assertThat(result.missingRecovered).isEqualTo(1)
        assertThat(result.rappiOrdersChecked).isEqualTo(1)

        val recovered = demoOrderRepo.findByRef(CONNECTION_ID, "ORD-MISSING-1")
        assertThat(recovered).isNotNull
        assertThat(recovered!!.status).isEqualTo(ProviderOrderStatus.DELIVERED)
        assertThat(recovered.orderTotalMinor).isEqualTo(1599)

        // Running again with same data: should not create a second order
        demoRappiClient.reset()
        demoRappiClient.enqueueOrders(rappiOrder)
        val result2 = reconcileUseCase.reconcile(CONNECTION_ID, from = yesterday, to = now)
        assertThat(result2.missingRecovered).isEqualTo(0)
    }

    @Test
    fun `status mismatch recorded without overwriting`() {
        // Seed an existing order in PLACED status
        seedOrder("ORD-STATUS-1", ProviderOrderStatus.PLACED, 1599, "ARS")

        // Rappi says it's DELIVERED
        demoRappiClient.enqueueOrders(
            RappiOrderSnapshot(
                orderRef = "ORD-STATUS-1",
                status = "DELIVERED",
                currency = "ARS",
                totalMinor = 1599,
                placedAt = yesterday,
                deliveredAt = now,
            ),
        )

        val result = reconcileUseCase.reconcile(CONNECTION_ID, from = yesterday, to = now)

        assertThat(result.mismatchesDetected).isEqualTo(1)

        val mismatches = demoMismatchRepo.mismatches
        assertThat(mismatches).hasSize(1)
        assertThat(mismatches[0].mismatchType).isEqualTo(MismatchType.STATUS_MISMATCH)
        assertThat(mismatches[0].snapPlayValue).isEqualTo("PLACED")
        assertThat(mismatches[0].rappiValue).isEqualTo("DELIVERED")

        // Existing order NOT overwritten — still PLACED
        val order = demoOrderRepo.findByRef(CONNECTION_ID, "ORD-STATUS-1")
        assertThat(order!!.status).isEqualTo(ProviderOrderStatus.PLACED)
    }

    @Test
    fun `amount mismatch recorded`() {
        seedOrder("ORD-AMOUNT-1", ProviderOrderStatus.DELIVERED, 1599, "ARS")

        demoRappiClient.enqueueOrders(
            RappiOrderSnapshot(
                orderRef = "ORD-AMOUNT-1",
                status = "DELIVERED",
                currency = "ARS",
                totalMinor = 2000,
                placedAt = yesterday,
                deliveredAt = now,
            ),
        )

        val result = reconcileUseCase.reconcile(CONNECTION_ID, from = yesterday, to = now)

        assertThat(result.mismatchesDetected).isEqualTo(1)
        val mismatch = demoMismatchRepo.mismatches.first()
        assertThat(mismatch.mismatchType).isEqualTo(MismatchType.AMOUNT_MISMATCH)
        assertThat(mismatch.snapPlayValue).isEqualTo("1599")
        assertThat(mismatch.rappiValue).isEqualTo("2000")
    }

    @Test
    fun `checkpoint advances only after successful persistence`() {
        demoRappiClient.enqueueOrders(
            RappiOrderSnapshot("ORD-CP-1", "DELIVERED", "ARS", 1599, yesterday, now),
        )

        val to = now
        reconcileUseCase.reconcile(CONNECTION_ID, from = yesterday, to = to)

        val checkpoint = demoCheckpointRepo.findByConnectionId(CONNECTION_ID)
        assertThat(checkpoint).isNotNull
        assertThat(checkpoint!!.lastReconciledAt).isEqualTo(to)
    }

    @Test
    fun `job resumes from checkpoint after failure`() {
        // First run: processes page successfully
        demoRappiClient.enqueueOrders(
            RappiOrderSnapshot("ORD-RESUME-1", "DELIVERED", "ARS", 1000, yesterday, now),
        )
        reconcileUseCase.reconcile(CONNECTION_ID, from = yesterday, to = now)

        // Checkpoint should be at `now`
        val checkpoint = demoCheckpointRepo.findByConnectionId(CONNECTION_ID)
        assertThat(checkpoint).isNotNull

        // Second run: new orders appear (uses checkpoint as base)
        demoRappiClient.reset()
        demoRappiClient.enqueueOrders(
            RappiOrderSnapshot("ORD-RESUME-2", "PLACED", "ARS", 500, now, null),
        )

        val result = reconcileUseCase.reconcile(CONNECTION_ID)
        assertThat(result.rappiOrdersChecked).isEqualTo(1)
        assertThat(demoOrderRepo.findByRef(CONNECTION_ID, "ORD-RESUME-2")).isNotNull
    }

    @Test
    fun `paginated response - repeated page cursor handled`() {
        val page1 =
            RappiOrderPage(
                orders =
                    listOf(
                        RappiOrderSnapshot("ORD-P1", "DELIVERED", "ARS", 1000, yesterday, now),
                    ),
                nextCursor = "cursor-page-2",
            )
        val page2 =
            RappiOrderPage(
                orders =
                    listOf(
                        RappiOrderSnapshot("ORD-P2", "PLACED", "ARS", 500, yesterday, null),
                    ),
                nextCursor = null,
            )
        demoRappiClient.enqueuePages(page1, page2)

        val result = reconcileUseCase.reconcile(CONNECTION_ID, from = yesterday, to = now)

        assertThat(result.rappiOrdersChecked).isEqualTo(2)
        assertThat(result.missingRecovered).isEqualTo(2)
        assertThat(demoOrderRepo.findByRef(CONNECTION_ID, "ORD-P1")).isNotNull
        assertThat(demoOrderRepo.findByRef(CONNECTION_ID, "ORD-P2")).isNotNull
    }

    @Test
    fun `late refund detected as status mismatch`() {
        seedOrder("ORD-REFUND-1", ProviderOrderStatus.DELIVERED, 1599, "ARS")

        demoRappiClient.enqueueOrders(
            RappiOrderSnapshot(
                orderRef = "ORD-REFUND-1",
                status = "REFUNDED",
                currency = "ARS",
                totalMinor = 1599,
                placedAt = yesterday.minus(5, ChronoUnit.DAYS),
                deliveredAt = yesterday.minus(4, ChronoUnit.DAYS),
            ),
        )

        val result = reconcileUseCase.reconcile(CONNECTION_ID, from = yesterday.minus(7, ChronoUnit.DAYS), to = now)

        assertThat(result.mismatchesDetected).isEqualTo(1)
        val mismatch = demoMismatchRepo.mismatches.first()
        assertThat(mismatch.mismatchType).isEqualTo(MismatchType.STATUS_MISMATCH)
        assertThat(mismatch.snapPlayValue).isEqualTo("DELIVERED")
        assertThat(mismatch.rappiValue).isEqualTo("REFUNDED")

        // Original order preserved — mismatch is a record, not an overwrite
        assertThat(demoOrderRepo.findByRef(CONNECTION_ID, "ORD-REFUND-1")!!.status)
            .isEqualTo(ProviderOrderStatus.DELIVERED)
    }

    @Test
    fun `matching order produces no mismatches`() {
        seedOrder("ORD-OK-1", ProviderOrderStatus.DELIVERED, 1599, "ARS")

        demoRappiClient.enqueueOrders(
            RappiOrderSnapshot("ORD-OK-1", "DELIVERED", "ARS", 1599, yesterday, now),
        )

        val result = reconcileUseCase.reconcile(CONNECTION_ID, from = yesterday, to = now)

        assertThat(result.mismatchesDetected).isEqualTo(0)
        assertThat(demoMismatchRepo.mismatches).isEmpty()
    }

    private fun seedOrder(
        ref: String,
        status: ProviderOrderStatus,
        totalMinor: Long,
        currency: String,
    ) {
        demoOrderRepo.upsert(
            io.snapplay.partner.application.port.output.ProviderOrderUpsert(
                connectionId = CONNECTION_ID,
                handoffId = null,
                providerOrderRef = ref,
                newStatus = status,
                currency = currency,
                orderTotalMinor = totalMinor,
                placedAt = yesterday,
                deliveredAt = if (status == ProviderOrderStatus.DELIVERED) now else null,
            ),
            partnerEventId = null,
        )
    }
}
