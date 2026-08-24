package io.snapplay.catalog

import io.snapplay.common.Cursor
import io.snapplay.common.PageResult
import io.snapplay.common.toPageResult
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcCatalogRepository(
    private val jdbc: JdbcTemplate,
) : CatalogRepository {
    private data class ProductRow(
        val product: CatalogProduct,
        val createdAt: Instant,
    )

    private val rowMapper =
        RowMapper { rs: ResultSet, _ ->
            ProductRow(
                product =
                    CatalogProduct(
                        id = UUID.fromString(rs.getString("id")),
                        providerProductId = rs.getString("provider_product_id"),
                        name = rs.getString("name"),
                        description = rs.getString("description"),
                        imageUrl = rs.getString("image_url") ?: "https://placehold.co/640x480?text=Producto",
                        brand = rs.getString("brand"),
                        categories =
                            (rs.getArray("categories")?.array as? Array<*>)
                                ?.filterIsInstance<String>() ?: emptyList(),
                        ageRestricted = rs.getBoolean("age_restricted"),
                        referencePriceMinor =
                            rs
                                .getLong("reference_price_minor")
                                .takeIf { !rs.wasNull() },
                        currency = rs.getString("currency"),
                        status = ProductStatus.valueOf(rs.getString("status")),
                    ),
                createdAt = rs.getTimestamp("created_at").toInstant(),
            )
        }

    override fun findProducts(
        organizationId: UUID,
        filters: ProductFilters,
        limit: Int,
        cursor: String?,
    ): PageResult<CatalogProduct> {
        val decoded = cursor?.let { Cursor.decode(it) }

        val sql =
            buildString {
                append(
                    """
                    SELECT cp.id, cp.provider_product_id, cp.name, cp.description,
                           cp.image_url, cp.brand, cp.categories, cp.age_restricted,
                           cp.reference_price_minor, cp.currency, cp.status, cp.created_at
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
                if (decoded != null) {
                    append("\nAND (cp.created_at < ? OR (cp.created_at = ? AND cp.id < ?::uuid))")
                }
                append("\nORDER BY cp.created_at DESC, cp.id DESC")
                append("\nLIMIT ?")
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
                if (decoded != null) {
                    add(Timestamp.from(decoded.first))
                    add(Timestamp.from(decoded.first))
                    add(decoded.second.toString())
                }
                add(limit + 1)
            }

        return jdbc
            .query(sql, rowMapper, *params.toTypedArray())
            .toPageResult(limit, { it.createdAt }, { it.product.id }, { it.product })
    }
}
