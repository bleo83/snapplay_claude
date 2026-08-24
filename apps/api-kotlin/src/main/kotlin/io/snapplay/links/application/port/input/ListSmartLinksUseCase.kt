package io.snapplay.links.application.port.input

import io.snapplay.common.PageResult
import io.snapplay.identity.RequestPrincipal
import io.snapplay.links.domain.SmartLink

interface ListSmartLinksUseCase {
    fun list(
        principal: RequestPrincipal,
        limit: Int,
        cursor: String?,
    ): PageResult<SmartLink>
}
