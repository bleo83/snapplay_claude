package io.snapplay.links.infrastructure.persistence

import io.snapplay.common.PageResult
import io.snapplay.config.SnapPlayProperties
import io.snapplay.links.application.port.output.CreateSmartLinkInput
import io.snapplay.links.application.port.output.SmartLinkRepository
import io.snapplay.links.domain.ResolvedSmartLink
import io.snapplay.links.domain.SmartLink
import io.snapplay.links.domain.SmartLinkStatus
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoSmartLinkRepository(
    private val props: SnapPlayProperties,
    private val demoSmartLinkResolver: DemoSmartLinkResolver,
) : SmartLinkRepository {
    private val links: MutableList<SmartLink> =
        CopyOnWriteArrayList(
            listOf(
                SmartLink(
                    id = UUID.fromString("9ab6732c-093d-4fee-8efa-b5d2b6ec4d1f"),
                    shortCode = "7E1vM2kP9xQ4",
                    url = "${props.publicBaseUrl}/r/7E1vM2kP9xQ4",
                    experienceName = "Toy Story Movie Night",
                    placementKey = "disney-plus.toy-story.endcard",
                    status = SmartLinkStatus.ACTIVE,
                    scans = 18742,
                    conversions = 1248,
                ),
                SmartLink(
                    id = UUID.fromString("f9aeff9c-1016-4d60-8dbe-6464cc1266cf"),
                    shortCode = "4G8zN7bK2mL6",
                    url = "${props.publicBaseUrl}/r/4G8zN7bK2mL6",
                    experienceName = "Moana Family Night",
                    placementKey = "disney-plus.moana.pause",
                    status = SmartLinkStatus.ACTIVE,
                    scans = 9836,
                    conversions = 771,
                ),
            ),
        )

    override fun findAll(
        organizationId: UUID,
        limit: Int,
        cursor: String?,
    ): PageResult<SmartLink> = PageResult(items = links.take(limit), nextCursor = null)

    override fun findByShortCode(
        organizationId: UUID,
        shortCode: String,
    ): SmartLink? = links.firstOrNull { it.shortCode == shortCode }

    override fun create(
        organizationId: UUID,
        actorId: UUID,
        input: CreateSmartLinkInput,
    ): SmartLink {
        val link =
            SmartLink(
                id = UUID.randomUUID(),
                shortCode = input.shortCode,
                url = "${props.publicBaseUrl}/r/${input.shortCode}",
                experienceName = input.experienceName,
                placementKey = input.placementKey,
                status = SmartLinkStatus.ACTIVE,
                scans = 0,
                conversions = 0,
            )
        links.add(0, link)
        demoSmartLinkResolver.registerResolved(
            ResolvedSmartLink(
                smartLinkId = link.id,
                shortCode = link.shortCode,
                experienceVersionId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                connectionId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                providerStoreId = "900000",
                providerCategoryId = "2000",
                handoffMode = "STORE_DEEPLINK",
                territory = "AR",
            ),
        )
        return link
    }

    override fun shortCodeExists(shortCode: String): Boolean = links.any { it.shortCode == shortCode }
}
