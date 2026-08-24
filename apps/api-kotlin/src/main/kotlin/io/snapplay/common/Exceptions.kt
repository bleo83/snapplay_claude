package io.snapplay.common

open class SnapPlayException(
    message: String,
    val statusCode: Int,
) : RuntimeException(message)

class NotFoundException(message: String) : SnapPlayException(message, 404)

class ForbiddenException(message: String) : SnapPlayException(message, 403)

class UnauthorizedException(message: String) : SnapPlayException(message, 401)

class ValidationException(
    message: String,
    val errors: List<Any> = emptyList(),
) : SnapPlayException(message, 422)

class InvalidTransitionException(from: String, to: String) :
    SnapPlayException("Invalid order transition: $from -> $to", 422)
