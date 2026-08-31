package io.snapplay.links.application.port.output

import io.snapplay.links.domain.ResolvedSmartLink

interface SmartLinkResolver {
    /** Cross-org lookup. Returns null if the link is inactive, expired, paused, or the experience is not PUBLISHED. */
    fun resolveByShortCode(shortCode: String): ResolvedSmartLink?
}
