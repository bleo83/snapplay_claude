package io.snapplay.notifications.infrastructure.client

import io.snapplay.notifications.application.port.output.DisneyWebhookClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class HttpDisneyWebhookClient(
    restClientBuilder: RestClient.Builder,
) : DisneyWebhookClient {
    private val log = LoggerFactory.getLogger(HttpDisneyWebhookClient::class.java)
    private val restClient = restClientBuilder.build()

    override fun deliver(
        webhookUrl: String,
        webhookSecret: String,
        notificationId: UUID,
        payload: String,
    ): Boolean {
        val signature = computeHmac(payload.toByteArray(Charsets.UTF_8), webhookSecret)
        return runCatching {
            val status =
                restClient
                    .post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-SnapPlay-Signature", signature)
                    .header("X-Notification-Id", notificationId.toString())
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .statusCode
                    .is2xxSuccessful
            if (!status) log.warn("Disney webhook returned non-2xx for notification_id={}", notificationId)
            status
        }.getOrElse {
            log.warn("I/O error delivering milestone notification_id={} to {}", notificationId, webhookUrl, it)
            false
        }
    }

    private fun computeHmac(
        data: ByteArray,
        secret: String,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hex = mac.doFinal(data).joinToString("") { "%02x".format(it) }
        return "sha256=$hex"
    }
}
