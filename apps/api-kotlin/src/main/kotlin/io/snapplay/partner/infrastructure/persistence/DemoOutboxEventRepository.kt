package io.snapplay.partner.infrastructure.persistence

import io.snapplay.partner.application.port.output.OutboxEventRepository
import io.snapplay.partner.domain.OutboxEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.concurrent.CopyOnWriteArrayList

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoOutboxEventRepository : OutboxEventRepository {
    val events: MutableList<OutboxEvent> = CopyOnWriteArrayList()

    override fun enqueue(event: OutboxEvent) {
        events.add(event)
    }
}
