package io.snapplay.outbox

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Minimal operational tool: retry a dead-lettered outbox event. */
@RestController
@RequestMapping("/v1/admin/outbox")
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "false", matchIfMissing = true)
class OutboxAdminController(
    private val workerService: OutboxWorkerService,
) {
    @PostMapping("/events/{id}/retry")
    fun retry(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> =
        if (workerService.retryDeadLetter(id)) {
            ResponseEntity.accepted().build()
        } else {
            ResponseEntity.notFound().build()
        }
}
