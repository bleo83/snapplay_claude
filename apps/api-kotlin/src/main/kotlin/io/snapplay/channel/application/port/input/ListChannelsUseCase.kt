package io.snapplay.channel.application.port.input

import io.snapplay.channel.domain.Channel
import io.snapplay.common.PageResult
import io.snapplay.identity.RequestPrincipal

fun interface ListChannelsUseCase {
    fun list(
        principal: RequestPrincipal,
        limit: Int,
        cursor: String?,
    ): PageResult<Channel>
}
