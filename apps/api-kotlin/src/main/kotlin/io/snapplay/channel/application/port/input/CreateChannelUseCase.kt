package io.snapplay.channel.application.port.input

import io.snapplay.channel.domain.Channel
import io.snapplay.identity.RequestPrincipal

data class CreateChannelCommand(
    val channelKey: String,
    val displayName: String,
)

fun interface CreateChannelUseCase {
    fun create(
        command: CreateChannelCommand,
        principal: RequestPrincipal,
    ): Channel
}
