package io.snapplay.links.application.port.output

import io.snapplay.links.domain.ResolvedSmartLink

interface DeepLinkAdapter {
    /** Builds the redirect URL for the given resolved smart link. */
    fun buildUrl(resolved: ResolvedSmartLink): String
}
