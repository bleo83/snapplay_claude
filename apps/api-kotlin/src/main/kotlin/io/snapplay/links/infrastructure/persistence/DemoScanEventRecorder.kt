package io.snapplay.links.infrastructure.persistence

import io.snapplay.links.application.port.output.ScanEventRecorder
import io.snapplay.links.domain.ResolvedSmartLink
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoScanEventRecorder : ScanEventRecorder {
    override fun record(resolved: ResolvedSmartLink) {
        // No-op in demo mode — scans are not persisted
    }
}
