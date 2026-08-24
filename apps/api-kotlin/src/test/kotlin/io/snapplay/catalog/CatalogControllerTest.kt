package io.snapplay.catalog

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class CatalogControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET products returns all 6 demo products`() {
        mockMvc.get("/v1/catalog/products")
            .andExpect {
                status { isOk() }
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.total") { value(6) }
                jsonPath("$.source") { value("demo") }
                jsonPath("$.items.length()") { value(6) }
                jsonPath("$.items[0].providerProductId") { isString() }
                jsonPath("$.items[0].categories") { isArray() }
            }
    }

    @Test
    fun `GET products filters by category`() {
        mockMvc.get("/v1/catalog/products?category=movie-night")
            .andExpect {
                status { isOk() }
                // Toy Story cup + Pochoclos = 2
                jsonPath("$.total") { value(2) }
            }
    }

    @Test
    fun `GET products filters by text query`() {
        mockMvc.get("/v1/catalog/products?q=vaso")
            .andExpect {
                status { isOk() }
                // Vaso coleccionable Toy Story + Vaso Moana = 2
                jsonPath("$.total") { value(2) }
            }
    }

    @Test
    fun `GET products filters by status`() {
        mockMvc.get("/v1/catalog/products?status=ACTIVE")
            .andExpect {
                status { isOk() }
                jsonPath("$.total") { value(6) }
            }
    }

    @Test
    fun `GET products returns 422 for unknown status value`() {
        mockMvc.get("/v1/catalog/products?status=UNKNOWN")
            .andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.status") { value(422) }
            }
    }
}
