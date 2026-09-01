package io.snapplay.rappi.mock

import io.snapplay.common.NotFoundException
import io.snapplay.common.ValidationException
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class EmitOrderRequest(
    @field:NotBlank val storeId: String,
    @field:NotBlank val categoryId: String,
    @field:NotBlank val trackingToken: String,
    val scenario: OrderScenario = OrderScenario.DELIVERED,
    @field:Min(0) @field:Max(30_000) val latencyMs: Long = 0,
    val invalidSignature: Boolean = false,
)

/**
 * REST endpoint to trigger manual or scripted Rappi order events.
 *
 * Builds a signed Rappi-style webhook payload and POSTs it to the local
 * /v1/partner/events endpoint, returning the emitted payload and webhook response
 * so the caller can inspect the full round-trip.
 *
 * Not for production — active only when snapplay.rappi-mock-enabled=true.
 */
@RestController
@RequestMapping("/mock/rappi/orders")
@ConditionalOnProperty(name = ["snapplay.rappi-mock-enabled"], havingValue = "true")
class RappiMockOrderController(
    private val catalog: RappiMockCatalog,
    private val emitter: RappiMockOrderEmitter,
) {
    @PostMapping("/emit")
    @ResponseStatus(HttpStatus.OK)
    fun emit(
        @Valid @RequestBody request: EmitOrderRequest,
    ): EmitResult {
        catalog.findStore(request.storeId)
            ?: throw NotFoundException("Unknown mock store: '${request.storeId}'")

        if (catalog.findCategory(request.storeId, request.categoryId) == null) {
            throw ValidationException(
                "Category '${request.categoryId}' does not belong to store '${request.storeId}'. " +
                    "Check the mock catalog for valid store/category combinations.",
            )
        }

        return emitter.emit(
            storeId = request.storeId,
            categoryId = request.categoryId,
            trackingToken = request.trackingToken,
            scenario = request.scenario,
            latencyMs = request.latencyMs,
            invalidSignature = request.invalidSignature,
        )
    }
}
