package io.snapplay.partner.application.usecase

import io.snapplay.config.SnapPlayProperties
import io.snapplay.partner.application.port.input.ReconcileOrdersUseCase
import io.snapplay.partner.application.port.output.ProviderOrderRepository
import io.snapplay.partner.application.port.output.ProviderOrderUpsert
import io.snapplay.partner.application.port.output.RappiOrdersClient
import io.snapplay.partner.application.port.output.ReconciliationCheckpointRepository
import io.snapplay.partner.application.port.output.ReconciliationMismatchRepository
import io.snapplay.partner.application.port.output.ReconciliationOrderPort
import io.snapplay.partner.domain.MismatchType
import io.snapplay.partner.domain.ProviderOrder
import io.snapplay.partner.domain.ProviderOrderStatus
import io.snapplay.partner.domain.RappiOrderSnapshot
import io.snapplay.partner.domain.ReconciliationMismatch
import io.snapplay.partner.domain.ReconciliationResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class ReconcileOrdersUseCaseImpl(
    private val rappiClient: RappiOrdersClient,
    private val checkpointRepo: ReconciliationCheckpointRepository,
    private val mismatchRepo: ReconciliationMismatchRepository,
    private val orderPort: ReconciliationOrderPort,
    private val orderRepo: ProviderOrderRepository,
    private val props: SnapPlayProperties,
) : ReconcileOrdersUseCase {
    private val log = LoggerFactory.getLogger(ReconcileOrdersUseCaseImpl::class.java)

    override fun reconcile(
        connectionId: UUID,
        from: Instant?,
        to: Instant?,
    ): ReconciliationResult {
        val now = Instant.now()
        val effectiveTo = to ?: now
        val effectiveFrom =
            from
                ?: checkpointRepo.findByConnectionId(connectionId)
                    ?.lastReconciledAt
                    ?.minus(props.reconciliationLookbackDays, ChronoUnit.DAYS)
                ?: effectiveTo.minus(props.reconciliationLookbackDays, ChronoUnit.DAYS)

        log.info("Reconciling connection={} from={} to={}", connectionId, effectiveFrom, effectiveTo)

        var rappiOrdersChecked = 0
        var missingRecovered = 0
        var mismatchesDetected = 0
        var cursor: String? = null

        do {
            val page = rappiClient.fetchOrders(connectionId, effectiveFrom, effectiveTo, cursor)

            for (rappiOrder in page.orders) {
                rappiOrdersChecked++
                val existing = orderPort.findByRef(connectionId, rappiOrder.orderRef)

                if (existing == null) {
                    recoverMissingOrder(connectionId, rappiOrder, now)
                    missingRecovered++
                    mismatchesDetected++
                } else {
                    mismatchesDetected += compareOrder(connectionId, existing, rappiOrder, now)
                }
            }

            cursor = page.nextCursor
        } while (cursor != null)

        // Advance checkpoint only after all pages processed successfully
        checkpointRepo.advance(connectionId, effectiveTo)

        log.info(
            "Reconciliation complete connection={}: checked={} recovered={} mismatches={}",
            connectionId,
            rappiOrdersChecked,
            missingRecovered,
            mismatchesDetected,
        )

        return ReconciliationResult(
            connectionId = connectionId,
            from = effectiveFrom,
            to = effectiveTo,
            rappiOrdersChecked = rappiOrdersChecked,
            missingRecovered = missingRecovered,
            mismatchesDetected = mismatchesDetected,
        )
    }

    private fun recoverMissingOrder(
        connectionId: UUID,
        rappiOrder: RappiOrderSnapshot,
        now: Instant,
    ) {
        val status =
            runCatching { ProviderOrderStatus.valueOf(rappiOrder.status) }.getOrElse {
                log.warn("Unknown Rappi order status '{}' for ref={}", rappiOrder.status, rappiOrder.orderRef)
                ProviderOrderStatus.PLACED
            }

        orderRepo.upsert(
            ProviderOrderUpsert(
                connectionId = connectionId,
                handoffId = null,
                providerOrderRef = rappiOrder.orderRef,
                newStatus = status,
                currency = rappiOrder.currency,
                orderTotalMinor = rappiOrder.totalMinor,
                placedAt = rappiOrder.placedAt,
                deliveredAt = rappiOrder.deliveredAt,
            ),
            partnerEventId = null,
        )

        mismatchRepo.save(
            ReconciliationMismatch(
                id = UUID.randomUUID(),
                connectionId = connectionId,
                providerOrderRef = rappiOrder.orderRef,
                mismatchType = MismatchType.MISSING_IN_SNAPPLAY,
                snapPlayValue = null,
                rappiValue = rappiOrder.status,
                detectedAt = now,
            ),
        )

        log.info("Recovered missing order ref={} status={}", rappiOrder.orderRef, status)
    }

    private fun compareOrder(
        connectionId: UUID,
        existing: ProviderOrder,
        rappiOrder: RappiOrderSnapshot,
        now: Instant,
    ): Int {
        var count = 0

        val rappiStatus = rappiOrder.status
        if (existing.status.name != rappiStatus) {
            mismatchRepo.save(
                ReconciliationMismatch(
                    id = UUID.randomUUID(),
                    connectionId = connectionId,
                    providerOrderRef = rappiOrder.orderRef,
                    mismatchType = MismatchType.STATUS_MISMATCH,
                    snapPlayValue = existing.status.name,
                    rappiValue = rappiStatus,
                    detectedAt = now,
                ),
            )
            count++
        }

        if (existing.orderTotalMinor != rappiOrder.totalMinor) {
            mismatchRepo.save(
                ReconciliationMismatch(
                    id = UUID.randomUUID(),
                    connectionId = connectionId,
                    providerOrderRef = rappiOrder.orderRef,
                    mismatchType = MismatchType.AMOUNT_MISMATCH,
                    snapPlayValue = existing.orderTotalMinor.toString(),
                    rappiValue = rappiOrder.totalMinor.toString(),
                    detectedAt = now,
                ),
            )
            count++
        }

        if (existing.currency != rappiOrder.currency) {
            mismatchRepo.save(
                ReconciliationMismatch(
                    id = UUID.randomUUID(),
                    connectionId = connectionId,
                    providerOrderRef = rappiOrder.orderRef,
                    mismatchType = MismatchType.CURRENCY_MISMATCH,
                    snapPlayValue = existing.currency,
                    rappiValue = rappiOrder.currency,
                    detectedAt = now,
                ),
            )
            count++
        }

        return count
    }
}
