package io.snapplay.partner

import io.snapplay.partner.domain.OrderStateMachine
import io.snapplay.partner.domain.ProviderOrderStatus
import io.snapplay.partner.domain.ProviderOrderStatus.ARRIVED_AT_DESTINATION
import io.snapplay.partner.domain.ProviderOrderStatus.CANCELLED
import io.snapplay.partner.domain.ProviderOrderStatus.CONFIRMED
import io.snapplay.partner.domain.ProviderOrderStatus.COURIER_ASSIGNED
import io.snapplay.partner.domain.ProviderOrderStatus.DELIVERED
import io.snapplay.partner.domain.ProviderOrderStatus.FAILED
import io.snapplay.partner.domain.ProviderOrderStatus.NEAR_DESTINATION
import io.snapplay.partner.domain.ProviderOrderStatus.PARTIALLY_REFUNDED
import io.snapplay.partner.domain.ProviderOrderStatus.PICKED_UP
import io.snapplay.partner.domain.ProviderOrderStatus.PLACED
import io.snapplay.partner.domain.ProviderOrderStatus.PREPARING
import io.snapplay.partner.domain.ProviderOrderStatus.REFUNDED
import io.snapplay.partner.domain.ProviderOrderStatus.REJECTED
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OrderStateMachineTest {
    private fun allows(
        from: ProviderOrderStatus,
        to: ProviderOrderStatus,
    ) = OrderStateMachine.isForwardTransition(from, to)

    // --- Normal forward progression ---

    @Test
    fun `PLACED to CONFIRMED is allowed`() = assertThat(allows(PLACED, CONFIRMED)).isTrue()

    @Test
    fun `CONFIRMED to PREPARING is allowed`() = assertThat(allows(CONFIRMED, PREPARING)).isTrue()

    @Test
    fun `PICKED_UP to DELIVERED is allowed (skip NEAR_DESTINATION)`() =
        assertThat(allows(PICKED_UP, DELIVERED)).isTrue()

    @Test
    fun `PLACED to DELIVERED is allowed (entire flow skip)`() =
        assertThat(allows(PLACED, DELIVERED)).isTrue()

    @Test
    fun `ARRIVED_AT_DESTINATION to DELIVERED is allowed`() =
        assertThat(allows(ARRIVED_AT_DESTINATION, DELIVERED)).isTrue()

    // --- Refund progression ---

    @Test
    fun `DELIVERED to PARTIALLY_REFUNDED is allowed`() =
        assertThat(allows(DELIVERED, PARTIALLY_REFUNDED)).isTrue()

    @Test
    fun `DELIVERED to REFUNDED is allowed`() =
        assertThat(allows(DELIVERED, REFUNDED)).isTrue()

    @Test
    fun `PARTIALLY_REFUNDED to REFUNDED is allowed`() =
        assertThat(allows(PARTIALLY_REFUNDED, REFUNDED)).isTrue()

    // --- Interruption events allowed from any main-flow state ---

    @Test
    fun `PLACED to CANCELLED is allowed`() = assertThat(allows(PLACED, CANCELLED)).isTrue()

    @Test
    fun `COURIER_ASSIGNED to CANCELLED is allowed`() =
        assertThat(allows(COURIER_ASSIGNED, CANCELLED)).isTrue()

    @Test
    fun `PICKED_UP to CANCELLED is allowed`() = assertThat(allows(PICKED_UP, CANCELLED)).isTrue()

    @Test
    fun `CONFIRMED to REJECTED is allowed`() = assertThat(allows(CONFIRMED, REJECTED)).isTrue()

    @Test
    fun `PREPARING to FAILED is allowed`() = assertThat(allows(PREPARING, FAILED)).isTrue()

    // --- Backward transitions are rejected ---

    @Test
    fun `CONFIRMED to PLACED is rejected`() = assertThat(allows(CONFIRMED, PLACED)).isFalse()

    @Test
    fun `DELIVERED to PICKED_UP is rejected`() = assertThat(allows(DELIVERED, PICKED_UP)).isFalse()

    @Test
    fun `NEAR_DESTINATION to CONFIRMED is rejected`() =
        assertThat(allows(NEAR_DESTINATION, CONFIRMED)).isFalse()

    // --- Terminal states block further transitions ---

    @Test
    fun `CANCELLED to CONFIRMED is rejected`() = assertThat(allows(CANCELLED, CONFIRMED)).isFalse()

    @Test
    fun `REJECTED to DELIVERED is rejected`() = assertThat(allows(REJECTED, DELIVERED)).isFalse()

    @Test
    fun `REFUNDED to PARTIALLY_REFUNDED is rejected`() =
        assertThat(allows(REFUNDED, PARTIALLY_REFUNDED)).isFalse()

    @Test
    fun `DELIVERED to CANCELLED is rejected (already delivered)`() =
        assertThat(allows(DELIVERED, CANCELLED)).isFalse()

    // --- NEAR_DESTINATION is not inferred ---

    @Test
    fun `NEAR_DESTINATION to NEAR_DESTINATION is rejected (same state)`() =
        assertThat(allows(NEAR_DESTINATION, NEAR_DESTINATION)).isFalse()

    // --- Refund not allowed from non-delivered states ---

    @Test
    fun `CONFIRMED to PARTIALLY_REFUNDED is rejected`() =
        assertThat(allows(CONFIRMED, PARTIALLY_REFUNDED)).isFalse()

    @Test
    fun `CANCELLED to REFUNDED is rejected`() = assertThat(allows(CANCELLED, REFUNDED)).isFalse()
}
