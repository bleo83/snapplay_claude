package io.snapplay.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory sliding-window rate limiter for the public `/r/` resolver path.
 * Limits per IP address to prevent automated scanning.
 *
 * For multi-instance deployments, this should be replaced with Redis-backed rate limiting.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class RateLimitFilter(
    private val props: SnapPlayProperties,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(RateLimitFilter::class.java)
    private val windows = ConcurrentHashMap<String, RateWindow>()

    companion object {
        private const val WINDOW_SECONDS = 60L
        private const val CLEANUP_THRESHOLD = 10_000
    }

    fun reset() = windows.clear()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !request.requestURI.startsWith("/r/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val ip = clientIp(request)
        val key = ip

        val window =
            windows.compute(key) { _, existing ->
                val now = Instant.now()
                if (existing == null || existing.isExpired(now)) {
                    RateWindow(now, AtomicInteger(1))
                } else {
                    existing.count.incrementAndGet()
                    existing
                }
            }!!

        if (window.count.get() > props.resolverRateLimitPerMinute) {
            log.warn("Rate limit exceeded for IP={} count={}", ip, window.count.get())
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.setHeader("Retry-After", "60")
            return
        }

        // Periodic cleanup of stale entries
        if (windows.size > CLEANUP_THRESHOLD) {
            val now = Instant.now()
            windows.entries.removeIf { it.value.isExpired(now) }
        }

        filterChain.doFilter(request, response)
    }

    private fun clientIp(request: HttpServletRequest): String =
        request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: request.remoteAddr

    private data class RateWindow(val start: Instant, val count: AtomicInteger) {
        fun isExpired(now: Instant): Boolean = now.epochSecond - start.epochSecond > WINDOW_SECONDS
    }
}
