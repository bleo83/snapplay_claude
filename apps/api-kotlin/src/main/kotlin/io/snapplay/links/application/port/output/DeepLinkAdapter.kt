package io.snapplay.links.application.port.output

data class DeepLinkRequest(
    val providerStoreId: String,
    val providerCategoryId: String,
    val trackingToken: String,
    val territory: String,
)

data class DeepLinkResult(
    val appDeepLink: String,
    val webFallbackUrl: String,
    val adapterVersion: String,
)

interface DeepLinkAdapter {
    /** Builds app deep link and web fallback URL. Throws [IllegalArgumentException] for invalid store/category. */
    fun build(request: DeepLinkRequest): DeepLinkResult
}
