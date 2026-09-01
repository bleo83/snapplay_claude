package io.snapplay.links.domain

import java.time.Instant
import java.util.UUID

enum class HandoffSessionStatus {
    CREATED,
    REDIRECTED,
    CONVERTED,
    EXPIRED,
    FAILED,
}

/**
 * Records that a QR scan produced a redirect, tying the tracking token to the exact
 * experience version, connection, and store/category served at that moment.
 *
 * [trackingTokenHash] is the SHA-256 hex of the raw token. The raw token is passed to
 * Rappi in the URL but never persisted, keeping it non-reversible from the DB.
 */
data class HandoffSession(
    val id: UUID,
    val smartLinkId: UUID,
    val experienceVersionId: UUID,
    val connectionId: UUID,
    val trackingTokenHash: String,
    val dataSharingMode: String,
    val status: HandoffSessionStatus,
    val expiresAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
)
