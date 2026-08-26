package io.snapplay.channel.application.port.output

import io.snapplay.channel.domain.Channel
import io.snapplay.common.PageResult
import java.util.UUID

data class CreateChannelInput(
    val organizationId: UUID,
    val channelKey: String,
    val displayName: String,
)

interface ChannelRepository {
    fun findAll(
        organizationId: UUID,
        limit: Int,
        cursor: String?,
    ): PageResult<Channel>

    fun create(input: CreateChannelInput): Channel
}
