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

private const val DEMO_CONNECTION_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"

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
                jsonPath("$.items[0].handoffMode") { value("STORE_DEEPLINK") }
                jsonPath("$.items[0].channel") { value("Disney+") }
                jsonPath("$.items[0].territory") { value("AR") }
                jsonPath("$.items[0].destination.providerStoreId") { value("rappi-store-ar-001") }
                jsonPath("$.items[0].destination.providerCategoryId") { value("snacks-drinks") }
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
                      "connectionId": "$DEMO_CONNECTION_ID",
                      "territory": "AR",
                      "destination": {
                        "providerStoreId": "store-001",
                        "providerCategoryId": "cat-999"
                      },
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
                jsonPath("$.territory") { value("AR") }
                jsonPath("$.destination.providerStoreId") { value("store-001") }
                jsonPath("$.destination.providerCategoryId") { value("cat-999") }
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
                      "connectionId": "$DEMO_CONNECTION_ID",
                      "territory": "AR",
                      "destination": {"providerStoreId": "s", "providerCategoryId": "c"},
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
    fun `POST experience returns 422 for invalid territory`() {
        mockMvc
            .post("/v1/experiences") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "name": "Test Night",
                      "contextTitle": "Toy Story",
                      "connectionId": "$DEMO_CONNECTION_ID",
                      "territory": "MX",
                      "destination": {"providerStoreId": "s", "providerCategoryId": "c"},
                      "handoffMode": "STORE_DEEPLINK",
                      "productCount": 0,
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
                      "connectionId": "$DEMO_CONNECTION_ID",
                      "territory": "AR",
                      "destination": {"providerStoreId": "s", "providerCategoryId": "c"},
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
        // Count items by splitting on "\"id\":" — one per experience, no false positives from nested destination
        val beforeSize =
            mockMvc
                .get("/v1/experiences")
                .andReturn()
                .response.contentAsString
                .split("\"id\":").size - 1

        mockMvc
            .post("/v1/experiences") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "name": "New Experience",
                      "contextTitle": "Toy Story",
                      "connectionId": "$DEMO_CONNECTION_ID",
                      "territory": "AR",
                      "destination": {
                        "providerStoreId": "store-001",
                        "providerCategoryId": "cat-001"
                      },
                      "handoffMode": "STORE_DEEPLINK",
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
