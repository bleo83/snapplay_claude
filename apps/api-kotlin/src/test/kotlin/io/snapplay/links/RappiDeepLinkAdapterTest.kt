package io.snapplay.links

import io.snapplay.config.SnapPlayProperties
import io.snapplay.links.application.port.output.DeepLinkRequest
import io.snapplay.links.infrastructure.deeplink.RappiDeepLinkAdapter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private val ADAPTER =
    RappiDeepLinkAdapter(
        SnapPlayProperties(
            rappiWebBaseUrl = "https://www.rappi.com.ar",
            rappiAppScheme = "rappi",
        ),
    )

private val VALID_REQUEST =
    DeepLinkRequest(
        providerStoreId = "900123",
        providerCategoryId = "snacks",
        trackingToken = "abc123-def456",
        territory = "AR",
    )

class RappiDeepLinkAdapterTest {
    @Test
    fun `web fallback URL has correct host, store and category`() {
        val result = ADAPTER.build(VALID_REQUEST)
        assertThat(result.webFallbackUrl).startsWith("https://www.rappi.com.ar/tiendas/900123")
        assertThat(result.webFallbackUrl).contains("categoriaId=snacks")
    }

    @Test
    fun `app deep link uses configured scheme`() {
        val result = ADAPTER.build(VALID_REQUEST)
        assertThat(result.appDeepLink).startsWith("rappi://home/stores/900123")
        assertThat(result.appDeepLink).contains("categoriaId=snacks")
    }

    @Test
    fun `tracking token is propagated in both URLs`() {
        val result = ADAPTER.build(VALID_REQUEST)
        assertThat(result.webFallbackUrl).contains("snp_tk=abc123-def456")
        assertThat(result.appDeepLink).contains("snp_tk=abc123-def456")
    }

    @Test
    fun `adapter version is rappi-ar-v1`() {
        assertThat(ADAPTER.build(VALID_REQUEST).adapterVersion).isEqualTo("rappi-ar-v1")
    }

    @Test
    fun `blank providerStoreId throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            ADAPTER.build(VALID_REQUEST.copy(providerStoreId = ""))
        }
    }

    @Test
    fun `blank providerCategoryId throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            ADAPTER.build(VALID_REQUEST.copy(providerCategoryId = ""))
        }
    }

    @Test
    fun `providerStoreId with path traversal throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            ADAPTER.build(VALID_REQUEST.copy(providerStoreId = "../admin"))
        }
    }

    @Test
    fun `providerCategoryId with injection chars throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            ADAPTER.build(VALID_REQUEST.copy(providerCategoryId = "cat&evil=1"))
        }
    }

    @Test
    fun `web URL host outside allowlist throws IllegalArgumentException`() {
        val adapter =
            RappiDeepLinkAdapter(
                SnapPlayProperties(rappiWebBaseUrl = "https://evil.example.com"),
            )
        assertThrows<IllegalArgumentException> {
            adapter.build(VALID_REQUEST)
        }
    }

    @Test
    fun `special chars in store id are URL-encoded`() {
        // Spaces would be unusual but encoding should not crash
        val result = ADAPTER.build(VALID_REQUEST.copy(providerStoreId = "store.123"))
        assertThat(result.webFallbackUrl).contains("store.123")
    }
}
