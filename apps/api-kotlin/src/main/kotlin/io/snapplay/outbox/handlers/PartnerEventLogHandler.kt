package io.snapplay.outbox.handlers

import io.snapplay.outbox.OutboxHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Sample handler: logs partner events processed by the outbox worker.
 * In production this would forward to analytics, notification systems, etc.
 */
@Component
class PartnerEventLogHandler : OutboxHandler {
    private val log = LoggerFactory.getLogger(PartnerEventLogHandler::class.java)

    override val supportedEventType: String = "ORDER_DELIVERED"

    override fun handle(
        aggregateType: String,
        aggregateId: UUID,
        eventType: String,
        payload: String,
    ): Boolean {
        log.info("Outbox: processing {} aggregate={} id={}", eventType, aggregateType, aggregateId)
        return true
    }
}
