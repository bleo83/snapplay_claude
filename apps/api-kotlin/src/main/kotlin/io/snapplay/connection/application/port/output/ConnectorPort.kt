package io.snapplay.connection.application.port.output

import io.snapplay.connection.domain.Capability

/**
 * Implemented by each partner connector adapter (Rappi, demo, …).
 * The core never references a specific adapter class directly — it works through this port.
 */
interface ConnectorPort {
    /** Stable key that matches [io.snapplay.connection.domain.Connection.connectorKey]. */
    val connectorKey: String

    /** Capabilities this adapter can fulfil. */
    val supportedCapabilities: Set<Capability>
}
