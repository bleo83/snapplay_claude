package io.snapplay.partner.infrastructure.persistence

import io.snapplay.partner.application.port.output.ReconciliationMismatchRepository
import io.snapplay.partner.domain.ReconciliationMismatch
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.concurrent.CopyOnWriteArrayList

@Repository
@ConditionalOnProperty(name = ["snapplay.demo"], havingValue = "true")
class DemoReconciliationMismatchRepository : ReconciliationMismatchRepository {
    private val _mismatches = CopyOnWriteArrayList<ReconciliationMismatch>()
    val mismatches: List<ReconciliationMismatch> get() = _mismatches

    fun clear() = _mismatches.clear()

    override fun save(mismatch: ReconciliationMismatch) {
        _mismatches.add(mismatch)
    }
}
