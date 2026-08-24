package io.snapplay.links.domain

import java.util.UUID

enum class SmartLinkStatus { ACTIVE, PAUSED, EXPIRED, RETIRED }

data class SmartLink(
    val id: UUID,
    val shortCode: String,
    val url: String,
    val experienceName: String,
    val placementKey: String,
    val status: SmartLinkStatus,
    val scans: Int,
    val conversions: Int,
)
