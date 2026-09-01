package io.snapplay.rappi

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@TestPropertySource(properties = ["snapplay.rappi-mock-enabled=true"])
class RappiMockControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    // --- Landing page ---

    @Test
    fun `landing page returns 200 for known store and category`() {
        mockMvc
            .get("/mock/rappi/tiendas/900000?categoriaId=2000&snp_tk=test-token")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
                content { string(org.hamcrest.Matchers.containsString("Toy Story Movie Night")) }
                content { string(org.hamcrest.Matchers.containsString("Snacks &amp; Drinks")) }
                content { string(org.hamcrest.Matchers.containsString("test-token")) }
            }
    }

    @Test
    fun `landing page returns 404 for unknown store`() {
        mockMvc
            .get("/mock/rappi/tiendas/unknown-store")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `landing page shows error when category does not belong to store`() {
        mockMvc
            .get("/mock/rappi/tiendas/900000?categoriaId=9999")
            .andExpect {
                status { isOk() }
                // Page renders but shows an error section for the invalid category
                content { string(org.hamcrest.Matchers.containsString("does not belong to this store")) }
            }
    }

    // --- Order emit ---

    @Test
    fun `emit returns 404 when store is not in catalog`() {
        mockMvc
            .post("/mock/rappi/orders/emit") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "storeId": "unknown-store",
                      "categoryId": "2000",
                      "trackingToken": "tok-abc",
                      "scenario": "DELIVERED"
                    }
                    """.trimIndent()
            }
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `emit returns 422 when category does not belong to store`() {
        mockMvc
            .post("/mock/rappi/orders/emit") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "storeId": "900000",
                      "categoryId": "5001",
                      "trackingToken": "tok-abc",
                      "scenario": "DELIVERED"
                    }
                    """.trimIndent()
            }
            .andExpect { status { isUnprocessableEntity() } }
    }

    @Test
    fun `emit returns result for valid store and category`() {
        mockMvc
            .post("/mock/rappi/orders/emit") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "storeId": "900000",
                      "categoryId": "2000",
                      "trackingToken": "tok-abc123",
                      "scenario": "DELIVERED"
                    }
                    """.trimIndent()
            }
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.emittedPayload.event_type") { value("ORDER_DELIVERED") }
                jsonPath("$.emittedPayload.store_id") { value("900000") }
                jsonPath("$.emittedPayload.category_id") { value("2000") }
                jsonPath("$.emittedPayload.tracking_token") { value("tok-abc123") }
                jsonPath("$.signature") { value(org.hamcrest.Matchers.startsWith("sha256=")) }
            }
    }

    @Test
    fun `emit PARTIAL_REFUND includes refunded_amount in payload`() {
        mockMvc
            .post("/mock/rappi/orders/emit") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "storeId": "900000",
                      "categoryId": "2000",
                      "trackingToken": "tok-refund",
                      "scenario": "PARTIAL_REFUND"
                    }
                    """.trimIndent()
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.emittedPayload.event_type") { value("ORDER_PARTIAL_REFUND") }
                jsonPath("$.emittedPayload.refunded_amount") { exists() }
            }
    }

    @Test
    fun `emit DUPLICATE returns same order_id as previous emit`() {
        // First emit — establishes the order_id for this tracking token
        val result1 =
            mockMvc
                .post("/mock/rappi/orders/emit") {
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """
                        {
                          "storeId": "900000",
                          "categoryId": "2000",
                          "trackingToken": "tok-dup",
                          "scenario": "DELIVERED"
                        }
                        """.trimIndent()
                }
                .andExpect { status { isOk() } }
                .andReturn()

        val body1 = result1.response.contentAsString
        val orderId1 =
            com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(body1)["emittedPayload"]["order_id"]
                .asText()

        // Duplicate emit — must reuse the same order_id
        mockMvc
            .post("/mock/rappi/orders/emit") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "storeId": "900000",
                      "categoryId": "2000",
                      "trackingToken": "tok-dup",
                      "scenario": "DUPLICATE"
                    }
                    """.trimIndent()
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.emittedPayload.order_id") { value(orderId1) }
            }
    }
}
