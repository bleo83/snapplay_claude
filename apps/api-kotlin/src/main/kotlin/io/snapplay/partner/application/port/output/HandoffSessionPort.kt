package io.snapplay.partner.application.port.output

import io.snapplay.partner.domain.HandoffSessionRef
import java.util.UUID

interface HandoffSessionPort {
    /** Looks up a non-terminal handoff session by its tracking token hash. */
    fun findByTokenHash(tokenHash: String): HandoffSessionRef?

    /** Transitions the session to CONVERTED, ignoring terminal-state conflicts. */
    fun markConverted(id: UUID)
}
