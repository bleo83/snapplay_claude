package io.snapplay.links

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
import org.springframework.test.web.servlet.post

// Published demo experience ID (Toy Story Movie Night)
private const val PUBLISHED_EXPERIENCE_ID = "184a63fe-4420-46de-9894-b16110364263"

// Draft demo experience ID (Hulu Classics)
private const val DRAFT_EXPERIENCE_ID = "53d116c9-e676-4c49-9a2b-371d0b4d5c1a"

// Existing demo short code
private const val DEMO_SHORT_CODE = "7E1vM2kP9xQ4"

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class SmartLinkControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    // ── List (read-only, run first to avoid count drift from POST tests) ────────

    @Test
    @Order(1)
    fun `GET smart-links returns all 2 demo smart links`() {
        mockMvc
            .get("/v1/smart-links")
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
    @Order(2)
    fun `GET smart-links returns url with correct base`() {
        mockMvc
            .get("/v1/smart-links")
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

    // ── QR download ────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    fun `GET smart-links shortCode qr returns PNG image`() {
        mockMvc
            .get("/v1/smart-links/$DEMO_SHORT_CODE/qr")
            .andExpect {
                status { isOk() }
                content { contentType(MediaType.IMAGE_PNG) }
                header { string("Content-Disposition", "attachment; filename=\"$DEMO_SHORT_CODE.png\"") }
            }
    }

    @Test
    @Order(4)
    fun `GET smart-links qr returns 404 for unknown short code`() {
        mockMvc
            .get("/v1/smart-links/unknownCode999/qr")
            .andExpect {
                status { isNotFound() }
            }
    }

    // ── Create ─────────────────────────────────────────────────────────────────

    @Test
    @Order(10)
    fun `POST smart-links creates link for PUBLISHED experience`() {
        mockMvc
            .post("/v1/smart-links") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"experienceId":"$PUBLISHED_EXPERIENCE_ID","placementKey":"disney-plus.toy-story.banner"}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.shortCode") { isString() }
                jsonPath("$.url") { isString() }
                jsonPath("$.experienceName") { value("Toy Story Movie Night") }
                jsonPath("$.placementKey") { value("disney-plus.toy-story.banner") }
                jsonPath("$.status") { value("ACTIVE") }
                jsonPath("$.scans") { value(0) }
                jsonPath("$.conversions") { value(0) }
            }
    }

    @Test
    @Order(11)
    fun `POST smart-links returns 422 for DRAFT experience`() {
        mockMvc
            .post("/v1/smart-links") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"experienceId":"$DRAFT_EXPERIENCE_ID","placementKey":"hulu.classics.banner"}"""
            }.andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.status") { value(422) }
            }
    }

    @Test
    @Order(12)
    fun `POST smart-links returns 422 for invalid placement key`() {
        mockMvc
            .post("/v1/smart-links") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"experienceId":"$PUBLISHED_EXPERIENCE_ID","placementKey":"Invalid Key!"}"""
            }.andExpect {
                status { isUnprocessableEntity() }
            }
    }

    @Test
    @Order(13)
    fun `POST smart-links returns 404 for unknown experience`() {
        mockMvc
            .post("/v1/smart-links") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"experienceId":"00000000-0000-0000-0000-000000000000","placementKey":"test.placement"}"""
            }.andExpect {
                status { isNotFound() }
            }
    }
}
