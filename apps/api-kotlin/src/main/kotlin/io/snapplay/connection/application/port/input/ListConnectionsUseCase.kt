package io.snapplay.connection.application.port.input

import io.snapplay.common.PageResult
import io.snapplay.connection.domain.Connection
import io.snapplay.identity.RequestPrincipal

fun interface ListConnectionsUseCase {
    fun list(
        principal: RequestPrincipal,
        limit: Int,
        cursor: String?,
    ): PageResult<Connection>
}
