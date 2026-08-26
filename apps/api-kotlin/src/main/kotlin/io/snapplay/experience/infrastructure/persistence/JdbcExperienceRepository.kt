package io.snapplay.experience.infrastructure.persistence

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.snapplay.common.Cursor
import io.snapplay.common.PageResult
import io.snapplay.common.toPageResult
import io.snapplay.experience.application.port.output.ContentContextResult
import io.snapplay.experience.application.port.output.CreateExperienceInput
import io.snapplay.experience.application.port.output.ExperienceRepository
import io.snapplay.experience.domain.CommerceDestination
import io.snapplay.experience.domain.Experience
import io.snapplay.experience.domain.ExperienceStatus
import io.snapplay.experience.domain.HandoffMode
import org.postgresql.util.PGobject
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class JdbcExperienceRepository(
    private val jdbc: JdbcTemplate,
) : ExperienceRepository {
    private val objectMapper = jacksonObjectMapper()

    private data class ExperienceRow(
        val experience: Experience,
        val createdAt: Instant,
    )

    private fun mapRow(rs: ResultSet): ExperienceRow {
        val productIds = (rs.getArray("product_ids")?.array as? Array<*>) ?: emptyArray<Any>()
        val storeSelectionJson = rs.getString("store_selection") ?: "{}"
        val storeSelection: Map<String, String> = objectMapper.readValue(storeSelectionJson)
        val eligibilityJson = rs.getString("eligibility") ?: "{}"
        val eligibility: Map<String, Any> = objectMapper.readValue(eligibilityJson)

        @Suppress("UNCHECKED_CAST")
        val countries = (eligibility["countries"] as? List<String>) ?: emptyList()

        return ExperienceRow(
            experience =
                Experience(
                    id = UUID.fromString(rs.getString("id")),
                    name = rs.getString("name"),
                    contextTitle = rs.getString("context_title"),
                    channel = rs.getString("channel_display_name"),
                    connectionId = UUID.fromString(rs.getString("connection_id")),
                    territory = countries.firstOrNull() ?: "",
                    destination =
                        CommerceDestination(
                            providerStoreId = storeSelection["provider_store_id"] ?: "",
                            providerCategoryId = storeSelection["provider_category_id"] ?: "",
                        ),
                    version = rs.getInt("current_version"),
                    status = ExperienceStatus.valueOf(rs.getString("status")),
                    handoffMode = HandoffMode.valueOf(rs.getString("handoff_mode") ?: "STORE_DEEPLINK"),
                    productCount = productIds.size,
                    startsAt = rs.getTimestamp("effective_from")?.toInstant() ?: Instant.now(),
                    endsAt = rs.getTimestamp("effective_to")?.toInstant(),
                ),
            createdAt = rs.getTimestamp("created_at").toInstant(),
        )
    }

    private val baseSelectSql =
        """
        SELECT e.id, e.name, e.status, e.current_version, e.created_at, e.connection_id,
               cc.title        AS context_title,
               ch.display_name AS channel_display_name,
               ev.handoff_mode, ev.product_ids,
               ev.store_selection, ev.eligibility,
               ev.effective_from, ev.effective_to
        FROM experiences e
        JOIN content_contexts cc ON cc.id = e.content_context_id
        JOIN channels         ch ON ch.id = cc.channel_id
        LEFT JOIN experience_versions ev
               ON ev.experience_id = e.id
              AND ev.version = e.current_version
        """.trimIndent()

    override fun findAll(
        organizationId: UUID,
        limit: Int,
        cursor: String?,
    ): PageResult<Experience> {
        val decoded = cursor?.let { Cursor.decode(it) }

        // sql is built exclusively from static string literals; all dynamic values use ? params — no injection risk
        @Suppress("SqlSourceToSinkFlow")
        val sql =
            buildString {
                append(baseSelectSql)
                append("\nWHERE e.organization_id = ?")
                if (decoded != null) {
                    append("\nAND (e.created_at < ? OR (e.created_at = ? AND e.id < ?::uuid))")
                }
                append("\nORDER BY e.created_at DESC, e.id DESC")
                append("\nLIMIT ?")
            }

        val params =
            buildList {
                add(organizationId)
                if (decoded != null) {
                    add(Timestamp.from(decoded.first))
                    add(Timestamp.from(decoded.first))
                    add(decoded.second.toString())
                }
                add(limit + 1)
            }

        return jdbc
            .query(sql, { rs, _ -> mapRow(rs) }, *params.toTypedArray())
            .toPageResult(limit, { it.createdAt }, { it.experience.id }, { it.experience })
    }

    override fun findById(
        organizationId: UUID,
        id: UUID,
    ): Experience? =
        jdbc
            .query(
                "$baseSelectSql\nWHERE e.organization_id = ? AND e.id = ?",
                { rs, _ -> mapRow(rs) },
                organizationId,
                id,
            ).firstOrNull()
            ?.experience

    override fun findContentContext(
        organizationId: UUID,
        contextTitle: String,
    ): ContentContextResult? =
        jdbc
            .query(
                """
                SELECT cc.id, ch.display_name AS channel_display_name
                FROM content_contexts cc
                JOIN channels ch ON ch.id = cc.channel_id
                WHERE cc.organization_id = ?
                  AND cc.title = ?
                LIMIT 1
                """.trimIndent(),
                { rs, _ -> ContentContextResult(UUID.fromString(rs.getString("id")), rs.getString("channel_display_name")) },
                organizationId,
                contextTitle,
            ).firstOrNull()

    override fun findActiveContractId(organizationId: UUID): UUID? =
        jdbc
            .query(
                "SELECT id FROM commercial_contracts WHERE publisher_organization_id = ? AND status = 'ACTIVE' LIMIT 1",
                { rs, _ -> UUID.fromString(rs.getString("id")) },
                organizationId,
            ).firstOrNull()

    override fun findActiveProductIds(
        connectionId: UUID,
        limit: Int,
    ): List<UUID> =
        jdbc.query(
            "SELECT id FROM catalog_products WHERE connection_id = ? AND status = 'ACTIVE' LIMIT ?",
            { rs, _ -> UUID.fromString(rs.getString("id")) },
            connectionId,
            limit,
        )

    @Transactional
    override fun create(
        organizationId: UUID,
        actorId: UUID,
        input: CreateExperienceInput,
    ): Experience {
        val experienceId = UUID.randomUUID()

        jdbc.update(
            """
            INSERT INTO experiences
                (id, organization_id, name, content_context_id, connection_id, contract_id, status, current_version)
            VALUES (?, ?, ?, ?, ?, ?, 'DRAFT', 1)
            """.trimIndent(),
            experienceId,
            organizationId,
            input.name,
            input.contextId,
            input.connectionId,
            input.contractId,
        )

        val productIdArray =
            jdbc.dataSource!!.connection.use { conn ->
                conn.createArrayOf("uuid", input.productIds.toTypedArray())
            }

        val storeSelectionPg =
            PGobject().apply {
                type = "jsonb"
                value =
                    objectMapper.writeValueAsString(
                        mapOf(
                            "provider_store_id" to input.destination.providerStoreId,
                            "provider_category_id" to input.destination.providerCategoryId,
                        ),
                    )
            }

        val eligibilityPg =
            PGobject().apply {
                type = "jsonb"
                value = objectMapper.writeValueAsString(mapOf("countries" to listOf(input.territory)))
            }

        jdbc.update(
            """
            INSERT INTO experience_versions
                (experience_id, version, status, handoff_mode, product_ids, store_selection, eligibility, presentation, effective_from, effective_to)
            VALUES (?, 1, 'DRAFT', ?, ?, ?, ?, '{"locale":"es-AR"}'::jsonb, ?, ?)
            """.trimIndent(),
            experienceId,
            input.handoffMode.name,
            productIdArray,
            storeSelectionPg,
            eligibilityPg,
            Timestamp.from(input.startsAt),
            input.endsAt?.let { Timestamp.from(it) },
        )

        jdbc.update(
            """
            INSERT INTO audit_log
                (organization_id, actor_user_id, action, resource_type, resource_id, request_id, after_redacted)
            VALUES (?, ?, 'experience.created', 'experience', ?, ?, '{"version":1,"status":"DRAFT"}'::jsonb)
            """.trimIndent(),
            organizationId,
            actorId,
            experienceId.toString(),
            UUID.randomUUID().toString(),
        )

        return Experience(
            id = experienceId,
            name = input.name,
            contextTitle = input.contextTitle,
            channel = input.channelDisplayName,
            connectionId = input.connectionId,
            territory = input.territory,
            destination = input.destination,
            version = 1,
            status = ExperienceStatus.DRAFT,
            handoffMode = input.handoffMode,
            productCount = input.productIds.size,
            startsAt = input.startsAt,
            endsAt = input.endsAt,
        )
    }

    @Transactional
    override fun publish(
        organizationId: UUID,
        actorId: UUID,
        id: UUID,
    ): Experience {
        jdbc.update(
            "UPDATE experiences SET status = 'PUBLISHED', updated_at = now() WHERE id = ? AND organization_id = ?",
            id,
            organizationId,
        )
        jdbc.update(
            """
            UPDATE experience_versions SET status = 'PUBLISHED'
            WHERE experience_id = ?
              AND version = (SELECT current_version FROM experiences WHERE id = ?)
            """.trimIndent(),
            id,
            id,
        )
        jdbc.update(
            """
            INSERT INTO audit_log
                (organization_id, actor_user_id, action, resource_type, resource_id, request_id, after_redacted)
            VALUES (?, ?, 'experience.published', 'experience', ?, ?, '{"status":"PUBLISHED"}'::jsonb)
            """.trimIndent(),
            organizationId,
            actorId,
            id.toString(),
            UUID.randomUUID().toString(),
        )
        return findById(organizationId, id)!!
    }

    @Transactional
    override fun pause(
        organizationId: UUID,
        actorId: UUID,
        id: UUID,
    ): Experience {
        jdbc.update(
            "UPDATE experiences SET status = 'PAUSED', updated_at = now() WHERE id = ? AND organization_id = ?",
            id,
            organizationId,
        )
        jdbc.update(
            """
            INSERT INTO audit_log
                (organization_id, actor_user_id, action, resource_type, resource_id, request_id, after_redacted)
            VALUES (?, ?, 'experience.paused', 'experience', ?, ?, '{"status":"PAUSED"}'::jsonb)
            """.trimIndent(),
            organizationId,
            actorId,
            id.toString(),
            UUID.randomUUID().toString(),
        )
        return findById(organizationId, id)!!
    }

    @Transactional
    override fun retire(
        organizationId: UUID,
        actorId: UUID,
        id: UUID,
    ): Experience {
        jdbc.update(
            "UPDATE experiences SET status = 'RETIRED', updated_at = now() WHERE id = ? AND organization_id = ?",
            id,
            organizationId,
        )
        jdbc.update(
            """
            INSERT INTO audit_log
                (organization_id, actor_user_id, action, resource_type, resource_id, request_id, after_redacted)
            VALUES (?, ?, 'experience.retired', 'experience', ?, ?, '{"status":"RETIRED"}'::jsonb)
            """.trimIndent(),
            organizationId,
            actorId,
            id.toString(),
            UUID.randomUUID().toString(),
        )
        return findById(organizationId, id)!!
    }

    @Transactional
    override fun clone(
        organizationId: UUID,
        actorId: UUID,
        id: UUID,
    ): Experience {
        val source = findById(organizationId, id)!!
        val newId = UUID.randomUUID()
        val cloneName = "Copy of ${source.name}"

        // Copy the experiences row
        jdbc.update(
            """
            INSERT INTO experiences
                (id, organization_id, name, content_context_id, connection_id, contract_id, status, current_version)
            SELECT ?, organization_id, ?, content_context_id, connection_id, contract_id, 'DRAFT', 1
            FROM experiences WHERE id = ?
            """.trimIndent(),
            newId,
            cloneName,
            id,
        )

        // Copy the current version into version 1 of the new experience
        jdbc.update(
            """
            INSERT INTO experience_versions
                (experience_id, version, status, handoff_mode, product_ids, store_selection, eligibility, presentation, effective_from, effective_to)
            SELECT ?, 1, 'DRAFT', handoff_mode, product_ids, store_selection, eligibility, presentation, effective_from, effective_to
            FROM experience_versions
            WHERE experience_id = ?
              AND version = (SELECT current_version FROM experiences WHERE id = ?)
            """.trimIndent(),
            newId,
            id,
            id,
        )

        jdbc.update(
            """
            INSERT INTO audit_log
                (organization_id, actor_user_id, action, resource_type, resource_id, request_id, after_redacted)
            VALUES (?, ?, 'experience.cloned', 'experience', ?, ?, ?::jsonb)
            """.trimIndent(),
            organizationId,
            actorId,
            newId.toString(),
            UUID.randomUUID().toString(),
            objectMapper.writeValueAsString(mapOf("clonedFrom" to id.toString(), "status" to "DRAFT")),
        )

        return findById(organizationId, newId)!!
    }
}
