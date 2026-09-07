package io.snapplay.config

/**
 * Detects known bots from User-Agent strings.
 * Does not store or fingerprint — simply classifies the request for metrics separation.
 */
object BotDetector {
    private val BOT_PATTERNS =
        listOf(
            "bot", "crawl", "spider", "slurp", "mediapartners",
            "facebookexternalhit", "twitterbot", "linkedinbot",
            "whatsapp", "telegrambot", "discordbot", "applebot",
            "bingpreview", "googlebot", "yandexbot", "baiduspider",
            "duckduckbot", "seznambot", "ia_archiver", "archive.org_bot",
            "semrushbot", "ahrefsbot", "dotbot", "petalbot",
            "headlesschrome", "phantomjs", "selenium", "puppeteer",
        )

    fun isBot(userAgent: String?): Boolean {
        if (userAgent.isNullOrBlank()) return true // missing UA treated as bot
        val lower = userAgent.lowercase()
        return BOT_PATTERNS.any { lower.contains(it) }
    }
}
