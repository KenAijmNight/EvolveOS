package dev.evolveos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evolveos.context.ContextItem;
import dev.evolveos.event.ContextAdded;
import dev.evolveos.event.MigrationPlanned;
import dev.evolveos.event.ProposalStatusChanged;
import dev.evolveos.event.ProposalSubmitted;
import dev.evolveos.migration.MigrationStage;
import dev.evolveos.proposal.ProposalStatus;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EvolveEngineIntegrationTest {

    @Test
    void runsTheDeterministicMorningReviewApprovalAndMigrationFlow() {
        var engine = new EvolveEngine();
        engine.addContext(new ContextItem(
                "task-2", "Write documentation", "Describe the quickstart", Set.of("docs")));
        engine.addContext(new ContextItem(
                "task-1", "Ship v0.1", "Blocked until the PR is green", Set.of("urgent", "release")));

        var draft = engine.runMorningReview();

        assertEquals("proposal-task-1", draft.id());
        assertEquals(ProposalStatus.DRAFT, draft.status());
        assertEquals(0.91, draft.confidence());
        assertEquals(List.of("context:task-1", "tag:urgent"), draft.evidence());
        assertEquals(Set.of("tasks:write"), draft.requiredPermissions());

        var approved = engine.approve(draft.id());
        var executed = engine.execute(draft.id());
        var migration = engine.planMorningReviewV2();

        assertEquals(ProposalStatus.APPROVED, approved.status());
        assertEquals(ProposalStatus.EXECUTED, executed.status());
        assertEquals(MigrationStage.RETIRED,
                migration.steps().get(migration.steps().size() - 1).stage());
        assertTrue(migration.addedOutputFields().contains("confidence"));

        assertEquals(List.of(
                        ContextAdded.class,
                        ContextAdded.class,
                        ProposalSubmitted.class,
                        ProposalStatusChanged.class,
                        ProposalStatusChanged.class,
                        MigrationPlanned.class),
                engine.events().stream().map(envelope -> envelope.event().getClass()).toList());
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L),
                engine.events().stream().map(envelope -> envelope.sequence()).toList());
    }
}
