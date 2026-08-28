package io.snapplay.links.application.usecase

import io.snapplay.common.NotFoundException
import io.snapplay.common.ValidationException
import io.snapplay.experience.application.port.output.ExperienceRepository
import io.snapplay.experience.domain.ExperienceStatus
import io.snapplay.identity.OrgRole
import io.snapplay.identity.RequestPrincipal
import io.snapplay.links.application.port.input.CreateSmartLinkCommand
import io.snapplay.links.application.port.input.CreateSmartLinkUseCase
import io.snapplay.links.application.port.output.CreateSmartLinkInput
import io.snapplay.links.application.port.output.SmartLinkRepository
import io.snapplay.links.domain.SmartLink
import org.springframework.stereotype.Service
import java.security.SecureRandom

@Service
class CreateSmartLinkUseCaseImpl(
    private val smartLinkRepository: SmartLinkRepository,
    private val experienceRepository: ExperienceRepository,
) : CreateSmartLinkUseCase {
    private val random = SecureRandom()
    private val base62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    override fun create(
        command: CreateSmartLinkCommand,
        principal: RequestPrincipal,
    ): SmartLink {
        principal.requireAnyRole(OrgRole.ORGANIZATION_ADMIN, OrgRole.CONTENT_MANAGER, OrgRole.PUBLISHER)

        val experience =
            experienceRepository.findById(principal.organizationId, command.experienceId)
                ?: throw NotFoundException("Experience ${command.experienceId} not found")

        if (experience.status != ExperienceStatus.PUBLISHED) {
            throw ValidationException(
                "Cannot create smart link for experience in status ${experience.status}. Expected PUBLISHED.",
            )
        }

        val shortCode = generateUniqueShortCode()

        return smartLinkRepository.create(
            principal.organizationId,
            principal.userId,
            CreateSmartLinkInput(
                experienceId = command.experienceId,
                experienceName = experience.name,
                placementKey = command.placementKey,
                shortCode = shortCode,
            ),
        )
    }

    // Generates a 17-char base62 short code (~101 bits entropy). Retries on collision (extremely rare).
    private fun generateUniqueShortCode(): String {
        repeat(5) {
            val code = (1..17).map { base62[random.nextInt(base62.length)] }.joinToString("")
            if (!smartLinkRepository.shortCodeExists(code)) return code
        }
        throw IllegalStateException("Failed to generate a unique short code after 5 attempts")
    }
}
