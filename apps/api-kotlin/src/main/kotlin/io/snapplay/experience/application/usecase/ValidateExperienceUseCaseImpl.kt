package io.snapplay.experience.application.usecase

import io.snapplay.common.NotFoundException
import io.snapplay.connection.application.port.output.ConnectionRepository
import io.snapplay.connection.domain.Capability
import io.snapplay.connection.domain.ConnectionStatus
import io.snapplay.experience.application.port.input.ValidateExperienceUseCase
import io.snapplay.experience.application.port.input.ValidationIssue
import io.snapplay.experience.application.port.input.ValidationResult
import io.snapplay.experience.application.port.output.CatalogValidationPort
import io.snapplay.experience.application.port.output.ExperienceRepository
import io.snapplay.experience.domain.ExperienceStatus
import io.snapplay.identity.RequestPrincipal
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ValidateExperienceUseCaseImpl(
    private val experienceRepository: ExperienceRepository,
    private val connectionRepository: ConnectionRepository,
    private val catalogValidationPort: CatalogValidationPort,
) : ValidateExperienceUseCase {
    override fun validate(
        id: UUID,
        principal: RequestPrincipal,
    ): ValidationResult {
        val errors = mutableListOf<ValidationIssue>()
        val warnings = mutableListOf<ValidationIssue>()

        val experience =
            experienceRepository.findById(principal.organizationId, id)
                ?: throw NotFoundException("Experience $id not found")

        // Status check
        if (experience.status != ExperienceStatus.DRAFT && experience.status != ExperienceStatus.IN_REVIEW) {
            errors.add(ValidationIssue("status", "INVALID_STATUS", "Experience is ${experience.status}, expected DRAFT or IN_REVIEW"))
        }

        // Connection checks
        val connection = connectionRepository.find(principal.organizationId, experience.connectionId)
        if (connection == null) {
            errors.add(ValidationIssue("connectionId", "CONNECTION_NOT_FOUND", "Connection ${experience.connectionId} not found"))
            return ValidationResult(valid = false, errors = errors, warnings = warnings)
        }

        if (connection.status != ConnectionStatus.ACTIVE) {
            errors.add(ValidationIssue("connectionId", "CONNECTION_INACTIVE", "Connection is ${connection.status}, expected ACTIVE"))
        }

        if (connection.killedAt != null) {
            errors.add(ValidationIssue("connectionId", "CONNECTION_KILLED", "Connection has been killed"))
        }

        if (Capability.STORE_CATEGORY_DEEPLINK !in connection.capabilities) {
            errors.add(ValidationIssue("connectionId", "MISSING_CAPABILITY", "Connection does not support STORE_CATEGORY_DEEPLINK"))
        }

        // Contract check
        val contractId = experienceRepository.findActiveContractId(principal.organizationId)
        if (contractId == null) {
            errors.add(ValidationIssue("contract", "NO_ACTIVE_CONTRACT", "No active commercial contract for this organization"))
        }

        // Destination: store validation
        val destination = experience.destination
        if (destination == null || destination.providerStoreId.isBlank()) {
            errors.add(ValidationIssue("destination.providerStoreId", "STORE_MISSING", "Provider store ID is required"))
            return ValidationResult(valid = errors.isEmpty(), errors = errors, warnings = warnings)
        }

        val storeCheck = catalogValidationPort.checkStore(experience.connectionId, destination.providerStoreId)

        if (!storeCheck.exists) {
            errors.add(ValidationIssue("destination.providerStoreId", "STORE_NOT_FOUND", "Store '${destination.providerStoreId}' not found in catalog"))
        } else {
            if (!storeCheck.active) {
                errors.add(ValidationIssue("destination.providerStoreId", "STORE_INACTIVE", "Store '${destination.providerStoreId}' is inactive"))
            }
            if (storeCheck.stale) {
                warnings.add(
                    ValidationIssue("destination.providerStoreId", "STORE_STALE", "Store '${destination.providerStoreId}' has not been synced recently"),
                )
            }
        }

        // Destination: category validation
        if (destination.providerCategoryId.isBlank()) {
            errors.add(ValidationIssue("destination.providerCategoryId", "CATEGORY_MISSING", "Provider category ID is required"))
        } else if (storeCheck.exists && storeCheck.internalId != null) {
            val catCheck =
                catalogValidationPort.checkCategoryForStore(
                    experience.connectionId,
                    destination.providerCategoryId,
                    storeCheck.internalId,
                )

            if (!catCheck.exists) {
                errors.add(
                    ValidationIssue(
                        "destination.providerCategoryId",
                        "CATEGORY_NOT_FOUND",
                        "Category '${destination.providerCategoryId}' not found in catalog",
                    ),
                )
            } else {
                if (!catCheck.active) {
                    errors.add(
                        ValidationIssue("destination.providerCategoryId", "CATEGORY_INACTIVE", "Category '${destination.providerCategoryId}' is inactive"),
                    )
                }
                if (catCheck.stale) {
                    warnings.add(
                        ValidationIssue(
                            "destination.providerCategoryId",
                            "CATEGORY_STALE",
                            "Category '${destination.providerCategoryId}' has not been synced recently",
                        ),
                    )
                }
                if (!catCheck.linkedToStore) {
                    errors.add(
                        ValidationIssue(
                            "destination.providerCategoryId",
                            "CATEGORY_NOT_LINKED",
                            "Category '${destination.providerCategoryId}' is not available for store '${destination.providerStoreId}'",
                        ),
                    )
                }
            }
        }

        return ValidationResult(valid = errors.isEmpty(), errors = errors, warnings = warnings)
    }
}
