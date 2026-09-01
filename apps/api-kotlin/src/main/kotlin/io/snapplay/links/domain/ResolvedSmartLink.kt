package io.snapplay.links.domain

import java.util.UUID

data class ResolvedSmartLink(
    val smartLinkId: UUID,
    val shortCode: String,
    val experienceVersionId: UUID,
    val connectionId: UUID,
    val providerStoreId: String,
    val providerCategoryId: String,
    val handoffMode: String,
    val territory: String,
)
