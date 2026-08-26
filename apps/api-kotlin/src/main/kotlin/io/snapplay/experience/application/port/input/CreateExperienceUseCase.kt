package io.snapplay.experience.application.port.input

import io.snapplay.experience.domain.CommerceDestination
import io.snapplay.experience.domain.Experience
import io.snapplay.experience.domain.HandoffMode
import io.snapplay.identity.RequestPrincipal
import java.time.Instant
import java.util.UUID

data class CreateExperienceCommand(
    val name: String,
    val contextTitle: String,
    val connectionId: UUID,
    val territory: String,
    val destination: CommerceDestination,
    val handoffMode: HandoffMode,
    val productCount: Int,
    val startsAt: Instant,
    val endsAt: Instant?,
)

fun interface CreateExperienceUseCase {
    fun create(
        command: CreateExperienceCommand,
        principal: RequestPrincipal,
    ): Experience
}
