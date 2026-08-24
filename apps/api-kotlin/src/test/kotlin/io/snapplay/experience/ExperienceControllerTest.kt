package io.snapplay.experience

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class ExperienceControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET experiences returns all 4 demo experiences`() {
        mockMvc
            .get("/v1/experiences")
            .andExpect {
                status { isOk() }
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.items.length()") { value(4) }
                jsonPath("$.items[0].name") { value("Toy Story Movie Night") }
                jsonPath("$.items[0].status") { value("PUBLISHED") }
                jsonPath("$.items[0].handoffMode") { value("DYNAMIC_STOREFRONT") }
                jsonPath("$.items[0].channel") { value("Disney+") }
            }
    }

    @Test
    fun `POST experience creates and returns new draft experience`() {
        mockMvc
            .post("/v1/experiences") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "name": "Test Night",
                      "contextTitle": "Toy Story",
                      "channel": "Disney+",
                      "handoffMode": "STORE_DEEPLINK",
                      "productCount": 2,
                      "startsAt": "2026-09-01T00:00:00Z",
                      "endsAt": null
                    }
                    """.trimIndent()
            }.andExpect {
                status { isCreated() }
                jsonPath("$.name") { value("Test Night") }
                jsonPath("$.status") { value("DRAFT") }
                jsonPath("$.version") { value(1) }
                jsonPath("$.handoffMode") { value("STORE_DEEPLINK") }
            }
    }

    @Test
    fun `POST experience returns 422 for name too short`() {
        mockMvc
            .post("/v1/experiences") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "name": "AB",
                      "contextTitle": "Toy Story",
                      "channel": "Disney+",
                      "handoffMode": "STORE_DEEPLINK",
                      "productCount": 2,
                      "startsAt": "2026-09-01T00:00:00Z"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.status") { value(422) }
            }
    }

    @Test
    fun `POST experience returns 422 for product count over limit`() {
        mockMvc
            .post("/v1/experiences") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "name": "Test Night",
                      "contextTitle": "Toy Story",
                      "channel": "Disney+",
                      "handoffMode": "STORE_DEEPLINK",
                      "productCount": 101,
                      "startsAt": "2026-09-01T00:00:00Z"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isUnprocessableEntity() }
            }
    }

    @Test
    fun `GET experiences list grows after POST`() {
        val beforeSize =
            mockMvc
                .get("/v1/experiences")
                .andReturn()
                .response.contentAsString
                .let { it.substringAfter("\"items\":[").count { c -> c == '{' } }

        mockMvc
            .post("/v1/experiences") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "name": "New Experience",
                      "contextTitle": "Toy Story",
                      "channel": "Disney+",
                      "handoffMode": "CART_HANDOFF",
                      "productCount": 0,
                      "startsAt": "2026-11-01T00:00:00Z"
                    }
                    """.trimIndent()
            }.andExpect { status { isCreated() } }

        mockMvc
            .get("/v1/experiences")
            .andExpect {
                jsonPath("$.items.length()") { value(beforeSize + 1) }
            }
    }
}
