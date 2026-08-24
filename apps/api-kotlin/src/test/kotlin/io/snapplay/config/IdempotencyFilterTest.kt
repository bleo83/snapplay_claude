package io.snapplay.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Testcontainers
@EnabledIfSystemProperty(named = "testcontainers.enabled", matches = "true")
class IdempotencyFilterTest {
    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun setup() {
        jdbc.execute("DELETE FROM idempotency_keys")
    }

    @Test
    fun `replays cached response without hitting handler`() {
        val key = "01JZABC1234567890ABCDEFGH" // valid ULID
        jdbc.update(
            "INSERT INTO idempotency_keys (key, endpoint, status_code, response, content_type) VALUES (?, ?, ?, ?, ?)",
            key,
            "POST:/v1/experiences",
            201,
            """{"id":"cached-id","name":"Cached Experience"}""",
            "application/json",
        )

        val response = post("/v1/experiences", key, """{"name":"New Experience"}""")

        assertThat(response.statusCode.value()).isEqualTo(201)
        assertThat(response.body).contains("cached-id")
    }

    @Test
    fun `returns 409 when key is in progress`() {
        val key = "01JZABC1234567890ABCDEFGI" // valid ULID, different from above
        jdbc.update(
            "INSERT INTO idempotency_keys (key, endpoint) VALUES (?, ?)",
            key,
            "POST:/v1/experiences",
        )

        val response = post("/v1/experiences", key, """{"name":"Any Experience"}""")

        assertThat(response.statusCode.value()).isEqualTo(409)
    }

    @Test
    fun `returns 400 for invalid Idempotency-Key format`() {
        val response = post("/v1/experiences", "not-a-valid-key", """{"name":"Any Experience"}""")

        assertThat(response.statusCode.value()).isEqualTo(400)
        assertThat(response.body).contains("Idempotency-Key must be a UUID or ULID")
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_keys", Int::class.java)).isEqualTo(0)
    }

    @Test
    fun `treats abandoned in-progress row as new and proceeds normally`() {
        val key = "01JZABC1234567890ABCDEFGJ" // valid ULID
        jdbc.update(
            "INSERT INTO idempotency_keys (key, endpoint, created_at) VALUES (?, ?, NOW() - INTERVAL '2 minutes')",
            key,
            "POST:/v1/experiences",
        )

        // The abandoned row should be deleted and the request should proceed to the handler
        // (which returns 4xx since no real org is set up, but the filter doesn't return 409)
        val response = post("/v1/experiences", key, """{"name":"Any Experience"}""")

        assertThat(response.statusCode.value()).isNotEqualTo(409)
        // A new in-progress row was inserted for this request
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_keys WHERE key = ?", Int::class.java, key)).isEqualTo(1)
    }

    private fun post(
        path: String,
        idempotencyKey: String,
        body: String,
    ) = restTemplate.exchange(
        path,
        HttpMethod.POST,
        HttpEntity(body, HttpHeaders().apply { set("Idempotency-Key", idempotencyKey) }),
        String::class.java,
    )
}
