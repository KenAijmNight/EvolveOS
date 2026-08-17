package dev.evolveos.proposal;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record Proposal(
        String id,
        String skillName,
        int skillVersion,
        String summary,
        double confidence,
        List<String> evidence,
        String proposedAction,
        Set<String> requiredPermissions,
        ProposalStatus status) {

    public Proposal {
        id = requireText(id, "id");
        skillName = requireText(skillName, "skillName");
        if (skillVersion < 1) {
            throw new IllegalArgumentException("skillVersion must be at least 1");
        }
        summary = requireText(summary, "summary");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        if (evidence.isEmpty()) {
            throw new IllegalArgumentException("evidence must not be empty");
        }
        proposedAction = requireText(proposedAction, "proposedAction");
        requiredPermissions = Set.copyOf(Objects.requireNonNull(requiredPermissions, "requiredPermissions"));
        status = Objects.requireNonNull(status, "status");
    }

    public Proposal approve() {
        requireStatus(ProposalStatus.DRAFT, "approve");
        return withStatus(ProposalStatus.APPROVED);
    }

    public Proposal reject() {
        requireStatus(ProposalStatus.DRAFT, "reject");
        return withStatus(ProposalStatus.REJECTED);
    }

    public Proposal execute() {
        requireStatus(ProposalStatus.APPROVED, "execute");
        return withStatus(ProposalStatus.EXECUTED);
    }

    private Proposal withStatus(ProposalStatus newStatus) {
        return new Proposal(
                id, skillName, skillVersion, summary, confidence, evidence,
                proposedAction, requiredPermissions, newStatus);
    }

    private void requireStatus(ProposalStatus expected, String transition) {
        if (status != expected) {
            throw new IllegalStateException(
                    "cannot " + transition + " proposal " + id + " from " + status);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
