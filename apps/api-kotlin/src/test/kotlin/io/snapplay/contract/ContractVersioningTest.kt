package io.snapplay.contract

import io.snapplay.contract.application.port.input.ActivateContractUseCase
import io.snapplay.contract.application.port.input.ApproveContractVersionUseCase
import io.snapplay.contract.application.port.input.CreateContractUseCase
import io.snapplay.contract.application.port.input.CreateContractVersionUseCase
import io.snapplay.contract.application.port.input.GetEffectiveVersionUseCase
import io.snapplay.contract.domain.ContractStatus
import io.snapplay.contract.domain.ContractVersionStatus
import io.snapplay.contract.infrastructure.persistence.DemoContractRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private val PUBLISHER_ORG = UUID.randomUUID()
private val COMMERCE_ORG = UUID.randomUUID()
private val POLICY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222")
private val APPROVER = UUID.randomUUID()

@SpringBootTest
@ActiveProfiles("demo")
class ContractVersioningTest {
    @Autowired lateinit var createContract: CreateContractUseCase

    @Autowired lateinit var activateContract: ActivateContractUseCase

    @Autowired lateinit var createVersion: CreateContractVersionUseCase

    @Autowired lateinit var approveVersion: ApproveContractVersionUseCase

    @Autowired lateinit var getEffective: GetEffectiveVersionUseCase

    @Autowired lateinit var demoRepo: DemoContractRepository

    @BeforeEach
    fun setUp() = demoRepo.clear()

    private val now = Instant.now()

    @Test
    fun `contract created in DRAFT status`() {
        val contract = createContract.create("Disney x Rappi AR", PUBLISHER_ORG, COMMERCE_ORG, listOf("AR"), "America/Argentina/Buenos_Aires", 1)
        assertThat(contract.status).isEqualTo(ContractStatus.DRAFT)
        assertThat(contract.territories).containsExactly("AR")
    }

    @Test
    fun `version created in DRAFT, approved becomes immutable`() {
        val contract = createContract.create("Test", PUBLISHER_ORG, COMMERCE_ORG, listOf("AR"), "UTC", 1)
        val version = createVersion.create(contract.id, "ARS", now, null, "DELIVERED", 7, "{}", POLICY_ID)

        assertThat(version.status).isEqualTo(ContractVersionStatus.DRAFT)

        val approved = approveVersion.approve(version.id, APPROVER)
        assertThat(approved.status).isEqualTo(ContractVersionStatus.APPROVED)
        assertThat(approved.approvedBy).isEqualTo(APPROVER)
        assertThat(approved.approvedAt).isNotNull()

        // Second approval attempt fails — version is immutable
        assertThatThrownBy { approveVersion.approve(version.id, APPROVER) }
            .hasMessageContaining("already approved")
    }

    @Test
    fun `overlapping version rejected at creation when approved version exists`() {
        val contract = createContract.create("Overlap Test", PUBLISHER_ORG, COMMERCE_ORG, listOf("AR"), "UTC", 1)

        val v1 = createVersion.create(contract.id, "ARS", now, now.plus(30, ChronoUnit.DAYS), "DELIVERED", 0, "{}", POLICY_ID)
        approveVersion.approve(v1.id, APPROVER)

        // Creating v2 that overlaps with approved v1 is blocked
        assertThatThrownBy {
            createVersion.create(contract.id, "ARS", now.plus(15, ChronoUnit.DAYS), now.plus(45, ChronoUnit.DAYS), "DELIVERED", 0, "{}", POLICY_ID)
        }.hasMessageContaining("overlaps")
    }

    @Test
    fun `non-overlapping sequential versions are allowed`() {
        val contract = createContract.create("Sequential Test", PUBLISHER_ORG, COMMERCE_ORG, listOf("AR"), "UTC", 1)
        val v1End = now.plus(30, ChronoUnit.DAYS)

        val v1 = createVersion.create(contract.id, "ARS", now, v1End, "DELIVERED", 0, "{}", POLICY_ID)
        approveVersion.approve(v1.id, APPROVER)

        // v2 starts exactly where v1 ends — no overlap
        val v2 = createVersion.create(contract.id, "ARS", v1End, null, "DELIVERED", 7, "{}", POLICY_ID)
        val approved2 = approveVersion.approve(v2.id, APPROVER)
        assertThat(approved2.status).isEqualTo(ContractVersionStatus.APPROVED)
    }

    @Test
    fun `getEffective returns correct version for timestamp`() {
        val contract = createContract.create("Effective Test", PUBLISHER_ORG, COMMERCE_ORG, listOf("AR"), "UTC", 1)
        val boundary = now.plus(30, ChronoUnit.DAYS)

        val v1 = createVersion.create(contract.id, "ARS", now, boundary, "DELIVERED", 0, """{"fee_pct":10}""", POLICY_ID)
        approveVersion.approve(v1.id, APPROVER)

        val v2 = createVersion.create(contract.id, "ARS", boundary, null, "DELIVERED", 7, """{"fee_pct":12}""", POLICY_ID)
        approveVersion.approve(v2.id, APPROVER)

        // Query in v1 period
        val effective1 = getEffective.getEffective(contract.id, now.plus(10, ChronoUnit.DAYS))
        assertThat(effective1!!.version).isEqualTo(v1.version)
        assertThat(effective1.rules).contains("10")

        // Query in v2 period
        val effective2 = getEffective.getEffective(contract.id, boundary.plus(1, ChronoUnit.DAYS))
        assertThat(effective2!!.version).isEqualTo(v2.version)
        assertThat(effective2.rules).contains("12")
    }

    @Test
    fun `contract cannot be activated without approved version`() {
        val contract = createContract.create("No Version", PUBLISHER_ORG, COMMERCE_ORG, listOf("AR"), "UTC", 1)

        assertThatThrownBy { activateContract.activate(contract.id) }
            .hasMessageContaining("no approved versions")
    }

    @Test
    fun `contract activated after version approval`() {
        val contract = createContract.create("Activate Test", PUBLISHER_ORG, COMMERCE_ORG, listOf("AR"), "UTC", 1)
        val v1 = createVersion.create(contract.id, "ARS", now, null, "DELIVERED", 0, "{}", POLICY_ID)
        approveVersion.approve(v1.id, APPROVER)

        val activated = activateContract.activate(contract.id)
        assertThat(activated.status).isEqualTo(ContractStatus.ACTIVE)
        assertThat(activated.activatedAt).isNotNull()
    }

    @Test
    fun `already-active contract cannot be re-activated`() {
        val contract = createContract.create("Re-activate", PUBLISHER_ORG, COMMERCE_ORG, listOf("AR"), "UTC", 1)
        val v1 = createVersion.create(contract.id, "ARS", now, null, "DELIVERED", 0, "{}", POLICY_ID)
        approveVersion.approve(v1.id, APPROVER)
        activateContract.activate(contract.id)

        assertThatThrownBy { activateContract.activate(contract.id) }
            .hasMessageContaining("ACTIVE")
    }
}
