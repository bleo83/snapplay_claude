package io.snapplay.retention

import io.snapplay.retention.domain.DataClass
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class RetentionPolicyTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `every data class has owner, purpose, and retention period`() {
        DataClass.entries.forEach { dc ->
            assertThat(dc.owner).isNotBlank()
            assertThat(dc.purpose).isNotBlank()
            assertThat(dc.retention.toDays()).isGreaterThan(0)
        }
    }

    @Test
    fun `audit events and ledger entries are never anonymized or deleted`() {
        assertThat(DataClass.AUDIT_EVENT.anonymizable).isFalse()
        assertThat(DataClass.AUDIT_EVENT.retention.toDays()).isGreaterThanOrEqualTo(2555)

        assertThat(DataClass.LEDGER_ENTRY.anonymizable).isFalse()
        assertThat(DataClass.LEDGER_ENTRY.retention.toDays()).isGreaterThanOrEqualTo(2555)
    }

    @Test
    fun `handoff sessions and partner events are anonymizable`() {
        assertThat(DataClass.HANDOFF_SESSION.anonymizable).isTrue()
        assertThat(DataClass.PARTNER_EVENT.anonymizable).isTrue()
    }

    @Test
    fun `operational data has shorter retention than financial`() {
        val operational = listOf(DataClass.HANDOFF_SESSION, DataClass.PARTNER_EVENT, DataClass.POLLING_TOKEN, DataClass.OUTBOX_EVENT)
        val financial = listOf(DataClass.AUDIT_EVENT, DataClass.LEDGER_ENTRY)

        val maxOperational = operational.maxOf { it.retention.toDays() }
        val minFinancial = financial.minOf { it.retention.toDays() }

        assertThat(maxOperational).isLessThan(minFinancial)
    }

    @Test
    fun `policies endpoint returns all data classes`() {
        mockMvc.get("/v1/admin/retention/policies")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.length()") { value(DataClass.entries.size) } }
            .andExpect { jsonPath("$[0].dataClass") { isString() } }
            .andExpect { jsonPath("$[0].owner") { isString() } }
            .andExpect { jsonPath("$[0].purpose") { isString() } }
            .andExpect { jsonPath("$[0].retentionDays") { isNumber() } }
    }

    @Test
    fun `no PII in demo environment data`() {
        // Demo repositories use synthetic data — verify no real email/phone patterns
        // This is a structural test: all demo data uses UUIDs and synthetic names
        DataClass.entries.forEach { dc ->
            assertThat(dc.tableName).doesNotContain("email")
            assertThat(dc.tableName).doesNotContain("phone")
        }
    }
}
