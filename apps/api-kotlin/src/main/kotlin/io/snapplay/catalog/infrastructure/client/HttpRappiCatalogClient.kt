package io.snapplay.catalog.infrastructure.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.snapplay.catalog.application.port.output.RappiCatalogClient
import io.snapplay.catalog.domain.RappiCatalogPage
import io.snapplay.catalog.domain.RappiCategorySnapshot
import io.snapplay.catalog.domain.RappiStoreSnapshot
import io.snapplay.config.SnapPlayProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.UUID

@Component
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class HttpRappiCatalogClient(
    restClientBuilder: RestClient.Builder,
    private val props: SnapPlayProperties,
    private val objectMapper: ObjectMapper,
) : RappiCatalogClient {
    private val log = LoggerFactory.getLogger(HttpRappiCatalogClient::class.java)
    private val restClient = restClientBuilder.build()

    override fun fetchStores(
        connectionId: UUID,
        cursor: String?,
    ): RappiCatalogPage {
        if (props.rappiApiBaseUrl.isBlank()) {
            log.warn("rappiApiBaseUrl not configured — returning empty catalog page")
            return RappiCatalogPage(stores = emptyList(), nextCursor = null)
        }

        val uri =
            "${props.rappiApiBaseUrl}/api/v1/catalog/stores" +
                (cursor?.let { "?cursor=$it" } ?: "")

        val body =
            restClient
                .get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer ${props.rappiApiKey}")
                .retrieve()
                .body(String::class.java) ?: return RappiCatalogPage(emptyList(), null)

        return parseResponse(body)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseResponse(body: String): RappiCatalogPage {
        val json: Map<String, Any?> = objectMapper.readValue(body)
        val rawStores = (json["stores"] as? List<Map<String, Any?>>) ?: emptyList()
        val stores =
            rawStores.map { raw ->
                val rawCategories = (raw["categories"] as? List<Map<String, Any?>>) ?: emptyList()
                RappiStoreSnapshot(
                    providerStoreId = raw["store_id"] as? String ?: "",
                    name = raw["name"] as? String ?: "",
                    country = raw["country"] as? String ?: "AR",
                    categories =
                        rawCategories.map { cat ->
                            RappiCategorySnapshot(
                                providerCategoryId = cat["category_id"] as? String ?: "",
                                name = cat["name"] as? String ?: "",
                            )
                        },
                )
            }
        return RappiCatalogPage(stores = stores, nextCursor = json["next_cursor"] as? String)
    }
}
