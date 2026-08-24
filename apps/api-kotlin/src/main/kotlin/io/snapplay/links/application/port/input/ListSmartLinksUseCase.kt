package io.snapplay.links.application.port.input

import io.snapplay.identity.RequestPrincipal
import io.snapplay.links.domain.SmartLink

interface ListSmartLinksUseCase {
    fun list(principal: RequestPrincipal): List<SmartLink>
}
