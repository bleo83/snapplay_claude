package io.snapplay.ledger.infrastructure.web

import io.snapplay.identity.PrincipalResolver
import io.snapplay.ledger.application.port.input.GetBalanceUseCase
import io.snapplay.ledger.application.port.input.ListOrderEntriesUseCase
import io.snapplay.ledger.application.port.input.RecordAdjustmentUseCase
import io.snapplay.ledger.domain.LedgerEntry
import io.snapplay.ledger.domain.PartyBalance
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@RestController
@RequestMapping("/v1/ledger")
class LedgerController(
    private val principalResolver: PrincipalResolver,
    private val listOrderEntries: ListOrderEntriesUseCase,
    private val getBalance: GetBalanceUseCase,
    private val recordAdjustment: RecordAdjustmentUseCase,
) {
    @GetMapping("/orders/{orderId}/entries")
    fun entriesByOrder(
        @PathVariable orderId: UUID,
    ): List<LedgerEntry> = listOrderEntries.list(orderId)

    @GetMapping("/balance")
    fun balance(
        @RequestParam partyId: UUID,
        @RequestParam currency: String,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        from: Instant?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        to: Instant?,
    ): PartyBalance {
        val effectiveTo = to ?: Instant.now()
        val effectiveFrom = from ?: effectiveTo.minus(30, ChronoUnit.DAYS)
        return getBalance.getBalance(partyId, currency, effectiveFrom, effectiveTo)
    }

    @PostMapping("/entries/{entryId}/adjust")
    @ResponseStatus(HttpStatus.CREATED)
    fun adjust(
        @PathVariable entryId: UUID,
        @RequestBody request: AdjustmentRequest,
    ): LedgerEntry {
        val principal = principalResolver.resolve()
        return recordAdjustment.recordAdjustment(entryId, request.amountMinor, request.reason, principal.userId)
    }
}

data class AdjustmentRequest(
    val amountMinor: Long,
    val reason: String,
)
