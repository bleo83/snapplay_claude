package io.snapplay.experience.application.port.input

import io.snapplay.experience.domain.Experience
import io.snapplay.identity.RequestPrincipal
import java.util.UUID

fun interface CloneExperienceUseCase {
    fun clone(
        id: UUID,
        principal: RequestPrincipal,
    ): Experience
}
