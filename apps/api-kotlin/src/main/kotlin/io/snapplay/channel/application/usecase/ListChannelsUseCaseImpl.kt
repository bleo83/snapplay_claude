package io.snapplay.channel.application.usecase

import io.snapplay.channel.application.port.input.ListChannelsUseCase
import io.snapplay.channel.application.port.output.ChannelRepository
import io.snapplay.channel.domain.Channel
import io.snapplay.common.PageResult
import io.snapplay.identity.RequestPrincipal
import org.springframework.stereotype.Service

@Service
class ListChannelsUseCaseImpl(
    private val channelRepository: ChannelRepository,
) : ListChannelsUseCase {
    override fun list(
        principal: RequestPrincipal,
        limit: Int,
        cursor: String?,
    ): PageResult<Channel> = channelRepository.findAll(principal.organizationId, limit, cursor)
}
