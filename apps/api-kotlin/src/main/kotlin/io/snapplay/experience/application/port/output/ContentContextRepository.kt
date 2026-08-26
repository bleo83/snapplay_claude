package io.snapplay.experience.application.port.output

import io.snapplay.common.PageResult
import io.snapplay.experience.domain.ContentContext
import io.snapplay.experience.domain.ContentContextType
import java.util.UUID

data class CreateContentContextInput(
    val organizationId: UUID,
    val channelId: UUID,
    val externalRef: String,
    val contextType: ContentContextType,
    val title: String,
)

interface ContentContextRepository {
    fun findAll(
        organizationId: UUID,
        channelId: UUID?,
        limit: Int,
        cursor: String?,
    ): PageResult<ContentContext>

    fun create(input: CreateContentContextInput): ContentContext
}
