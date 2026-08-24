package io.snapplay.common

import com.fasterxml.jackson.annotation.JsonProperty

data class ProblemDetails(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String,
    @JsonProperty("request_id")
    val requestId: String,
    val errors: List<Any>? = null,
)
