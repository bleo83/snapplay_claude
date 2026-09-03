package io.snapplay.partner.infrastructure.persistence

import io.snapplay.partner.application.port.output.ReconciliationOrderPort
import io.snapplay.partner.domain.ProviderOrder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoReconciliationOrderPort(
    private val orderRepo: DemoProviderOrderRepository,
) : ReconciliationOrderPort {
    override fun findByRef(
        connectionId: UUID,
        providerOrderRef: String,
    ): ProviderOrder? = orderRepo.findByRef(connectionId, providerOrderRef)
}
