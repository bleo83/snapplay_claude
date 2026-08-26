package io.snapplay.experience.application.port.input

import io.snapplay.common.PageResult
import io.snapplay.experience.domain.ContentContext
import io.snapplay.experience.domain.ContentContextType
import io.snapplay.identity.RequestPrincipal
import java.util.UUID

data class CreateContentContextCommand(
    val channelId: UUID,
    val externalRef: String,
    val contextType: ContentContextType,
    val title: String,
)

fun interface CreateContentContextUseCase {
    fun create(
        command: CreateContentContextCommand,
        principal: RequestPrincipal,
    ): ContentContext
}

fun interface ListContentContextsUseCase {
    fun list(
        principal: RequestPrincipal,
        channelId: UUID?,
        limit: Int,
        cursor: String?,
    ): PageResult<ContentContext>
}
