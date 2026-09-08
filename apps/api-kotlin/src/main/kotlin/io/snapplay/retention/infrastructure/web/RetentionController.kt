package io.snapplay.retention.infrastructure.web

import io.snapplay.retention.domain.DataClass
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/admin/retention")
class RetentionController {
    @GetMapping("/policies")
    fun policies(): List<RetentionPolicyDto> =
        DataClass.entries.map {
            RetentionPolicyDto(
                dataClass = it.name,
                table = it.tableName,
                owner = it.owner,
                purpose = it.purpose,
                retentionDays = it.retention.toDays(),
                anonymizable = it.anonymizable,
            )
        }
}

data class RetentionPolicyDto(
    val dataClass: String,
    val table: String,
    val owner: String,
    val purpose: String,
    val retentionDays: Long,
    val anonymizable: Boolean,
)
