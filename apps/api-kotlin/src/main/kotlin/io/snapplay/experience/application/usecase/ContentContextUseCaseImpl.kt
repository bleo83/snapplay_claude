package io.snapplay.experience.application.usecase

import io.snapplay.common.PageResult
import io.snapplay.experience.application.port.input.CreateContentContextCommand
import io.snapplay.experience.application.port.input.CreateContentContextUseCase
import io.snapplay.experience.application.port.input.ListContentContextsUseCase
import io.snapplay.experience.application.port.output.ContentContextRepository
import io.snapplay.experience.application.port.output.CreateContentContextInput
import io.snapplay.experience.domain.ContentContext
import io.snapplay.identity.OrgRole
import io.snapplay.identity.RequestPrincipal
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CreateContentContextUseCaseImpl(
    private val contentContextRepository: ContentContextRepository,
) : CreateContentContextUseCase {
    override fun create(
        command: CreateContentContextCommand,
        principal: RequestPrincipal,
    ): ContentContext {
        principal.requireAnyRole(OrgRole.ORGANIZATION_ADMIN, OrgRole.CONTENT_MANAGER)
        return contentContextRepository.create(
            CreateContentContextInput(
                organizationId = principal.organizationId,
                channelId = command.channelId,
                externalRef = command.externalRef,
                contextType = command.contextType,
                title = command.title,
            ),
        )
    }
}

@Service
class ListContentContextsUseCaseImpl(
    private val contentContextRepository: ContentContextRepository,
) : ListContentContextsUseCase {
    override fun list(
        principal: RequestPrincipal,
        channelId: UUID?,
        limit: Int,
        cursor: String?,
    ): PageResult<ContentContext> = contentContextRepository.findAll(principal.organizationId, channelId, limit, cursor)
}
