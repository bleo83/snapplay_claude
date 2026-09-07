package io.snapplay.experience.infrastructure.persistence

import io.snapplay.experience.application.port.output.CatalogValidationPort
import io.snapplay.experience.application.port.output.CatalogValidationPort.CategoryCheck
import io.snapplay.experience.application.port.output.CatalogValidationPort.StoreCheck
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcCatalogValidationPort(
    private val jdbc: JdbcTemplate,
) : CatalogValidationPort {
    override fun checkStore(
        connectionId: UUID,
        providerStoreId: String,
    ): StoreCheck {
        val rows =
            jdbc.query(
                "SELECT id, status FROM provider_stores WHERE connection_id = ? AND provider_store_id = ?",
                { rs, _ -> Triple(UUID.fromString(rs.getString("id")), rs.getString("status"), true) },
                connectionId,
                providerStoreId,
            )
        if (rows.isEmpty()) return StoreCheck(exists = false, active = false, stale = false, internalId = null)
        val (id, status, _) = rows.first()
        return StoreCheck(exists = true, active = status == "ACTIVE", stale = status == "STALE", internalId = id)
    }

    override fun checkCategoryForStore(
        connectionId: UUID,
        providerCategoryId: String,
        storeInternalId: UUID,
    ): CategoryCheck {
        val rows =
            jdbc.query(
                """
                SELECT c.id, c.status,
                       EXISTS(SELECT 1 FROM store_categories sc WHERE sc.store_id = ? AND sc.category_id = c.id) AS linked
                FROM provider_categories c
                WHERE c.connection_id = ? AND c.provider_category_id = ?
                """.trimIndent(),
                { rs, _ -> Triple(rs.getString("status"), rs.getBoolean("linked"), true) },
                storeInternalId,
                connectionId,
                providerCategoryId,
            )
        if (rows.isEmpty()) return CategoryCheck(exists = false, active = false, stale = false, linkedToStore = false)
        val (status, linked, _) = rows.first()
        return CategoryCheck(exists = true, active = status == "ACTIVE", stale = status == "STALE", linkedToStore = linked)
    }
}
