package io.snapplay.links.infrastructure.persistence

import io.snapplay.config.SnapPlayProperties
import io.snapplay.links.application.port.output.SmartLinkRepository
import io.snapplay.links.domain.SmartLink
import io.snapplay.links.domain.SmartLinkStatus
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoSmartLinkRepository(
    private val props: SnapPlayProperties,
) : SmartLinkRepository {
    override fun findAll(organizationId: UUID): List<SmartLink> =
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
        )
}
