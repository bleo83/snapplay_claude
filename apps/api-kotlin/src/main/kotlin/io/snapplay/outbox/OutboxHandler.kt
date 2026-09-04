package io.snapplay.outbox

import java.util.UUID

/**
 * Components implement this interface to react to outbox events.
 * The worker auto-discovers all handlers via Spring DI.
 * Handlers MUST be idempotent — they may be called more than once for the same event.
 */
interface OutboxHandler {
    /** The event_type this handler processes (e.g. "ORDER_DELIVERED"). */
    val supportedEventType: String

    /**
     * Processes the event.
     * @return true on success; false or exception triggers retry/dead-letter.
     */
    fun handle(
        aggregateType: String,
        aggregateId: UUID,
        eventType: String,
        payload: String,
    ): Boolean
}
