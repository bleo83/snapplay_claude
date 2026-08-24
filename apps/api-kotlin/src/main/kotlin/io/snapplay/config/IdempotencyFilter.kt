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
            jdbc.update(
                "UPDATE idempotency_keys SET status_code = ?, response = ? WHERE key = ? AND endpoint = ?",
                statusCode,
                body,
                key,
                endpoint,
            )
            wrapped.copyBodyToResponse()
        }
    }

    private data class CachedResponse(val statusCode: Int?, val body: String?)

    private fun findExisting(
        key: String,
        endpoint: String,
    ): CachedResponse? =
        jdbc
            .query(
                """
                SELECT status_code, response FROM idempotency_keys
                WHERE key = ? AND endpoint = ?
                  AND created_at > NOW() - make_interval(hours => ?)
                """.trimIndent(),
                { rs, _ ->
                    val code = rs.getInt("status_code").takeIf { !rs.wasNull() }
                    CachedResponse(code, rs.getString("response"))
                },
                key,
                endpoint,
                props.idempotencyTtlHours,
            ).firstOrNull()

    private fun replayOrConflict(
        cached: CachedResponse,
        response: HttpServletResponse,
    ) {
        if (cached.statusCode == null) {
            // In-progress: another request with this key is being processed
            sendConflict(response)
            return
        }
        response.status = cached.statusCode
        response.contentType = "application/json"
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
}
