package io.snapplay.partner.infrastructure.persistence

import io.snapplay.partner.application.port.output.PartnerEventRepository
import io.snapplay.partner.domain.PartnerEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoPartnerEventRepository : PartnerEventRepository {
    private val store = ConcurrentHashMap<String, PartnerEvent>()

    override fun existsByEventId(eventId: String): Boolean = store.containsKey(eventId)

    override fun save(event: PartnerEvent): Boolean = store.putIfAbsent(event.eventId, event) == null
}
