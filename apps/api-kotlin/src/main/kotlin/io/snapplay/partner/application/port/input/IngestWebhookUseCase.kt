package io.snapplay.partner.application.port.input

enum class IngestResult { ACCEPTED, DUPLICATE }

interface IngestWebhookUseCase {
    /**
     * Validates and persists a signed Rappi order-event webhook.
     *
     * @param rawBody the unmodified request body bytes (used for signature verification)
     * @param signature the `X-Rappi-Signature` header value (`sha256=<hex>`)
     * @return [IngestResult.ACCEPTED] for new events, [IngestResult.DUPLICATE] for already-seen event_ids
     * @throws io.snapplay.common.UnauthorizedException if the HMAC signature is invalid
     * @throws io.snapplay.common.ValidationException if the event timestamp is stale or the tracking token is unknown
     */
    fun ingest(
        rawBody: ByteArray,
        signature: String,
    ): IngestResult
}
