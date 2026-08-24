package io.snapplay.links.infrastructure.web

import io.snapplay.identity.PrincipalResolver
import io.snapplay.links.application.port.input.ListSmartLinksUseCase
import io.snapplay.links.domain.SmartLink
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class SmartLinkListResponse(
    val items: List<SmartLink>,
    val nextCursor: String?,
)

@RestController
@RequestMapping("/v1/smart-links")
class SmartLinkController(
    private val principalResolver: PrincipalResolver,
    private val listSmartLinksUseCase: ListSmartLinksUseCase,
) {
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam cursor: String? = null,
    ): SmartLinkListResponse {
        val safeLimit = limit.coerceIn(1, 200)
        val principal = principalResolver.resolve()
        val result = listSmartLinksUseCase.list(principal, safeLimit, cursor)
        return SmartLinkListResponse(items = result.items, nextCursor = result.nextCursor)
    }
}
