package io.snapplay.notifications.application.port.output

import io.snapplay.notifications.domain.OrderMilestone
import java.util.UUID

interface SessionMilestonePort {
    /** Returns the most advanced milestone reached for this session, or null if none yet. */
    fun findCurrentMilestone(handoffSessionId: UUID): OrderMilestone?
}
