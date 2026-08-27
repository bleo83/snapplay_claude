package io.snapplay.links.application.port.input

import io.snapplay.identity.RequestPrincipal
import io.snapplay.links.domain.SmartLink
import java.util.UUID

data class CreateSmartLinkCommand(
    val experienceId: UUID,
    val placementKey: String,
)

fun interface CreateSmartLinkUseCase {
    fun create(
        command: CreateSmartLinkCommand,
        principal: RequestPrincipal,
    ): SmartLink
}
