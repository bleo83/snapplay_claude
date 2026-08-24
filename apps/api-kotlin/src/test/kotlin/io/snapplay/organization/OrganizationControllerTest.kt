package io.snapplay.organization

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class OrganizationControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET organization returns demo organization`() {
        mockMvc
            .get("/v1/organization")
            .andExpect {
                status { isOk() }
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.id") { value("50d2d7eb-c8fd-42df-bbb0-0ea0e2271090") }
                jsonPath("$.legalName") { value("Disney Streaming Services Argentina S.A.") }
                jsonPath("$.displayName") { value("Disney Argentina") }
                jsonPath("$.organizationType") { value("CONTENT_PROVIDER") }
                jsonPath("$.country") { value("AR") }
                jsonPath("$.defaultCurrency") { value("ARS") }
                jsonPath("$.status") { value("ACTIVE") }
            }
    }

    @Test
    fun `PATCH organization updates fields and returns updated profile`() {
        mockMvc
            .patch("/v1/organization") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "legalName": "Disney Updated S.A.",
                      "displayName": "Disney Updated",
                      "country": "AR",
                      "defaultCurrency": "ARS",
                      "timezone": "America/Argentina/Buenos_Aires"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.legalName") { value("Disney Updated S.A.") }
                jsonPath("$.displayName") { value("Disney Updated") }
            }
    }

    @Test
    fun `PATCH organization returns 422 for invalid country code`() {
        mockMvc
            .patch("/v1/organization") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "legalName": "Disney S.A.",
                      "displayName": "Disney",
                      "country": "argentina",
                      "defaultCurrency": "ARS",
                      "timezone": "America/Argentina/Buenos_Aires"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.status") { value(422) }
            }
    }
}
