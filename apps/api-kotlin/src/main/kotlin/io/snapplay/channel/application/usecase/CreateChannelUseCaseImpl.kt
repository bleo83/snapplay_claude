package io.snapplay.channel.application.usecase

import io.snapplay.channel.application.port.input.CreateChannelCommand
import io.snapplay.channel.application.port.input.CreateChannelUseCase
import io.snapplay.channel.application.port.output.ChannelRepository
import io.snapplay.channel.application.port.output.CreateChannelInput
import io.snapplay.channel.domain.Channel
import io.snapplay.identity.OrgRole
import io.snapplay.identity.RequestPrincipal
import org.springframework.stereotype.Service

@Service
class CreateChannelUseCaseImpl(
    private val channelRepository: ChannelRepository,
) : CreateChannelUseCase {
    override fun create(
        command: CreateChannelCommand,
        principal: RequestPrincipal,
    ): Channel {
        principal.requireAnyRole(OrgRole.ORGANIZATION_ADMIN, OrgRole.CONTENT_MANAGER)
        return channelRepository.create(
            CreateChannelInput(
                organizationId = principal.organizationId,
                channelKey = command.channelKey,
                displayName = command.displayName,
            ),
        )
    }
}
