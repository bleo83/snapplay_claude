package io.snapplay.experience.application.port.input

import io.snapplay.common.PageResult
import io.snapplay.experience.domain.Experience
import io.snapplay.identity.RequestPrincipal

interface ListExperiencesUseCase {
    fun list(
        principal: RequestPrincipal,
        limit: Int,
        cursor: String?,
    ): PageResult<Experience>
}
