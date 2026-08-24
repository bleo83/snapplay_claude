package io.snapplay.health

import io.snapplay.config.SnapPlayProperties
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
class HealthController(
    private val props: SnapPlayProperties,
) {
    @GetMapping("/health")
    fun health(request: HttpServletRequest): Map<String, String> =
        mapOf(
            "status" to "ok",
            "mode" to if (props.demo) "demo" else "supabase",
            "timestamp" to Instant.now().toString(),
        )
}
