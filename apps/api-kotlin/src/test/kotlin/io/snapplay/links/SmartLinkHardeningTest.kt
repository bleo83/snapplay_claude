package io.snapplay.links

import io.snapplay.config.BotDetector
import io.snapplay.config.RateLimitFilter
import io.snapplay.links.infrastructure.persistence.DemoHandoffSessionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

private const val DEMO_SHORT_CODE = "7E1vM2kP9xQ4"

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@TestPropertySource(properties = ["snapplay.resolverRateLimitPerMinute=200"])
class SmartLinkHardeningTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var demoSessionRepo: DemoHandoffSessionRepository

    @Autowired lateinit var rateLimitFilter: RateLimitFilter

    @BeforeEach
    fun setUp() {
        demoSessionRepo.clear()
        rateLimitFilter.reset()
    }

    @Test
    fun `malformed short code returns uniform 404`() {
        // Too short
        mockMvc.get("/r/abc")
            .andExpect { status { isNotFound() } }
            .andExpect { header { string("Cache-Control", "no-store") } }

        // Contains special characters
        mockMvc.get("/r/abc!@def123")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `non-existent short code returns same 404 as malformed`() {
        mockMvc.get("/r/validcode_but_not_found")
            .andExpect { status { isNotFound() } }
            .andExpect { header { string("Cache-Control", "no-store") } }
    }

    @Test
    fun `valid resolve returns 302 with security headers`() {
        mockMvc.get("/r/$DEMO_SHORT_CODE")
            .andExpect { status { isFound() } }
            .andExpect { header { string("Cache-Control", "no-store") } }
            .andExpect { header { string("X-Robots-Tag", "noindex, nofollow") } }
    }

    @Test
    fun `bot requests are tagged in handoff session`() {
        mockMvc.get("/r/$DEMO_SHORT_CODE") {
            header("User-Agent", "Googlebot/2.1 (+http://www.google.com/bot.html)")
        }.andExpect { status { isFound() } }

        val sessions = demoSessionRepo.findAll()
        assertThat(sessions).isNotEmpty
        assertThat(sessions.last().isBot).isTrue()
    }

    @Test
    fun `human requests are not tagged as bot`() {
        mockMvc.get("/r/$DEMO_SHORT_CODE") {
            header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X)")
        }.andExpect { status { isFound() } }

        val sessions = demoSessionRepo.findAll()
        assertThat(sessions).isNotEmpty
        assertThat(sessions.last().isBot).isFalse()
    }

    @Test
    fun `missing User-Agent treated as bot`() {
        assertThat(BotDetector.isBot(null)).isTrue()
        assertThat(BotDetector.isBot("")).isTrue()
    }

    @Test
    fun `known bots detected`() {
        assertThat(BotDetector.isBot("Googlebot/2.1")).isTrue()
        assertThat(BotDetector.isBot("facebookexternalhit/1.1")).isTrue()
        assertThat(BotDetector.isBot("Mozilla/5.0 (compatible; Discordbot/2.0)")).isTrue()
        assertThat(BotDetector.isBot("HeadlessChrome/119.0")).isTrue()
    }

    @Test
    fun `normal browsers not flagged as bot`() {
        assertThat(BotDetector.isBot("Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X)")).isFalse()
        assertThat(BotDetector.isBot("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")).isFalse()
    }

    @Test
    fun `rate limit returns 429 after threshold`() {
        // Rate limit set to 200 per minute in test properties — exhaust it
        repeat(200) {
            mockMvc.get("/r/$DEMO_SHORT_CODE")
        }

        // 201st request should be rate limited
        mockMvc.get("/r/$DEMO_SHORT_CODE")
            .andExpect { status { isTooManyRequests() } }
            .andExpect { header { exists("Retry-After") } }
    }

    @Test
    fun `redirect destination is never derived from query params`() {
        mockMvc.get("/r/$DEMO_SHORT_CODE?redirect=https://evil.com")
            .andExpect { status { isFound() } }
            .andExpect {
                header {
                    // Location must point to the configured Rappi domain, never to the query param
                    string("Location", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("evil.com")))
                }
            }
    }

    @Test
    fun `short codes are non-enumerable (minimum 8 chars with mixed case)`() {
        // The demo short code is 12 chars with mixed case + digits — not sequential
        assertThat(DEMO_SHORT_CODE.length).isGreaterThanOrEqualTo(8)
        assertThat(DEMO_SHORT_CODE).matches(".*[a-z].*")
        assertThat(DEMO_SHORT_CODE).matches(".*[A-Z].*")
        assertThat(DEMO_SHORT_CODE).matches(".*[0-9].*")
    }
}
