package io.snapplay.experience.infrastructure.persistence

import io.snapplay.common.PageResult
import io.snapplay.experience.application.port.output.ContentContextRepository
import io.snapplay.experience.application.port.output.CreateContentContextInput
import io.snapplay.experience.domain.ContentContext
import io.snapplay.experience.domain.ContentContextType
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

private val DEMO_CHANNEL_DISNEY = UUID.fromString("11111111-1111-1111-1111-000000000001")
private val DEMO_CHANNEL_ESPN = UUID.fromString("11111111-1111-1111-1111-000000000002")

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoContentContextRepository : ContentContextRepository {
    private val contexts: MutableList<ContentContext> =
        CopyOnWriteArrayList(
            listOf(
                ContentContext(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    channelId = DEMO_CHANNEL_DISNEY,
                    channelDisplayName = "Disney+",
                    externalRef = "movie:toy-story",
                    contextType = ContentContextType.MOVIE,
                    title = "Toy Story",
                    createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                ),
                ContentContext(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    channelId = DEMO_CHANNEL_DISNEY,
                    channelDisplayName = "Disney+",
                    externalRef = "movie:moana",
                    contextType = ContentContextType.MOVIE,
                    title = "Moana",
                    createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                ),
                ContentContext(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000003"),
                    channelId = DEMO_CHANNEL_ESPN,
                    channelDisplayName = "ESPN",
                    externalRef = "live:espn-ar",
                    contextType = ContentContextType.LIVE_EVENT,
                    title = "ESPN Live",
                    createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                ),
            ),
        )

    override fun findAll(
        organizationId: UUID,
        channelId: UUID?,
        limit: Int,
        cursor: String?,
    ): PageResult<ContentContext> {
        val filtered = if (channelId != null) contexts.filter { it.channelId == channelId } else contexts.toList()
        return PageResult(items = filtered.take(limit), nextCursor = null)
    }

    override fun create(input: CreateContentContextInput): ContentContext {
        val context =
            ContentContext(
                id = UUID.randomUUID(),
                channelId = input.channelId,
                channelDisplayName = "",
                externalRef = input.externalRef,
                contextType = input.contextType,
                title = input.title,
                createdAt = Instant.now(),
            )
        contexts.add(context)
        return context
    }
}
