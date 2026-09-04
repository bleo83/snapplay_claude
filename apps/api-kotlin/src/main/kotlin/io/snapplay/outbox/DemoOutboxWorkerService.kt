package io.snapplay.outbox

import io.snapplay.partner.infrastructure.persistence.DemoOutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Demo-mode outbox worker that processes events from the in-memory [DemoOutboxEventRepository].
 * Tests call [processBatch] directly.
 */
@Service
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoOutboxWorkerService(
    private val outboxRepo: DemoOutboxEventRepository,
    handlers: List<OutboxHandler>,
) {
    private val log = LoggerFactory.getLogger(DemoOutboxWorkerService::class.java)
    private val handlerMap: Map<String, OutboxHandler> = handlers.associateBy { it.supportedEventType }

    data class ProcessedEvent(val eventId: UUID, val succeeded: Boolean)

    private val processed = mutableListOf<ProcessedEvent>()

    fun clear() = processed.clear()

    fun getProcessed(): List<ProcessedEvent> = processed.toList()

    fun processBatch() {
        val events = outboxRepo.events.toList()
        outboxRepo.events.clear()
        for (event in events) {
            val handler = handlerMap[event.eventType]
            if (handler == null) {
                log.warn("[DEMO] No handler for event_type={}", event.eventType)
                continue
            }
            val success =
                runCatching {
                    handler.handle(event.aggregateType, event.aggregateId, event.eventType, event.payload)
                }.getOrElse { false }
            processed.add(ProcessedEvent(event.id, success))
        }
    }
}
