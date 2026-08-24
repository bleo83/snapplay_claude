package io.snapplay.experience.infrastructure.persistence

import io.snapplay.common.PageResult
import io.snapplay.experience.application.port.output.ContentContextResult
import io.snapplay.experience.application.port.output.CreateExperienceInput
import io.snapplay.experience.application.port.output.ExperienceRepository
import io.snapplay.experience.domain.Experience
import io.snapplay.experience.domain.ExperienceStatus
import io.snapplay.experience.domain.HandoffMode
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoExperienceRepository : ExperienceRepository {
    private val experiences: MutableList<Experience> =
        CopyOnWriteArrayList(
            listOf(
                Experience(
                    id = UUID.fromString("184a63fe-4420-46de-9894-b16110364263"),
                    name = "Toy Story Movie Night",
                    contextTitle = "Toy Story",
                    channel = "Disney+",
                    version = 3,
                    status = ExperienceStatus.PUBLISHED,
                    handoffMode = HandoffMode.DYNAMIC_STOREFRONT,
                    productCount = 4,
                    startsAt = Instant.parse("2026-08-01T00:00:00Z"),
                    endsAt = Instant.parse("2026-12-01T00:00:00Z"),
                ),
                Experience(
                    id = UUID.fromString("7351e94e-a88b-46bd-8f3c-a6df37c13483"),
                    name = "Moana Family Night",
                    contextTitle = "Moana",
                    channel = "Disney+",
                    version = 2,
                    status = ExperienceStatus.PUBLISHED,
                    handoffMode = HandoffMode.DYNAMIC_STOREFRONT,
                    productCount = 3,
                    startsAt = Instant.parse("2026-08-10T00:00:00Z"),
                    endsAt = null,
                ),
                Experience(
                    id = UUID.fromString("1d79abf3-33e1-427c-aeba-c27f607f6f71"),
                    name = "ESPN Match Night",
                    contextTitle = "ESPN Live",
                    channel = "ESPN",
                    version = 1,
                    status = ExperienceStatus.IN_REVIEW,
                    handoffMode = HandoffMode.STORE_DEEPLINK,
                    productCount = 5,
                    startsAt = Instant.parse("2026-09-01T00:00:00Z"),
                    endsAt = null,
                ),
                Experience(
                    id = UUID.fromString("53d116c9-e676-4c49-9a2b-371d0b4d5c1a"),
                    name = "Hulu Classics",
                    contextTitle = "Hulu",
                    channel = "Hulu",
                    version = 1,
                    status = ExperienceStatus.DRAFT,
                    handoffMode = HandoffMode.STORE_DEEPLINK,
                    productCount = 2,
                    startsAt = Instant.parse("2026-10-01T00:00:00Z"),
                    endsAt = null,
                ),
            ),
        )

    override fun findAll(
        organizationId: UUID,
        limit: Int,
        cursor: String?,
    ): PageResult<Experience> = PageResult(items = experiences.take(limit), nextCursor = null)

    // Demo always resolves to the Disney+ channel regardless of contextTitle
    override fun findContentContext(
        organizationId: UUID,
        contextTitle: String,
    ): ContentContextResult =
        ContentContextResult(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            channelDisplayName = "Disney+",
        )

    override fun findActiveConnectionId(organizationId: UUID): UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")

    override fun findActiveContractId(organizationId: UUID): UUID = UUID.fromString("00000000-0000-0000-0000-000000000003")

    override fun findActiveProductIds(
        connectionId: UUID,
        limit: Int,
    ): List<UUID> = emptyList()

    override fun create(
        organizationId: UUID,
        actorId: UUID,
        input: CreateExperienceInput,
    ): Experience {
        val experience =
            Experience(
                id = UUID.randomUUID(),
                name = input.name,
                contextTitle = input.contextTitle,
                channel = input.channelDisplayName,
                version = 1,
                status = ExperienceStatus.DRAFT,
                handoffMode = input.handoffMode,
                productCount = input.productIds.size,
                startsAt = input.startsAt,
                endsAt = input.endsAt,
            )
        experiences.add(0, experience)
        return experience
    }
}
