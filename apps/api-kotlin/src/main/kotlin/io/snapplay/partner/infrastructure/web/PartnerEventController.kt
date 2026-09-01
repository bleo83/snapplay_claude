package io.snapplay.partner.infrastructure.web

import io.snapplay.partner.application.port.input.IngestWebhookUseCase
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/partner")
class PartnerEventController(
    private val ingestWebhookUseCase: IngestWebhookUseCase,
) {
    @PostMapping("/events")
    fun ingestEvent(
        @RequestBody rawBody: ByteArray,
        @RequestHeader("X-Rappi-Signature", required = false, defaultValue = "") signature: String,
    ): ResponseEntity<Void> {
        ingestWebhookUseCase.ingest(rawBody, signature)
        return ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }
}
