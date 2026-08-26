package io.snapplay.connection.infrastructure.adapter

import io.snapplay.connection.application.port.output.ConnectorPort
import io.snapplay.connection.domain.Capability
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * In-process stub of the Rappi connector used in demo and local-development mode.
 * Supports every MVP capability so the full vertical can be exercised without a live Rappi sandbox.
 * Replaced by RappiConnectorAdapter in production.
 */
@Component
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoConnectorAdapter : ConnectorPort {
    override val connectorKey = "rappi"

    override val supportedCapabilities: Set<Capability> =
        setOf(
            Capability.STORE_CATEGORY_DEEPLINK,
            Capability.CATALOG_SYNC,
            Capability.ORDER_WEBHOOK_INGRESS,
            Capability.ORDER_STATUS_MILESTONES,
            Capability.DAILY_RECONCILIATION,
        )
}
