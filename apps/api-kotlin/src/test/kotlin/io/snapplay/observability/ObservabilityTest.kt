package io.snapplay.observability

import io.snapplay.config.BusinessMetrics
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest(
    properties = [
        "management.endpoints.web.exposure.include=health,prometheus,metrics",
    ],
)
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class ObservabilityTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var metrics: BusinessMetrics

    @Test
    fun `health endpoint returns 200`() {
        mockMvc.get("/actuator/health")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.status") { value("UP") } }
    }

    @Test
    fun `business metrics are registered in Micrometer`() {
        metrics.recordScan("conn-001", false)
        metrics.recordScan("conn-001", true)
        metrics.recordWebhookIngested("ORDER_DELIVERED")
        metrics.recordWebhookRejected("INVALID_SIGNATURE")
        metrics.recordOutboxProcessed("ORDER_DELIVERED")
        metrics.recordOutboxDeadLettered("ORDER_DELIVERED")
        metrics.recordMilestoneDelivered()
        metrics.recordMilestoneDeadLettered()
        metrics.recordReconciliationMismatch("STATUS_MISMATCH")
        metrics.recordReconciliationRecovered()
        metrics.recordAuthDenied("EXPIRED_TOKEN")

        // Verify via actuator/metrics that our custom metrics exist
        val metricsListBody =
            mockMvc.get("/actuator/metrics")
                .andExpect { status { isOk() } }
                .andReturn().response.contentAsString

        assertThat(metricsListBody).contains("snapplay.resolver.scans")
        assertThat(metricsListBody).contains("snapplay.webhook.ingested")
        assertThat(metricsListBody).contains("snapplay.outbox.processed")
        assertThat(metricsListBody).contains("snapplay.milestone.delivered")
        assertThat(metricsListBody).contains("snapplay.reconciliation.mismatches")
        assertThat(metricsListBody).contains("snapplay.auth.denied")
    }

    @Test
    fun `resolver timer records latency`() {
        val sample = metrics.resolverTimer()
        Thread.sleep(5)
        metrics.recordResolverLatency(sample, true)

        val body =
            mockMvc.get("/actuator/metrics")
                .andReturn().response.contentAsString

        assertThat(body).contains("snapplay.resolver.latency")
    }

    @Test
    fun `metrics with tags are distinguishable`() {
        metrics.recordScan("conn-A", false)
        metrics.recordScan("conn-B", true)

        // Both should contribute to the same metric name but with different tags
        val body =
            mockMvc.get("/actuator/metrics/snapplay.resolver.scans")
                .andExpect { status { isOk() } }
                .andReturn().response.contentAsString

        assertThat(body).contains("connection")
        assertThat(body).contains("bot")
    }

    @Test
    fun `metrics endpoint does not contain PII`() {
        metrics.recordScan("conn-001", false)

        val body =
            mockMvc.get("/actuator/metrics")
                .andReturn().response.contentAsString

        assertThat(body).doesNotContain("tracking_token")
        assertThat(body).doesNotContain("snp_tk")
        assertThat(body).doesNotContain("password")
        assertThat(body).doesNotContain("secret")
    }
}
