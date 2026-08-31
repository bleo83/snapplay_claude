package io.snapplay.links.infrastructure.deeplink

import io.snapplay.links.application.port.output.DeepLinkAdapter
import io.snapplay.links.domain.ResolvedSmartLink
import org.springframework.stereotype.Component

/**
 * Stub adapter until SNA-24 (Rappi deep link adapter) is implemented.
 * Builds a Rappi web URL for the configured store/category.
 */
@Component
class StubDeepLinkAdapter : DeepLinkAdapter {
    override fun buildUrl(resolved: ResolvedSmartLink): String =
        "https://www.rappi.com.ar/stores/${resolved.providerStoreId}?category=${resolved.providerCategoryId}"
}
