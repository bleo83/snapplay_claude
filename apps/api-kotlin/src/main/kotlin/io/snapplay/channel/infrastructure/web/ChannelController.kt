package io.snapplay.channel.infrastructure.web

import io.snapplay.channel.application.port.input.CreateChannelCommand
import io.snapplay.channel.application.port.input.CreateChannelUseCase
import io.snapplay.channel.application.port.input.ListChannelsUseCase
import io.snapplay.channel.domain.Channel
import io.snapplay.common.PageResult
import io.snapplay.identity.PrincipalResolver
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class CreateChannelRequest(
    @field:NotBlank
    @field:Pattern(regexp = "^[a-z0-9-]+$", message = "Must be lowercase alphanumeric with hyphens")
    @field:Size(min = 2, max = 80)
    val channelKey: String,
    @field:NotBlank @field:Size(min = 1, max = 160) val displayName: String,
)

@RestController
@RequestMapping("/v1/channels")
class ChannelController(
    private val principalResolver: PrincipalResolver,
    private val createChannelUseCase: CreateChannelUseCase,
    private val listChannelsUseCase: ListChannelsUseCase,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CreateChannelRequest,
    ): Channel =
        createChannelUseCase.create(
            CreateChannelCommand(channelKey = request.channelKey, displayName = request.displayName),
            principalResolver.resolve(),
        )

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) cursor: String?,
    ): PageResult<Channel> =
        listChannelsUseCase.list(
            principalResolver.resolve(),
            limit.coerceIn(1, 200),
            cursor,
        )
}
