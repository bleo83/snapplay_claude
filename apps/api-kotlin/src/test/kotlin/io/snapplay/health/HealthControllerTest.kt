package io.snapplay.health

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("demo")
class HealthControllerTest {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Test
    fun `GET health returns 200 with status ok and mode demo`() {
        val response = restTemplate.getForEntity("/health", Map::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isNotNull
        assertThat(response.body!!["status"]).isEqualTo("ok")
        assertThat(response.body!!["mode"]).isEqualTo("demo")
        assertThat(response.body!!["timestamp"]).isNotNull()
    }
}
