package io.snapplay.links.application.usecase

import io.snapplay.common.PageResult
import io.snapplay.identity.RequestPrincipal
import io.snapplay.links.application.port.input.ListSmartLinksUseCase
import io.snapplay.links.application.port.output.SmartLinkRepository
import io.snapplay.links.domain.SmartLink
import org.springframework.stereotype.Service

@Service
class ListSmartLinksUseCaseImpl(
    private val smartLinkRepository: SmartLinkRepository,
) : ListSmartLinksUseCase {
    override fun list(
        principal: RequestPrincipal,
        limit: Int,
        cursor: String?,
    ): PageResult<SmartLink> = smartLinkRepository.findAll(principal.organizationId, limit, cursor)
}
