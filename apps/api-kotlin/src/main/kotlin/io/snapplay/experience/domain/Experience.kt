package io.snapplay.experience.domain

import java.time.Instant
import java.util.UUID

enum class ExperienceStatus { DRAFT, IN_REVIEW, PUBLISHED, PAUSED, RETIRED }

enum class HandoffMode { STORE_DEEPLINK, DYNAMIC_STOREFRONT, CART_HANDOFF, ORDER_API }

data class Experience(
    val id: UUID,
    val name: String,
    val contextTitle: String,
    val channel: String,
    val version: Int,
    val status: ExperienceStatus,
    val handoffMode: HandoffMode,
    val productCount: Int,
    val startsAt: Instant,
    val endsAt: Instant?,
)
