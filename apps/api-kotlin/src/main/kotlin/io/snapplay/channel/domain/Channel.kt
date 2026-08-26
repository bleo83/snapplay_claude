package io.snapplay.channel.domain

import java.time.Instant
import java.util.UUID

enum class ChannelStatus { ACTIVE, INACTIVE }

data class Channel(
    val id: UUID,
    val channelKey: String,
    val displayName: String,
    val status: ChannelStatus,
    val createdAt: Instant,
)
