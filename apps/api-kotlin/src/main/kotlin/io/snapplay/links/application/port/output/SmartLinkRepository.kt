package io.snapplay.links.application.port.output

import io.snapplay.common.PageResult
import io.snapplay.links.domain.SmartLink
import java.util.UUID

interface SmartLinkRepository {
    fun findAll(
        organizationId: UUID,
        limit: Int,
        cursor: String?,
    ): PageResult<SmartLink>
}
