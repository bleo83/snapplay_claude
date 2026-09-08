package io.snapplay.settlement

import io.snapplay.common.ValidationException
import io.snapplay.ledger.application.port.input.RecordEarnUseCase
import io.snapplay.ledger.infrastructure.persistence.DemoLedgerRepository
import io.snapplay.settlement.application.port.input.ApproveStatementUseCase
import io.snapplay.settlement.application.port.input.GenerateStatementUseCase
import io.snapplay.settlement.application.port.input.GetStatementLinesUseCase
import io.snapplay.settlement.domain.StatementStatus
import io.snapplay.settlement.infrastructure.persistence.DemoStatementRepository
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

private val PARTY = UUID.randomUUID()
private val COUNTERPARTY = UUID.randomUUID()
private val CONTRACT_ID = UUID.randomUUID()
private val CONTRACT_VERSION_ID = UUID.randomUUID()
private val CALCULATOR = UUID.randomUUID()
private val APPROVER = UUID.randomUUID()
private val NOW = Instant.now()

@SpringBootTest
@ActiveProfiles("demo")
class StatementTest {
    @Autowired lateinit var generateStatement: GenerateStatementUseCase

    @Autowired lateinit var approveStatement: ApproveStatementUseCase

    @Autowired lateinit var getLines: GetStatementLinesUseCase

    @Autowired lateinit var recordEarn: RecordEarnUseCase

    @Autowired lateinit var demoStatementRepo: DemoStatementRepository

    @Autowired lateinit var demoLedgerRepo: DemoLedgerRepository

    @BeforeEach
    fun setUp() {
        demoStatementRepo.clear()
        demoLedgerRepo.clear()
    }

    private fun seedEarnEntry(amount: Long) {
        recordEarn.recordEarn(
            providerOrderId = UUID.randomUUID(),
            handoffSessionId = null,
            contractVersionId = CONTRACT_VERSION_ID,
            ruleKey = "platform_fee",
            causativeEvent = "ORDER_DELIVERED",
            partyId = PARTY,
            counterpartyId = COUNTERPARTY,
            baseAmountMinor = 10000,
            amountMinor = amount,
            currency = "ARS",
        )
    }

    @Test
    fun `statement total matches ledger entries`() {
        seedEarnEntry(1000)
        seedEarnEntry(2000)

        val statement =
            generateStatement.generate(
                PARTY,
                COUNTERPARTY,
                CONTRACT_ID,
                NOW.minus(1, ChronoUnit.HOURS),
                NOW.plus(1, ChronoUnit.HOURS),
                "ARS",
                CALCULATOR,
            )

        assertThat(statement.totalMinor).isEqualTo(3000)
        assertThat(statement.status).isEqualTo(StatementStatus.REVIEW)
        assertThat(statement.checksum).isNotNull()
    }

    @Test
    fun `statement has traceable lines`() {
        seedEarnEntry(1500)

        val statement =
            generateStatement.generate(
                PARTY,
                COUNTERPARTY,
                CONTRACT_ID,
                NOW.minus(1, ChronoUnit.HOURS),
                NOW.plus(1, ChronoUnit.HOURS),
                "ARS",
                CALCULATOR,
            )

        val lines = getLines.getLines(statement.id)
        assertThat(lines).isNotEmpty
        assertThat(lines.sumOf { it.totalMinor }).isEqualTo(statement.totalMinor)
    }

    @Test
    fun `four-eyes - approver must differ from calculator`() {
        seedEarnEntry(1000)

        val statement =
            generateStatement.generate(PARTY, COUNTERPARTY, CONTRACT_ID, NOW.minus(1, ChronoUnit.HOURS), NOW.plus(1, ChronoUnit.HOURS), "ARS", CALCULATOR)

        // Same person tries to approve → rejected
        assertThatThrownBy { approveStatement.approve(statement.id, CALCULATOR) }
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("four-eyes")
    }

    @Test
    fun `approve freezes statement`() {
        seedEarnEntry(1000)

        val statement =
            generateStatement.generate(PARTY, COUNTERPARTY, CONTRACT_ID, NOW.minus(1, ChronoUnit.HOURS), NOW.plus(1, ChronoUnit.HOURS), "ARS", CALCULATOR)

        val approved = approveStatement.approve(statement.id, APPROVER)

        assertThat(approved.status).isEqualTo(StatementStatus.APPROVED)
        assertThat(approved.approvedBy).isEqualTo(APPROVER)
        assertThat(approved.approvedAt).isNotNull()

        // Cannot re-approve
        assertThatThrownBy { approveStatement.approve(statement.id, APPROVER) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `empty period produces zero statement`() {
        val statement =
            generateStatement.generate(
                PARTY,
                COUNTERPARTY,
                CONTRACT_ID,
                NOW.minus(10, ChronoUnit.DAYS),
                NOW.minus(9, ChronoUnit.DAYS),
                "ARS",
                CALCULATOR,
            )

        assertThat(statement.totalMinor).isEqualTo(0)
    }
}
