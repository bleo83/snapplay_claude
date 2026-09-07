package io.snapplay.links.infrastructure.web

import io.snapplay.config.BotDetector
import io.snapplay.links.application.port.input.ResolveSmartLinkUseCase
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/r")
class QrResolverController(
    private val resolveSmartLinkUseCase: ResolveSmartLinkUseCase,
) {
    private val log = LoggerFactory.getLogger(QrResolverController::class.java)

    companion object {
        // Short codes must be >= 8 chars alphanumeric to resist enumeration
        private val VALID_SHORT_CODE = Regex("^[A-Za-z0-9_-]{8,64}$")
    }

    @GetMapping("/{shortCode}")
    fun resolve(
        @PathVariable shortCode: String,
        @RequestHeader(HttpHeaders.USER_AGENT, required = false) userAgent: String?,
    ): ResponseEntity<Void> {
        // Reject malformed short codes early — uniform 404 for all invalid/missing/killed links
        if (!VALID_SHORT_CODE.matches(shortCode)) {
            return notFound()
        }

        val isBot = BotDetector.isBot(userAgent)

        val destination =
            resolveSmartLinkUseCase.resolve(shortCode, isBot)
                ?: return notFound()

        return ResponseEntity
            .status(HttpStatus.FOUND)
            .location(destination)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header("X-Robots-Tag", "noindex, nofollow")
            .build()
    }

    /** Uniform response for non-existent, killed, expired, or malformed codes — no information leakage. */
    private fun notFound(): ResponseEntity<Void> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .build()
}
