package io.snapplay.notifications.application.port.output

import io.snapplay.notifications.domain.DisneySubscription
import java.util.UUID

interface DisneySubscriptionRepository {
    fun findByConnectionId(connectionId: UUID): DisneySubscription?
}
