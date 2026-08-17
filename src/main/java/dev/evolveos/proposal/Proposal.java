package dev.evolveos.proposal;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class Proposal {
    private final String id;
    private final String skillName;
    private final int skillVersion;
    private final String summary;
    private final double confidence;
    private final List<String> evidence;
    private final String proposedAction;
    private final Set<String> requiredPermissions;
    private final ProposalStatus status;

    private Proposal(
            String id,
            String skillName,
            int skillVersion,
            String summary,
            double confidence,
            List<String> evidence,
            String proposedAction,
            Set<String> requiredPermissions,
            ProposalStatus status) {
        this.id = requireText(id, "id");
        this.skillName = requireText(skillName, "skillName");
        if (skillVersion < 1) {
            throw new IllegalArgumentException("skillVersion must be at least 1");
        }
        this.skillVersion = skillVersion;
        this.summary = requireText(summary, "summary");
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be finite and between 0 and 1");
        }
        this.confidence = confidence;
        this.evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        if (this.evidence.isEmpty()) {
            throw new IllegalArgumentException("evidence must not be empty");
        }
        this.proposedAction = requireText(proposedAction, "proposedAction");
        this.requiredPermissions = Set.copyOf(
                Objects.requireNonNull(requiredPermissions, "requiredPermissions"));
        this.status = Objects.requireNonNull(status, "status");
    }

    public static Proposal draft(
            String id,
            String skillName,
            int skillVersion,
            String summary,
            double confidence,
            List<String> evidence,
            String proposedAction,
            Set<String> requiredPermissions) {
        return new Proposal(
                id, skillName, skillVersion, summary, confidence, evidence,
                proposedAction, requiredPermissions, ProposalStatus.DRAFT);
    }

    public String id() {
        return id;
    }

    public String skillName() {
        return skillName;
    }

    public int skillVersion() {
        return skillVersion;
    }

    public String summary() {
        return summary;
    }

    public double confidence() {
        return confidence;
    }

    public List<String> evidence() {
        return evidence;
    }

    public String proposedAction() {
        return proposedAction;
    }

    public Set<String> requiredPermissions() {
        return requiredPermissions;
    }

    public ProposalStatus status() {
        return status;
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
