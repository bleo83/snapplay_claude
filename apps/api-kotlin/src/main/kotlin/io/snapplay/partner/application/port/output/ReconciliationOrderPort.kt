package io.snapplay.partner.application.port.output

import io.snapplay.partner.domain.ProviderOrder
import java.util.UUID

interface ReconciliationOrderPort {
    fun findByRef(
        connectionId: UUID,
        providerOrderRef: String,
    ): ProviderOrder?
}
