package io.snapplay.experience.application.port.output

import io.snapplay.experience.domain.Experience
import io.snapplay.experience.domain.HandoffMode
import java.time.Instant
import java.util.UUID

data class ContentContextResult(
    val id: UUID,
    val channelDisplayName: String,
)

data class CreateExperienceInput(
    val name: String,
    val contextId: UUID,
    val contextTitle: String,
    val channelDisplayName: String,
    val connectionId: UUID,
    val contractId: UUID,
    val productIds: List<UUID>,
    val handoffMode: HandoffMode,
    val startsAt: Instant,
    val endsAt: Instant?,
)

interface ExperienceRepository {
    fun findAll(organizationId: UUID): List<Experience>

    fun findContentContext(
        organizationId: UUID,
        contextTitle: String,
    ): ContentContextResult?

    fun findActiveConnectionId(organizationId: UUID): UUID?

    fun findActiveContractId(organizationId: UUID): UUID?

    fun findActiveProductIds(
        connectionId: UUID,
        limit: Int,
    ): List<UUID>

    fun create(
        organizationId: UUID,
        actorId: UUID,
        input: CreateExperienceInput,
    ): Experience
}
