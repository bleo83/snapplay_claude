package io.snapplay.experience.domain

import java.time.Instant
import java.util.UUID

enum class ContentContextType {
    CHANNEL,
    MOVIE,
    SERIES,
    EPISODE,
    LIVE_EVENT,
    COURSE,
    LESSON,
    EDITORIAL,
}

data class ContentContext(
    val id: UUID,
    val channelId: UUID,
    val channelDisplayName: String,
    val externalRef: String,
    val contextType: ContentContextType,
    val title: String,
    val createdAt: Instant,
)
