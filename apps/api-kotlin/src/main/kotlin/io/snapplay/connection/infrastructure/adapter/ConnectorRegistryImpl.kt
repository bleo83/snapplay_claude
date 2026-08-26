package io.snapplay.connection.infrastructure.adapter

import io.snapplay.connection.application.port.output.ConnectorPort
import io.snapplay.connection.application.port.output.ConnectorRegistry
import org.springframework.stereotype.Component

/**
 * Spring-managed registry built from all [ConnectorPort] beans on the classpath.
 * Use cases resolve adapters by connectorKey without knowing which adapters are present.
 */
@Component
class ConnectorRegistryImpl(connectors: List<ConnectorPort>) : ConnectorRegistry {
    private val registry: Map<String, ConnectorPort> = connectors.associateBy { it.connectorKey }

    override fun find(connectorKey: String): ConnectorPort? = registry[connectorKey]
}
