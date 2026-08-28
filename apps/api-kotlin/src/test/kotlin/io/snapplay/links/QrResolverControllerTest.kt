package io.snapplay.links

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

// Published demo experience → smart link "7E1vM2kP9xQ4" resolves to a Rappi store URL
private const val ACTIVE_SHORT_CODE = "7E1vM2kP9xQ4"

// Moana link is ACTIVE but its experience is DRAFT → resolver must return 404
private const val DRAFT_EXPERIENCE_SHORT_CODE = "4G8zN7bK2mL6"

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class QrResolverControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET r shortCode returns 302 redirect for active published link`() {
        mockMvc
            .get("/r/$ACTIVE_SHORT_CODE")
            .andExpect {
                status { isFound() }
                header { string("Location", "https://www.rappi.com.ar/stores/900000?category=2000") }
                header { string("Cache-Control", "no-store") }
            }
    }

    @Test
    fun `GET r shortCode returns 404 for unknown short code`() {
        mockMvc
            .get("/r/unknownCode000")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `GET r shortCode returns 404 for link backed by draft experience`() {
        mockMvc
            .get("/r/$DRAFT_EXPERIENCE_SHORT_CODE")
            .andExpect {
                status { isNotFound() }
            }
    }
}
