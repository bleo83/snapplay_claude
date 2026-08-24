package io.snapplay.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingResponseWrapper

@Component
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class IdempotencyFilter(
    private val jdbc: JdbcTemplate,
    private val props: SnapPlayProperties,
) : OncePerRequestFilter() {
    companion object {
        private val IDEMPOTENT_METHODS = setOf("POST", "PATCH")
        private val IDEMPOTENT_PATHS = setOf("/v1/experiences", "/v1/partner/events")

        // UUID (8-4-4-4-12 hex) or ULID (26 alphanumeric chars)
        private val VALID_KEY =
            Regex(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}" +
                    "|[0-9A-Za-z]{26}",
            )

        // In-progress rows older than this are considered abandoned (e.g. JVM crash mid-request)
        private const val ABANDONED_AFTER_SECONDS = 60L
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.method !in IDEMPOTENT_METHODS ||
            request.requestURI !in IDEMPOTENT_PATHS ||
            request.getHeader("Idempotency-Key").isNullOrBlank()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val key = request.getHeader("Idempotency-Key")

        if (!VALID_KEY.matches(key)) {
            sendBadRequest(response, "Idempotency-Key must be a UUID or ULID")
            return
        }

        val endpoint = "${request.method}:${request.requestURI}"

        val existing = findExisting(key, endpoint)
        if (existing != null) {
            replayOrConflict(existing, response)
            return
        }

        if (!tryInsertInProgress(key, endpoint)) {
            sendConflict(response)
            return
        }

        val wrapped = ContentCachingResponseWrapper(response)
        try {
            chain.doFilter(request, wrapped)
        } finally {
            val statusCode = wrapped.status
            val body = String(wrapped.contentAsByteArray, Charsets.UTF_8)
            val contentType = wrapped.contentType
            jdbc.update(
                "UPDATE idempotency_keys SET status_code = ?, response = ?, content_type = ? WHERE key = ? AND endpoint = ?",
                statusCode,
                body,
                contentType,
                key,
                endpoint,
            )
            wrapped.copyBodyToResponse()
        }
    }

    private data class CachedResponse(
        val statusCode: Int?,
        val body: String?,
        val contentType: String?,
        val abandoned: Boolean,
    )

    private fun findExisting(
        key: String,
        endpoint: String,
    ): CachedResponse? {
        val found =
            jdbc
                .query(
                    """
                    SELECT status_code, response, content_type,
                           (status_code IS NULL AND created_at < NOW() - make_interval(secs => ?)) AS abandoned
                    FROM idempotency_keys
                    WHERE key = ? AND endpoint = ?
                      AND created_at > NOW() - make_interval(hours => ?)
                    """.trimIndent(),
                    { rs, _ ->
                        val code = rs.getInt("status_code").takeIf { !rs.wasNull() }
                        CachedResponse(code, rs.getString("response"), rs.getString("content_type"), rs.getBoolean("abandoned"))
                    },
                    ABANDONED_AFTER_SECONDS,
                    key,
                    endpoint,
                    props.idempotencyTtlHours,
                ).firstOrNull() ?: return null

        if (found.abandoned) {
            // Delete the stuck row so the new request can proceed normally
            jdbc.update("DELETE FROM idempotency_keys WHERE key = ? AND endpoint = ?", key, endpoint)
            return null
        }

        return found
    }

    private fun replayOrConflict(
        cached: CachedResponse,
        response: HttpServletResponse,
    ) {
        if (cached.statusCode == null) {
            sendConflict(response)
            return
        }
        response.status = cached.statusCode
        response.contentType = cached.contentType ?: "application/json"
        response.writer.write(cached.body ?: "")
    }

    private fun tryInsertInProgress(
        key: String,
        endpoint: String,
    ): Boolean =
        try {
            jdbc.update(
                "INSERT INTO idempotency_keys (key, endpoint) VALUES (?, ?)",
                key,
                endpoint,
            )
            true
        } catch (_: DuplicateKeyException) {
            false
        }

    private fun sendConflict(response: HttpServletResponse) {
        response.status = HttpStatus.CONFLICT.value()
        response.contentType = "application/json"
        response.writer.write("""{"error":"A request with this Idempotency-Key is already in progress"}""")
    }

    private fun sendBadRequest(
        response: HttpServletResponse,
        message: String,
    ) {
        response.status = HttpStatus.BAD_REQUEST.value()
        response.contentType = "application/json"
        response.writer.write("""{"error":"$message"}""")
    }
}
