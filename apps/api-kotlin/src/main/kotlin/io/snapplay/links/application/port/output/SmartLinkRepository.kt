package io.snapplay.links.application.port.output

import io.snapplay.common.PageResult
import io.snapplay.links.domain.SmartLink
import java.util.UUID

data class CreateSmartLinkInput(
    val experienceId: UUID,
    val experienceName: String,
    val placementKey: String,
    val shortCode: String,
)

interface SmartLinkRepository {
    fun findAll(
        organizationId: UUID,
        limit: Int,
        cursor: String?,
    ): PageResult<SmartLink>

    fun findByShortCode(
        organizationId: UUID,
        shortCode: String,
    ): SmartLink?

    fun create(
        organizationId: UUID,
        actorId: UUID,
        input: CreateSmartLinkInput,
    ): SmartLink

    fun shortCodeExists(shortCode: String): Boolean
}
