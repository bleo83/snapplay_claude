package io.snapplay.notifications.application.port.output

import java.util.UUID

interface DisneyWebhookClient {
    /**
     * POSTs [payload] to [webhookUrl], signed with [webhookSecret].
     * @return true if the remote endpoint returned a 2xx status; false on non-2xx or I/O error.
     */
    fun deliver(
        webhookUrl: String,
        webhookSecret: String,
        notificationId: UUID,
        payload: String,
    ): Boolean
}
