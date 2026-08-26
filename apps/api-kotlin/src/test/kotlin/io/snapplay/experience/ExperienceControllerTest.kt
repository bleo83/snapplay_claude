package io.snapplay.experience

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

private const val DEMO_CONNECTION_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ExperienceControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    private val mapper = jacksonObjectMapper()

    /** Creates a new DRAFT experience and returns its id. */
    private fun createDraft(name: String): String {
        val body =
            mockMvc
                .post("/v1/experiences") {
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """
                        {
                          "name": "$name",
                          "contextTitle": "Toy Story",
                          "connectionId": "$DEMO_CONNECTION_ID",
                          "territory": "AR",
                          "destination": {"providerStoreId": "s", "providerCategoryId": "c"},
                          "handoffMode": "STORE_DEEPLINK",
                          "productCount": 0,
                          "startsAt": "2026-09-01T00:00:00Z"
                        }
                        """.trimIndent()
                }.andExpect { status { isCreated() } }
                .andReturn().response.contentAsString
        return mapper.readTree(body).get("id").asText()
    }

    /** Creates a DRAFT and immediately publishes it, returns the id. */
    private fun createPublished(name: String): String {
        val id = createDraft(name)
        mockMvc.patch("/v1/experiences/$id/publish").andExpect { status { isOk() } }
        return id
    }

    // ── Basic CRUD ─────────────────────────────────────────────────────────────

    @Test
    @Order(1)
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
    @Order(2)
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
    @Order(3)
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
    @Order(4)
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
    @Order(5)
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
    @Order(6)
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

    // ── Lifecycle: publish ─────────────────────────────────────────────────────

    @Test
    @Order(10)
    fun `PATCH publish transitions DRAFT experience to PUBLISHED`() {
        val id = createDraft("Lifecycle Publish Test")
        mockMvc
            .patch("/v1/experiences/$id/publish")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("PUBLISHED") }
            }
    }

    @Test
    @Order(11)
    fun `PATCH publish returns 422 when experience is already PUBLISHED`() {
        val id = createPublished("Already Published Test")
        mockMvc
            .patch("/v1/experiences/$id/publish")
            .andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.status") { value(422) }
            }
    }

    @Test
    @Order(12)
    fun `PATCH publish returns 404 for unknown experience id`() {
        mockMvc
            .patch("/v1/experiences/00000000-0000-0000-0000-000000000000/publish")
            .andExpect {
                status { isNotFound() }
            }
    }

    // ── Lifecycle: pause ───────────────────────────────────────────────────────

    @Test
    @Order(20)
    fun `PATCH pause transitions PUBLISHED experience to PAUSED`() {
        val id = createPublished("Pause Transition Test")
        mockMvc
            .patch("/v1/experiences/$id/pause")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("PAUSED") }
            }
    }

    @Test
    @Order(21)
    fun `PATCH pause returns 422 when experience is not PUBLISHED`() {
        val id = createDraft("Pause From Draft Test")
        mockMvc
            .patch("/v1/experiences/$id/pause")
            .andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.status") { value(422) }
            }
    }

    // ── Lifecycle: retire ──────────────────────────────────────────────────────

    @Test
    @Order(30)
    fun `PATCH retire transitions PUBLISHED experience to RETIRED`() {
        val id = createPublished("Retire Transition Test")
        mockMvc
            .patch("/v1/experiences/$id/retire")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("RETIRED") }
            }
    }

    @Test
    @Order(31)
    fun `PATCH retire returns 422 when experience is already RETIRED`() {
        val id = createPublished("Already Retired Test")
        mockMvc.patch("/v1/experiences/$id/retire").andExpect { status { isOk() } }
        mockMvc
            .patch("/v1/experiences/$id/retire")
            .andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.status") { value(422) }
            }
    }

    // ── Lifecycle: clone ───────────────────────────────────────────────────────

    @Test
    @Order(40)
    fun `POST clone creates new DRAFT copy of PUBLISHED experience`() {
        val id = createPublished("Clone Source")
        mockMvc
            .post("/v1/experiences/$id/clone")
            .andExpect {
                status { isCreated() }
                jsonPath("$.status") { value("DRAFT") }
                jsonPath("$.version") { value(1) }
                jsonPath("$.name") { value("Copy of Clone Source") }
            }
    }

    @Test
    @Order(41)
    fun `POST clone returns 422 when experience is not PUBLISHED or PAUSED`() {
        val id = createDraft("Clone From Draft Test")
        mockMvc
            .post("/v1/experiences/$id/clone")
            .andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.status") { value(422) }
            }
    }
}
