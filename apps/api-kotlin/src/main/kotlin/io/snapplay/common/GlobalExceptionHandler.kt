package io.snapplay.common

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.UUID

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    private fun requestId(request: HttpServletRequest): String =
        (request.getAttribute("snapplay.requestId") as? String) ?: UUID.randomUUID().toString()

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(
        ex: NotFoundException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetails> {
        log.debug("Not found: {}", ex.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ProblemDetails(
                type = "https://snapplay.io/problems/not-found",
                title = "Not Found",
                status = 404,
                detail = ex.message ?: "Resource not found",
                requestId = requestId(request),
            ),
        )
    }

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(
        ex: ForbiddenException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetails> {
        log.debug("Forbidden: {}", ex.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ProblemDetails(
                type = "https://snapplay.io/problems/forbidden",
                title = "Forbidden",
                status = 403,
                detail = ex.message ?: "Access denied",
                requestId = requestId(request),
            ),
        )
    }

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(
        ex: UnauthorizedException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetails> {
        log.debug("Unauthorized: {}", ex.message)
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ProblemDetails(
                type = "https://snapplay.io/problems/unauthorized",
                title = "Unauthorized",
                status = 401,
                detail = ex.message ?: "Authentication required",
                requestId = requestId(request),
            ),
        )
    }

    @ExceptionHandler(ValidationException::class)
    fun handleValidation(
        ex: ValidationException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetails> {
        log.debug("Validation error: {}", ex.message)
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ProblemDetails(
                type = "https://snapplay.io/problems/validation-error",
                title = "Validation Error",
                status = 422,
                detail = ex.message ?: "Validation failed",
                requestId = requestId(request),
                errors = ex.errors.ifEmpty { null },
            ),
        )
    }

    @ExceptionHandler(InvalidTransitionException::class)
    fun handleInvalidTransition(
        ex: InvalidTransitionException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetails> {
        log.debug("Invalid transition: {}", ex.message)
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ProblemDetails(
                type = "https://snapplay.io/problems/invalid-transition",
                title = "Invalid State Transition",
                status = 422,
                detail = ex.message ?: "Invalid state transition",
                requestId = requestId(request),
            ),
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetails> {
        val errors =
            ex.bindingResult.fieldErrors.map { fieldError ->
                mapOf(
                    "field" to fieldError.field,
                    "message" to (fieldError.defaultMessage ?: "Invalid value"),
                    "rejected_value" to fieldError.rejectedValue,
                )
            }
        log.debug("Method argument not valid: {} field errors", errors.size)
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ProblemDetails(
                type = "https://snapplay.io/problems/validation-error",
                title = "Validation Error",
                status = 422,
                detail = "Request validation failed",
                requestId = requestId(request),
                errors = errors,
            ),
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetails> {
        log.error("Unhandled exception", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ProblemDetails(
                type = "https://snapplay.io/problems/internal-error",
                title = "Internal Server Error",
                status = 500,
                detail = "An unexpected error occurred",
                requestId = requestId(request),
            ),
        )
    }
}
