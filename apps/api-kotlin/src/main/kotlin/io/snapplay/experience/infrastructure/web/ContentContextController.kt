package io.snapplay.experience.infrastructure.web

import io.snapplay.common.PageResult
import io.snapplay.experience.application.port.input.CreateContentContextCommand
import io.snapplay.experience.application.port.input.CreateContentContextUseCase
import io.snapplay.experience.application.port.input.ListContentContextsUseCase
import io.snapplay.experience.domain.ContentContext
import io.snapplay.experience.domain.ContentContextType
import io.snapplay.identity.PrincipalResolver
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class CreateContentContextRequest(
    val channelId: UUID,
    @field:NotBlank @field:Size(min = 2, max = 200) val externalRef: String,
    val contextType: ContentContextType,
    @field:NotBlank @field:Size(min = 1, max = 200) val title: String,
)

@RestController
@RequestMapping("/v1/content-contexts")
class ContentContextController(
    private val principalResolver: PrincipalResolver,
    private val createContentContextUseCase: CreateContentContextUseCase,
    private val listContentContextsUseCase: ListContentContextsUseCase,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CreateContentContextRequest,
    ): ContentContext =
        createContentContextUseCase.create(
            CreateContentContextCommand(
                channelId = request.channelId,
                externalRef = request.externalRef,
                contextType = request.contextType,
                title = request.title,
            ),
            principalResolver.resolve(),
        )

    @GetMapping
    fun list(
        @RequestParam(required = false) channelId: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) cursor: String?,
    ): PageResult<ContentContext> =
        listContentContextsUseCase.list(
            principalResolver.resolve(),
            channelId,
            limit.coerceIn(1, 200),
            cursor,
        )
}
