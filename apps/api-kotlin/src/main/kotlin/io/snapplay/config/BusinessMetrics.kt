package io.snapplay.config

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralised business metrics for the pilot.
 * All metrics use the `snapplay.` prefix for easy filtering in Prometheus/Grafana.
 */
@Component
class BusinessMetrics(
    private val registry: MeterRegistry,
) {
    private val counters = ConcurrentHashMap<String, Counter>()
    private val timers = ConcurrentHashMap<String, Timer>()

    // --- QR Resolver ---

    fun recordScan(
        connectionId: String,
        isBot: Boolean,
    ) {
        counter("snapplay.resolver.scans", "connection" to connectionId, "bot" to isBot.toString()).increment()
    }

    fun resolverTimer(): Timer.Sample = Timer.start(registry)

    fun recordResolverLatency(
        sample: Timer.Sample,
        success: Boolean,
    ) {
        sample.stop(timer("snapplay.resolver.latency", "success" to success.toString()))
    }

    // --- Webhook Ingress ---

    fun recordWebhookIngested(eventType: String) {
        counter("snapplay.webhook.ingested", "event_type" to eventType).increment()
    }

    fun recordWebhookRejected(reason: String) {
        counter("snapplay.webhook.rejected", "reason" to reason).increment()
    }

    // --- Outbox ---

    fun recordOutboxProcessed(eventType: String) {
        counter("snapplay.outbox.processed", "event_type" to eventType).increment()
    }

    fun recordOutboxDeadLettered(eventType: String) {
        counter("snapplay.outbox.dead_lettered", "event_type" to eventType).increment()
    }

    // --- Milestone Delivery ---

    fun recordMilestoneDelivered() {
        counter("snapplay.milestone.delivered").increment()
    }

    fun recordMilestoneDeadLettered() {
        counter("snapplay.milestone.dead_lettered").increment()
    }

    // --- Reconciliation ---

    fun recordReconciliationMismatch(type: String) {
        counter("snapplay.reconciliation.mismatches", "type" to type).increment()
    }

    fun recordReconciliationRecovered() {
        counter("snapplay.reconciliation.recovered").increment()
    }

    // --- Auth ---

    fun recordAuthDenied(reason: String) {
        counter("snapplay.auth.denied", "reason" to reason).increment()
    }

    // --- Helpers ---

    private fun counter(
        name: String,
        vararg tags: Pair<String, String>,
    ): Counter {
        val key = "$name:${tags.joinToString(",") { "${it.first}=${it.second}" }}"
        return counters.getOrPut(key) {
            val builder = Counter.builder(name)
            tags.forEach { (k, v) -> builder.tag(k, v) }
            builder.register(registry)
        }
    }

    private fun timer(
        name: String,
        vararg tags: Pair<String, String>,
    ): Timer {
        val key = "$name:${tags.joinToString(",") { "${it.first}=${it.second}" }}"
        return timers.getOrPut(key) {
            val builder = Timer.builder(name)
            tags.forEach { (k, v) -> builder.tag(k, v) }
            builder.register(registry)
        }
    }
}
