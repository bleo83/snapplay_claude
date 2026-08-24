package io.snapplay.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class RequestIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = UUID.randomUUID().toString()
        request.setAttribute("snapplay.requestId", requestId)
        response.setHeader("X-Request-Id", requestId)
        filterChain.doFilter(request, response)
    }
}
