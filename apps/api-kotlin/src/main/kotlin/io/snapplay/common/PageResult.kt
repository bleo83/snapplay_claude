package io.snapplay.common

data class PageResult<T>(
    val items: List<T>,
    val nextCursor: String?,
)
