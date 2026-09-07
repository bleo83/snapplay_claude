package io.snapplay.experience.application.port.input

import io.snapplay.identity.RequestPrincipal
import java.util.UUID

data class ValidationIssue(
    val field: String,
    val code: String,
    val message: String,
)

data class ValidationResult(
    val valid: Boolean,
    val errors: List<ValidationIssue>,
    val warnings: List<ValidationIssue>,
)

interface ValidateExperienceUseCase {
    /** Returns structured errors and warnings without modifying state. */
    fun validate(
        id: UUID,
        principal: RequestPrincipal,
    ): ValidationResult
}
