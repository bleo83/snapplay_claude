package io.snapplay.experience.application.port.input

import io.snapplay.experience.domain.Experience
import io.snapplay.identity.RequestPrincipal
import java.util.UUID

fun interface RetireExperienceUseCase {
    fun retire(
        id: UUID,
        principal: RequestPrincipal,
    ): Experience
}
