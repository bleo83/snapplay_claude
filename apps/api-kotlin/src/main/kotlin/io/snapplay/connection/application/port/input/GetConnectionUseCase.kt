package io.snapplay.connection.application.port.input

import io.snapplay.connection.domain.Connection
import io.snapplay.identity.RequestPrincipal
import java.util.UUID

fun interface GetConnectionUseCase {
    fun get(
        principal: RequestPrincipal,
        id: UUID,
    ): Connection
}
