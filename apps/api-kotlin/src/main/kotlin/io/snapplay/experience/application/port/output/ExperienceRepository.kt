package io.snapplay.experience.application.port.output

import io.snapplay.experience.domain.Experience
import io.snapplay.experience.domain.HandoffMode
import java.time.Instant
import java.util.UUID

data class CreateExperienceInput(
    val name: String,
    val contextTitle: String,
    val handoffMode: HandoffMode,
    val productCount: Int,
    val startsAt: Instant,
    val endsAt: Instant?,
)

interface ExperienceRepository {
    fun findAll(organizationId: UUID): List<Experience>
    fun create(organizationId: UUID, actorId: UUID, input: CreateExperienceInput): Experience
}
