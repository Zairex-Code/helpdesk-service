package org.softtech.infrastructure.seed;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.softtech.domain.model.ErpModule;
import org.softtech.domain.model.Feedback;
import org.softtech.domain.model.Priority;
import org.softtech.domain.model.Ticket;
import org.softtech.domain.port.out.TicketPersistencePort;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Development-only database seeder that populates MongoDB with sample support tickets
 * covering all lifecycle states, priorities, ERP modules, and customer tiers.
 * <p>
 * Only executes when the {@code dev} build profile is active (i.e., {@code ./mvnw quarkus:dev})
 * and the tickets collection is empty — it is never triggered in production or test builds.
 * If MongoDB is unreachable, the seeder logs a warning and skips gracefully.
 * </p>
 */
@Slf4j
@ApplicationScoped
@IfBuildProfile("dev")
@RequiredArgsConstructor
public class DevDataSeeder {

    private static final String AGENT_TI_5042 = "AGT-TI-5042";
    private static final String AGENT_TI_3320 = "AGT-TI-3320";

    private final TicketPersistencePort ticketPersistencePort;

    void onStart(@Observes StartupEvent event) {
        seedIfEmpty()
                .onFailure().invoke(t -> log.warn("Development seed skipped: {}", t.getMessage()))
                .subscribe().with(v -> {});
    }

    private Uni<Void> seedIfEmpty() {
        return ticketPersistencePort.existsByTicketNumber("TICK-2026-0001")
                .onFailure().recoverWithItem(() -> {
                    log.warn("Cannot check existing data, attempting seed anyway");
                    return false;
                })
                .flatMap(exists -> exists
                        ? Uni.createFrom().voidItem()
                                .invoke(() -> log.info("Development seed skipped: collection already has data"))
                        : seedData());
    }

    private Uni<Void> seedData() {
        List<Ticket> tickets = buildSeedTickets();
        return Multi.createFrom().iterable(tickets)
                .onItem().transformToUniAndConcatenate(ticketPersistencePort::save)
                .collect().asList()
                .replaceWithVoid()
                .invoke(() -> log.info("Seeded {} development tickets across all lifecycle states", tickets.size()));
    }

