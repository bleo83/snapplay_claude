package io.snapplay.connection

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class ConnectionControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET connections returns demo Disney-Rappi connection`() {
        mockMvc
            .get("/v1/connections")
            .andExpect {
                status { isOk() }
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.items.length()") { value(1) }
                jsonPath("$.items[0].id") { value("a1b2c3d4-e5f6-7890-abcd-ef1234567890") }
                jsonPath("$.items[0].connectorKey") { value("rappi") }
                jsonPath("$.items[0].status") { value("ACTIVE") }
                jsonPath("$.items[0].environment") { value("SANDBOX") }
                jsonPath("$.nextCursor") { doesNotExist() }
            }
    }

    @Test
    fun `GET connections by id returns demo connection`() {
        mockMvc
            .get("/v1/connections/a1b2c3d4-e5f6-7890-abcd-ef1234567890")
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value("a1b2c3d4-e5f6-7890-abcd-ef1234567890") }
                jsonPath("$.name") { value("Disney Argentina ↔ Rappi SANDBOX") }
                jsonPath("$.territories[0]") { value("AR") }
            }
    }

    @Test
    fun `GET connections by unknown id returns 404`() {
        mockMvc
            .get("/v1/connections/00000000-0000-0000-0000-000000000000")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `POST connection creates and returns new pending connection`() {
        mockMvc
            .post("/v1/connections") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "name": "Disney ↔ PedidosYa SANDBOX",
                      "commerceOrganizationId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                      "connectorKey": "pedidosya",
                      "environment": "SANDBOX",
                      "territories": ["AR", "UY"],
                      "capabilities": {"catalog_sync": true},
                      "dataSharingPolicyId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isCreated() }
                jsonPath("$.name") { value("Disney ↔ PedidosYa SANDBOX") }
                jsonPath("$.status") { value("PENDING") }
                jsonPath("$.connectorKey") { value("pedidosya") }
                jsonPath("$.territories.length()") { value(2) }
            }
    }

    @Test
    fun `POST connection returns 422 for empty name`() {
        mockMvc
            .post("/v1/connections") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "name": "",
                      "commerceOrganizationId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                      "connectorKey": "pedidosya",
                      "environment": "SANDBOX",
                      "territories": ["AR"],
                      "capabilities": {},
                      "dataSharingPolicyId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.status") { value(422) }
            }
    }

    @Test
    fun `PATCH connection updates status`() {
        mockMvc
            .patch("/v1/connections/a1b2c3d4-e5f6-7890-abcd-ef1234567890") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"status": "SUSPENDED"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("SUSPENDED") }
            }
    }

    @Test
    fun `PATCH connection returns 404 for unknown id`() {
        mockMvc
            .patch("/v1/connections/00000000-0000-0000-0000-000000000000") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"status": "ACTIVE"}"""
            }.andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `GET connections list grows after POST`() {
        val beforeJson = mockMvc.get("/v1/connections").andReturn().response.contentAsString
        // Count items by splitting on "\"id\":" — one per connection object, no false positives from nested capabilities
        val before = beforeJson.split("\"id\":").size - 1

        mockMvc
            .post("/v1/connections") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "name": "New Connection",
                      "commerceOrganizationId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
                      "connectorKey": "rappi",
                      "environment": "PRODUCTION",
                      "territories": ["MX"],
                      "capabilities": {},
                      "dataSharingPolicyId": "dddddddd-dddd-dddd-dddd-dddddddddddd"
                    }
                    """.trimIndent()
            }.andExpect { status { isCreated() } }

        val afterJson = mockMvc.get("/v1/connections").andReturn().response.contentAsString
        val after = afterJson.split("\"id\":").size - 1
        assert(after == before + 1) { "Expected $before + 1 connections, got $after" }
    }
}
