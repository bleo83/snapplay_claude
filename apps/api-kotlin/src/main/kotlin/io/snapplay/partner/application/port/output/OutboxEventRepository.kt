package io.snapplay.partner.application.port.output

import io.snapplay.partner.domain.OutboxEvent

interface OutboxEventRepository {
    fun enqueue(event: OutboxEvent)
}