    private List<Ticket> buildSeedTickets() {
        Instant base = Instant.now();

        Ticket openPayroll = ticket(
                "seed-1", "TICK-2026-0001",
                "Database timeout in payroll batch",
                "PostgreSQL deadlock detected when processing 5 000+ employee payroll records concurrently in ERP-RRHH.",
                Priority.HIGH, ErpModule.HUMAN_RESOURCES, "cliente@softtech.com", true,
                base.minus(Duration.ofHours(1)));

        Ticket openFinance = ticket(
                "seed-2", "TICK-2026-0002",
                "General ledger balance mismatch after multi-currency consolidation",
                "Discrepancy of 14 250 EUR detected in the EUR/USD consolidation batch for Q3 close.",
                Priority.CRITICAL, ErpModule.FINANCIAL, "USR-CORP-22100", false,
                base.minus(Duration.ofMinutes(30)));

        Ticket assignedCrm = ticket(
                "seed-3", "TICK-2026-0003",
                "CRM contact sync delayed with external marketing platform",
                "Contacts created in the last 48 hours are not appearing in the MailChimp integration.",
                Priority.MEDIUM, ErpModule.CRM, "USR-CORP-45010", false,
                base.minus(Duration.ofHours(3)))
                .assignToAgent(AGENT_TI_5042, base.minus(Duration.ofHours(2)).plus(Duration.ofMinutes(30)));

        Ticket inProgressBilling = ticket(
                "seed-4", "TICK-2026-0004",
                "Electronic invoice PDF generation fails for Chilean DTE format",
                "NullPointerException in DTE XSLT transformer when processing invoices with discount percentages over 50%.",
                Priority.HIGH, ErpModule.BILLING, "USR-CORP-11890", false,
                base.minus(Duration.ofHours(5)))
                .assignToAgent(AGENT_TI_3320, base.minus(Duration.ofHours(4)).plus(Duration.ofMinutes(30)))
                .startInvestigation(base.minus(Duration.ofHours(3)).plus(Duration.ofMinutes(45)));

        Ticket resolvedInventory = ticket(
                "seed-5", "TICK-2026-0005",
                "Stock discrepancy in warehouse WH-03 after physical count",
                "Physical count reports 340 units of SKU-8842, but system shows 315 units. Audit trail requested.",
                Priority.MEDIUM, ErpModule.INVENTORY, "USR-CORP-77210", false,
                base.minus(Duration.ofHours(10)))
                .assignToAgent(AGENT_TI_5042, base.minus(Duration.ofHours(9)).plus(Duration.ofMinutes(30)))
                .startInvestigation(base.minus(Duration.ofHours(8)).plus(Duration.ofMinutes(45)))
                .resolve("Corrected inventory count in WH-03 after reconciling transfer order TO-4451 that was not applied.",
                        base.minus(Duration.ofHours(7)).plus(Duration.ofMinutes(15)));

        Ticket closedCrm = ticket(
                "seed-6", "TICK-2026-0006",
                "Email template editor not saving custom CSS",
                "The rich text editor discards custom CSS styles when switching between HTML and visual mode.",
                Priority.LOW, ErpModule.CRM, "USR-CORP-55120", false,
                base.minus(Duration.ofDays(1)))
                .assignToAgent(AGENT_TI_3320, base.minus(Duration.ofHours(23)).plus(Duration.ofMinutes(30)))
                .startInvestigation(base.minus(Duration.ofHours(22)).plus(Duration.ofMinutes(45)))
                .resolve("Patched CKEditor 5 configuration to preserve style tags via allowedContent rules.",
                        base.minus(Duration.ofHours(21)).plus(Duration.ofMinutes(15)))
                .closeWithFeedback(
                        Feedback.of(5, "The editor finally works as expected. Great support!", base.minus(Duration.ofHours(20))),
                        base.minus(Duration.ofHours(20)));

        Ticket closedCoreVip = ticket(
                "seed-7", "TICK-2026-0007",
                "RBAC role assignment fails after LDAP sync for corporate users",
                "Users authenticated via LDAP cannot inherit the mapped ERP roles after the nightly sync cron job.",
                Priority.HIGH, ErpModule.CORE_SYSTEM, "cliente@softtech.com", true,
                base.minus(Duration.ofDays(2)))
                .assignToAgent(AGENT_TI_5042, base.minus(Duration.ofHours(47)).plus(Duration.ofMinutes(30)))
                .startInvestigation(base.minus(Duration.ofHours(46)).plus(Duration.ofMinutes(45)))
                .resolve("Fixed role mapping cache invalidation after LDAP sync; added a forced refresh event.",
                        base.minus(Duration.ofHours(44)).plus(Duration.ofMinutes(15)))
                .closeWithFeedback(
                        Feedback.of(4, "Resolved quickly before the morning batch. Thank you.",
                                base.minus(Duration.ofHours(43))),
                        base.minus(Duration.ofHours(43)));

        Ticket cancelledSupply = ticket(
                "seed-8", "TICK-2026-0008",
                "Vendor evaluation workflow stuck in pending approval",
                "Purchase order PO-8821 requires vendor scorecard approval but the workflow engine is not progressing.",
                Priority.LOW, ErpModule.SUPPLY_CHAIN, "USR-CORP-30240", false,
                base.minus(Duration.ofHours(2)))
                .cancel("Duplicate ticket already tracked under TICK-2026-0041", base.minus(Duration.ofHours(1)));

        Ticket openSales = ticket(
                "seed-9", "TICK-2026-0009",
                "POS terminal freezes during peak-hour checkout",
                "Store ST-12 reports POS terminal #3 freezing intermittently during the 12:00-14:00 peak window.",
                Priority.MEDIUM, ErpModule.SALES, "USR-CORP-66510", false,
                base.minus(Duration.ofMinutes(15)));

        Ticket inProgressFinance = ticket(
                "seed-10", "TICK-2026-0010",
                "Automated tax compliance report failed for AR",
                "Argentina AFIP electronic invoice gateway returning HTTP 503 during the last 3 reporting windows.",
                Priority.CRITICAL, ErpModule.FINANCIAL, "USR-CORP-22100", true,
                base.minus(Duration.ofHours(8)))
                .assignToAgent(AGENT_TI_3320, base.minus(Duration.ofHours(7)).plus(Duration.ofMinutes(30)))
                .startInvestigation(base.minus(Duration.ofHours(6)).plus(Duration.ofMinutes(45)));

        return List.of(
                openPayroll, openFinance, assignedCrm, inProgressBilling, resolvedInventory,
                closedCrm, closedCoreVip, cancelledSupply, openSales, inProgressFinance
        );
    }

    private Ticket ticket(String id, String number, String title, String description,
                          Priority priority, ErpModule module, String requester, boolean vip,
                          Instant createdAt) {
        return Ticket.created(id, number, title, description, priority, module, requester, vip, createdAt);
    }
}