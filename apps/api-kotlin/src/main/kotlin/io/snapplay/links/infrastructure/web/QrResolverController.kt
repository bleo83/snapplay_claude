package io.snapplay.links.infrastructure.web

import io.snapplay.links.application.port.input.ResolveSmartLinkUseCase
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/r")
class QrResolverController(
    private val resolveSmartLinkUseCase: ResolveSmartLinkUseCase,
) {
    @GetMapping("/{shortCode}")
    fun resolve(
        @PathVariable shortCode: String,
    ): ResponseEntity<Void> {
        val destination =
            resolveSmartLinkUseCase.resolve(shortCode)
                ?: return ResponseEntity.notFound().build()

        return ResponseEntity
            .status(HttpStatus.FOUND)
            .location(destination)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .build()
    }
}
