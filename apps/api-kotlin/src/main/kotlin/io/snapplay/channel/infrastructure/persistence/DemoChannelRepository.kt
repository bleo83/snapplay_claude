package io.snapplay.channel.infrastructure.persistence

import io.snapplay.channel.application.port.output.ChannelRepository
import io.snapplay.channel.application.port.output.CreateChannelInput
import io.snapplay.channel.domain.Channel
import io.snapplay.channel.domain.ChannelStatus
import io.snapplay.common.PageResult
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

private val DEMO_ORG_ID = UUID.fromString("50d2d7eb-c8fd-42df-bbb0-0ea0e2271090")

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoChannelRepository : ChannelRepository {
    private val channels: MutableList<Channel> =
        CopyOnWriteArrayList(
            listOf(
                Channel(
                    id = UUID.fromString("11111111-1111-1111-1111-000000000001"),
                    channelKey = "disney-plus",
                    displayName = "Disney+",
                    status = ChannelStatus.ACTIVE,
                    createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                ),
                Channel(
                    id = UUID.fromString("11111111-1111-1111-1111-000000000002"),
                    channelKey = "espn",
                    displayName = "ESPN",
                    status = ChannelStatus.ACTIVE,
                    createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                ),
            ),
        )

    override fun findAll(
        organizationId: UUID,
        limit: Int,
        cursor: String?,
    ): PageResult<Channel> = PageResult(items = channels.take(limit), nextCursor = null)

    override fun create(input: CreateChannelInput): Channel {
        val channel =
            Channel(
                id = UUID.randomUUID(),
                channelKey = input.channelKey,
                displayName = input.displayName,
                status = ChannelStatus.ACTIVE,
                createdAt = Instant.now(),
            )
        channels.add(channel)
        return channel
    }
}
