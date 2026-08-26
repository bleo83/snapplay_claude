package io.snapplay.experience.application.port.output

import io.snapplay.common.PageResult
import io.snapplay.experience.domain.CommerceDestination
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
    val territory: String,
    val destination: CommerceDestination,
    val startsAt: Instant,
    val endsAt: Instant?,
)

interface ExperienceRepository {
    fun findAll(
        organizationId: UUID,
        limit: Int,
        cursor: String?,
    ): PageResult<Experience>

    fun findById(
        organizationId: UUID,
        id: UUID,
    ): Experience?

    fun findContentContext(
        organizationId: UUID,
        contextTitle: String,
    ): ContentContextResult?

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

    fun publish(
        organizationId: UUID,
        actorId: UUID,
        id: UUID,
    ): Experience

    fun pause(
        organizationId: UUID,
        actorId: UUID,
        id: UUID,
    ): Experience

    fun retire(
        organizationId: UUID,
        actorId: UUID,
        id: UUID,
    ): Experience

    fun clone(
        organizationId: UUID,
        actorId: UUID,
        id: UUID,
    ): Experience
}
