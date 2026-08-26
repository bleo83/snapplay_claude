package io.snapplay.experience.domain

/**
 * The provider-side landing point for a STORE_DEEPLINK handoff.
 * Both IDs come from the synced Rappi catalog and are stored immutably in each experience version.
 */
data class CommerceDestination(
    val providerStoreId: String,
    val providerCategoryId: String,
)
