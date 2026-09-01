package io.snapplay.partner.application.port.output

import io.snapplay.partner.domain.PartnerEvent

interface PartnerEventRepository {
    /** Returns true if an event with this [eventId] has already been persisted. */
    fun existsByEventId(eventId: String): Boolean

    /**
     * Persists a new partner event.
     * @return true if inserted, false if [PartnerEvent.eventId] already exists (duplicate)
     */
    fun save(event: PartnerEvent): Boolean
}
