package io.snapplay.links.application.port.output

import io.snapplay.links.domain.ResolvedSmartLink

interface ScanEventRecorder {
    /** Persists a scan event for the resolved link. Implementations must be non-blocking where possible. */
    fun record(resolved: ResolvedSmartLink)
}
