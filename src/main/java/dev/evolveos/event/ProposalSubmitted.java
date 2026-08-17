package dev.evolveos.event;

public record ProposalSubmitted(String proposalId) implements DomainEvent {
    public ProposalSubmitted {
        if (proposalId == null || proposalId.isBlank()) {
            throw new IllegalArgumentException("proposalId must not be blank");
        }
    }
}
