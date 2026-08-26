package io.snapplay.connection.infrastructure.web

import io.snapplay.common.PageResult
import io.snapplay.common.ValidationException
import io.snapplay.connection.application.port.input.CreateConnectionCommand
import io.snapplay.connection.application.port.input.CreateConnectionUseCase
import io.snapplay.connection.application.port.input.GetConnectionUseCase
import io.snapplay.connection.application.port.input.ListConnectionsUseCase
import io.snapplay.connection.application.port.input.UpdateConnectionCommand
import io.snapplay.connection.application.port.input.UpdateConnectionUseCase
import io.snapplay.connection.domain.Capability
import io.snapplay.connection.domain.Connection
import io.snapplay.connection.domain.ConnectionStatus
import io.snapplay.connection.domain.Environment
import io.snapplay.identity.PrincipalResolver
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class CreateConnectionRequest(
    @field:Size(min = 1, max = 200) val name: String,
    val commerceOrganizationId: UUID,
    @field:NotBlank val connectorKey: String,
    val environment: Environment,
    @field:NotEmpty val territories: List<
        @Pattern(regexp = "^[A-Z]{2}$", message = "Must be a 2-letter ISO country code")
        String,
        >,
    val capabilities: List<String>,
    val dataSharingPolicyId: UUID,
)

data class UpdateConnectionRequest(
    val territories: List<
        @Pattern(regexp = "^[A-Z]{2}$", message = "Must be a 2-letter ISO country code")
        String,
        >?,
    val capabilities: List<String>?,
    val status: ConnectionStatus?,
)

@RestController
@RequestMapping("/v1/connections")
class ConnectionController(
    private val principalResolver: PrincipalResolver,
    private val createConnectionUseCase: CreateConnectionUseCase,
    private val getConnectionUseCase: GetConnectionUseCase,
    private val listConnectionsUseCase: ListConnectionsUseCase,
    private val updateConnectionUseCase: UpdateConnectionUseCase,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CreateConnectionRequest,
    ): Connection =
        createConnectionUseCase.create(
            CreateConnectionCommand(
                name = request.name,
                commerceOrganizationId = request.commerceOrganizationId,
                connectorKey = request.connectorKey,
                environment = request.environment,
                territories = request.territories,
                capabilities = parseCapabilities(request.capabilities),
                dataSharingPolicyId = request.dataSharingPolicyId,
            ),
            principalResolver.resolve(),
        )

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) cursor: String?,
    ): PageResult<Connection> =
        listConnectionsUseCase.list(
            principalResolver.resolve(),
            limit.coerceIn(1, 200),
            cursor,
        )

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): Connection = getConnectionUseCase.get(principalResolver.resolve(), id)

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateConnectionRequest,
    ): Connection =
        updateConnectionUseCase.update(
            UpdateConnectionCommand(
                territories = request.territories,
                capabilities = request.capabilities?.let { parseCapabilities(it) },
                status = request.status,
            ),
            principalResolver.resolve(),
            id,
        )

    private fun parseCapabilities(names: List<String>): Set<Capability> =
        names.mapTo(mutableSetOf()) { name ->
            Capability.entries.firstOrNull { it.name == name }
                ?: throw ValidationException(
                    "Unknown capability: '$name'. Valid values: ${Capability.entries.joinToString { it.name }}",
                )
        }
}
