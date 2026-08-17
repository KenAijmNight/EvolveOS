package dev.evolveos;

import dev.evolveos.context.ContextItem;
import dev.evolveos.context.ContextStore;
import dev.evolveos.context.InMemoryContextStore;
import dev.evolveos.event.ContextAdded;
import dev.evolveos.event.EventEnvelope;
import dev.evolveos.event.EventLog;
import dev.evolveos.event.MigrationPlanned;
import dev.evolveos.event.ProposalStatusChanged;
import dev.evolveos.event.ProposalSubmitted;
import dev.evolveos.migration.ContractMigrationPlanner;
import dev.evolveos.migration.MigrationPlan;
import dev.evolveos.proposal.Proposal;
import dev.evolveos.proposal.ProposalInbox;
import dev.evolveos.skill.DeterministicMorningReviewSkill;
import java.util.List;
import java.util.Objects;

public final class EvolveEngine {
    private final ContextStore contextStore;
    private final ProposalInbox proposalInbox;
    private final EventLog eventLog;
    private final DeterministicMorningReviewSkill morningReview;
    private final ContractMigrationPlanner migrationPlanner;

    public EvolveEngine() {
        this(
                new InMemoryContextStore(),
                new ProposalInbox(),
                new EventLog(),
                new DeterministicMorningReviewSkill(),
                new ContractMigrationPlanner());
    }

    EvolveEngine(
            ContextStore contextStore,
            ProposalInbox proposalInbox,
            EventLog eventLog,
            DeterministicMorningReviewSkill morningReview,
            ContractMigrationPlanner migrationPlanner) {
        this.contextStore = Objects.requireNonNull(contextStore, "contextStore");
        this.proposalInbox = Objects.requireNonNull(proposalInbox, "proposalInbox");
        this.eventLog = Objects.requireNonNull(eventLog, "eventLog");
        this.morningReview = Objects.requireNonNull(morningReview, "morningReview");
        this.migrationPlanner = Objects.requireNonNull(migrationPlanner, "migrationPlanner");
    }

    public void addContext(ContextItem item) {
        contextStore.add(item);
        eventLog.append(new ContextAdded(item.id()));
    }

    public List<ContextItem> searchContext(String query) {
        return contextStore.search(query);
    }

    public Proposal runMorningReview() {
        var proposal = morningReview.run(contextStore.all());
        proposalInbox.submit(proposal);
        eventLog.append(new ProposalSubmitted(proposal.id()));
        return proposal;
    }

    public Proposal approve(String proposalId) {
        var before = proposalInbox.get(proposalId);
        var after = proposalInbox.approve(proposalId);
        eventLog.append(new ProposalStatusChanged(proposalId, before.status(), after.status()));
        return after;
    }

    public Proposal reject(String proposalId) {
        var before = proposalInbox.get(proposalId);
        var after = proposalInbox.reject(proposalId);
        eventLog.append(new ProposalStatusChanged(proposalId, before.status(), after.status()));
        return after;
    }

    public Proposal execute(String proposalId) {
        var before = proposalInbox.get(proposalId);
        var after = proposalInbox.execute(proposalId);
        eventLog.append(new ProposalStatusChanged(proposalId, before.status(), after.status()));
        return after;
    }

    public MigrationPlan planMorningReviewV2() {
        var plan = migrationPlanner.plan(morningReview.contractV1(), morningReview.contractV2());
        eventLog.append(new MigrationPlanned(
                plan.skillName(), plan.fromVersion(), plan.toVersion()));
        return plan;
    }

    public List<EventEnvelope> events() {
        return eventLog.all();
    }
}
