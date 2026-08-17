package dev.evolveos.event;

import dev.evolveos.proposal.ProposalStatus;
import java.util.Objects;

public record ProposalStatusChanged(
        String proposalId, ProposalStatus from, ProposalStatus to) implements DomainEvent {
    public ProposalStatusChanged {
        if (proposalId == null || proposalId.isBlank()) {
            throw new IllegalArgumentException("proposalId must not be blank");
        }
        from = Objects.requireNonNull(from, "from");
        to = Objects.requireNonNull(to, "to");
        if (from == to) {
            throw new IllegalArgumentException("status transition must change status");
        }
    }
}
