package io.snapplay.connection.application.port.output

/**
 * Resolves a [ConnectorPort] by its key.
 * Returns null for unknown connectors so callers can decide whether to reject or allow.
 */
fun interface ConnectorRegistry {
    fun find(connectorKey: String): ConnectorPort?
}
