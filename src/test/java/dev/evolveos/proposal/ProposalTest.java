package dev.evolveos.proposal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProposalTest {

    @Test
    void aProposalCannotExecuteBeforeApproval() {
        assertThrows(IllegalStateException.class, () -> draft().execute());
    }

    @Test
    void anApprovedProposalCanExecuteExactlyOnce() {
        var approved = draft().approve();
        var executed = approved.execute();

        assertEquals(ProposalStatus.APPROVED, approved.status());
        assertEquals(ProposalStatus.EXECUTED, executed.status());
        assertThrows(IllegalStateException.class, executed::execute);
    }

    @Test
    void aRejectedProposalCannotBeApprovedOrExecuted() {
        var rejected = draft().reject();

        assertEquals(ProposalStatus.REJECTED, rejected.status());
        assertThrows(IllegalStateException.class, rejected::approve);
        assertThrows(IllegalStateException.class, rejected::execute);
    }

    private Proposal draft() {
        return new Proposal(
                "proposal-task-1",
                "morning-review",
                1,
                "Prioritize task 1",
                0.91,
                List.of("context:task-1", "tag:urgent"),
                "Mark task-1 as high priority",
                Set.of("tasks:write"),
                ProposalStatus.DRAFT);
    }
}
