package io.snapplay.links

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
class SmartLinkControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET smart-links returns all 2 demo smart links`() {
        mockMvc.get("/v1/smart-links")
            .andExpect {
                status { isOk() }
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.items.length()") { value(2) }
                jsonPath("$.items[0].shortCode") { value("7E1vM2kP9xQ4") }
                jsonPath("$.items[0].experienceName") { value("Toy Story Movie Night") }
                jsonPath("$.items[0].placementKey") { value("disney-plus.toy-story.endcard") }
                jsonPath("$.items[0].status") { value("ACTIVE") }
                jsonPath("$.items[0].scans") { value(18742) }
                jsonPath("$.items[0].conversions") { value(1248) }
            }
    }

    @Test
    fun `GET smart-links returns url with correct base`() {
        mockMvc.get("/v1/smart-links")
            .andExpect {
                status { isOk() }
                jsonPath("$.items[0].url") { isString() }
                jsonPath("$.items[1].shortCode") { value("4G8zN7bK2mL6") }
                jsonPath("$.items[1].experienceName") { value("Moana Family Night") }
                jsonPath("$.items[1].status") { value("ACTIVE") }
                jsonPath("$.items[1].scans") { value(9836) }
                jsonPath("$.items[1].conversions") { value(771) }
            }
    }
}
