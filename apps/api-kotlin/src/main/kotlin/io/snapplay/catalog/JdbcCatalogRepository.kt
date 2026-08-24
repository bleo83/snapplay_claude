package io.snapplay.catalog

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcCatalogRepository(
    private val jdbc: JdbcTemplate,
) : CatalogRepository {
    private val rowMapper =
        RowMapper { rs: ResultSet, _ ->
            CatalogProduct(
                id = UUID.fromString(rs.getString("id")),
                providerProductId = rs.getString("provider_product_id"),
                name = rs.getString("name"),
                description = rs.getString("description"),
                imageUrl = rs.getString("image_url") ?: "https://placehold.co/640x480?text=Producto",
                brand = rs.getString("brand"),
                categories =
                    (rs.getArray("categories")?.array as? kotlin.Array<*>)
                        ?.filterIsInstance<String>() ?: emptyList(),
                ageRestricted = rs.getBoolean("age_restricted"),
                referencePriceMinor =
                    rs
                        .getLong("reference_price_minor")
                        .takeIf { !rs.wasNull() },
                currency = rs.getString("currency"),
                status = ProductStatus.valueOf(rs.getString("status")),
            )
        }

    override fun findProducts(
        organizationId: UUID,
        filters: ProductFilters,
    ): List<CatalogProduct> {
        val sql =
            buildString {
                append(
                    """
                    SELECT cp.id, cp.provider_product_id, cp.name, cp.description,
                           cp.image_url, cp.brand, cp.categories, cp.age_restricted,
                           cp.reference_price_minor, cp.currency, cp.status
                    FROM catalog_products cp
                    WHERE cp.connection_id IN (
                        SELECT id FROM connections
                        WHERE content_organization_id = ?
                           OR commerce_organization_id = ?
                    )
                    """.trimIndent(),
                )
                if (filters.status != null) append("\nAND cp.status = ?")
                if (filters.category != null) append("\nAND ? = ANY(cp.categories)")
                if (filters.q != null) append("\nAND (cp.name ILIKE ? OR cp.description ILIKE ?)")
                append("\nORDER BY cp.name")
            }

        val params =
            buildList {
                add(organizationId)
                add(organizationId)
                if (filters.status != null) add(filters.status.name)
                if (filters.category != null) add(filters.category)
                if (filters.q != null) {
                    val like = "%${filters.q}%"
                    add(like)
                    add(like)
                }
            }

        return jdbc.query(sql, rowMapper, *params.toTypedArray())
    }
}
