package io.snapplay.experience.application.port.input

import io.snapplay.experience.domain.Experience
import io.snapplay.identity.RequestPrincipal

interface ListExperiencesUseCase {
    fun list(principal: RequestPrincipal): List<Experience>
}
