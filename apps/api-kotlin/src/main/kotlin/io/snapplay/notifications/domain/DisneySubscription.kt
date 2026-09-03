package io.snapplay.notifications.domain

import java.util.UUID

data class DisneySubscription(
    val id: UUID,
    val connectionId: UUID,
    val webhookUrl: String,
    val webhookSecret: String,
)
