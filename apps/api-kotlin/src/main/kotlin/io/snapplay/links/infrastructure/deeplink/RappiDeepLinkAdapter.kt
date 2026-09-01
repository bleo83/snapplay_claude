package io.snapplay.links.infrastructure.deeplink

import io.snapplay.config.SnapPlayProperties
import io.snapplay.links.application.port.output.DeepLinkAdapter
import io.snapplay.links.application.port.output.DeepLinkRequest
import io.snapplay.links.application.port.output.DeepLinkResult
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URLEncoder

/**
 * Rappi STORE_DEEPLINK adapter.
 *
 * Builds an app deep link and a web fallback URL that open exactly the configured
 * store and category within Rappi, preserving the Snap Play tracking token.
 *
 * URL format (configurable via snapplay.rappi.*):
 *   Web: {rappiWebBaseUrl}/tiendas/{storeId}?categoriaId={categoryId}&snp_tk={token}
 *   App: {rappiAppScheme}://home/stores/{storeId}?categoriaId={categoryId}&snp_tk={token}
 */
@Component
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class RappiDeepLinkAdapter(
    private val props: SnapPlayProperties,
) : DeepLinkAdapter {
    companion object {
        private const val ADAPTER_VERSION = "rappi-ar-v1"

        // Only these web hosts are allowed in built URLs — prevents open-redirect via misconfiguration
        private val ALLOWED_WEB_HOSTS = setOf("rappi.com.ar", "app.rappi.com")

        // Alphanumeric + URL-safe chars; rejects path traversal, injected separators, etc.
        private val SAFE_VALUE = Regex("^[A-Za-z0-9_\\-\\.~]+$")
    }

    override fun build(request: DeepLinkRequest): DeepLinkResult {
        validate(request)
        val webUrl = buildWebUrl(request)
        val appUrl = buildAppUrl(request)
        assertWebHostAllowed(webUrl)
        return DeepLinkResult(
            appDeepLink = appUrl,
            webFallbackUrl = webUrl,
            adapterVersion = ADAPTER_VERSION,
        )
    }

    private fun validate(request: DeepLinkRequest) {
        require(request.providerStoreId.isNotBlank()) {
            "Rappi adapter: providerStoreId must not be blank"
        }
        require(request.providerCategoryId.isNotBlank()) {
            "Rappi adapter: providerCategoryId must not be blank"
        }
        require(SAFE_VALUE.matches(request.providerStoreId)) {
            "Rappi adapter: providerStoreId '${request.providerStoreId}' contains invalid characters"
        }
        require(SAFE_VALUE.matches(request.providerCategoryId)) {
            "Rappi adapter: providerCategoryId '${request.providerCategoryId}' contains invalid characters"
        }
        require(SAFE_VALUE.matches(request.trackingToken)) {
            // Do not log the token value — redacted intentionally
            "Rappi adapter: trackingToken contains invalid characters"
        }
    }

    private fun buildWebUrl(request: DeepLinkRequest): String {
        val base = props.rappiWebBaseUrl.trimEnd('/')
        return "$base/tiendas/${enc(request.providerStoreId)}" +
            "?categoriaId=${enc(request.providerCategoryId)}" +
            "&snp_tk=${enc(request.trackingToken)}"
    }

    private fun buildAppUrl(request: DeepLinkRequest): String =
        "${props.rappiAppScheme}://home/stores/${enc(request.providerStoreId)}" +
            "?categoriaId=${enc(request.providerCategoryId)}" +
            "&snp_tk=${enc(request.trackingToken)}"

    private fun assertWebHostAllowed(url: String) {
        val host =
            URI.create(url).host
                ?: throw IllegalStateException("Rappi adapter: built web URL has no host: $url")
        val allowed = ALLOWED_WEB_HOSTS.any { host == it || host.endsWith(".$it") }
        require(allowed) {
            "Rappi adapter: web URL host '$host' is not in the allowlist $ALLOWED_WEB_HOSTS"
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)
}
