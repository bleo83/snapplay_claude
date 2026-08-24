package io.snapplay.organization.domain

import java.util.UUID

enum class OrganizationType {
    CONTENT_PROVIDER,
    COMMERCE_PROVIDER,
    ORCHESTRATOR,
    BRAND,
    MERCHANT,
}

enum class OrganizationStatus {
    PENDING,
    ACTIVE,
    SUSPENDED,
    CLOSED,
}

data class OrganizationProfile(
    val id: UUID,
    val legalName: String,
    val displayName: String,
    val organizationType: OrganizationType,
    val country: String,
    val defaultCurrency: String,
    val timezone: String,
    val status: OrganizationStatus,
)
