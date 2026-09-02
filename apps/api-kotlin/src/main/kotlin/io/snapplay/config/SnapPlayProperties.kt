package io.snapplay.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "snapplay")
data class SnapPlayProperties(
    val demo: Boolean = false,
    val publicBaseUrl: String = "http://localhost:8080",
    val backofficeUrl: String = "http://localhost:3000",
    val allowedOrigins: String = "http://localhost:3000",
    val rappiWebhookSecret: String = "",
    val idempotencyTtlHours: Long = 24,
    val rappiWebBaseUrl: String = "https://www.rappi.com.ar",
    val rappiAppScheme: String = "rappi",
    val rappiMockEnabled: Boolean = false,
    val handoffSessionTtlMinutes: Long = 30,
    val pollingTokenTtlMinutes: Long = 60,
)
