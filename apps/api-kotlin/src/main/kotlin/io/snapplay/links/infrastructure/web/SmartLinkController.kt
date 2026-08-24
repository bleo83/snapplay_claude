package io.snapplay.links.infrastructure.web

import io.snapplay.identity.PrincipalResolver
import io.snapplay.links.application.port.input.ListSmartLinksUseCase
import io.snapplay.links.domain.SmartLink
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class SmartLinkListResponse(
    val items: List<SmartLink>,
)

@RestController
@RequestMapping("/v1/smart-links")
class SmartLinkController(
    private val principalResolver: PrincipalResolver,
    private val listSmartLinksUseCase: ListSmartLinksUseCase,
) {
    @GetMapping
    fun list(): SmartLinkListResponse {
        val principal = principalResolver.resolve()
        return SmartLinkListResponse(listSmartLinksUseCase.list(principal))
    }
}
