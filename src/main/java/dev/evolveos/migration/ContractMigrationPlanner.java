package dev.evolveos.migration;

import dev.evolveos.contract.SkillContract;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class ContractMigrationPlanner {

    public MigrationPlan plan(SkillContract from, SkillContract to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (!from.name().equals(to.name())) {
            throw new IllegalArgumentException("contracts must belong to the same skill");
        }
        if (to.version() <= from.version()) {
            throw new IllegalArgumentException("target contract version must be higher");
        }

        var steps = Arrays.stream(MigrationStage.values())
                .map(stage -> new MigrationStep(stage, describe(stage, from, to), isBlocking(stage)))
                .toList();

        return new MigrationPlan(
                from.name(),
                from.version(),
                to.version(),
                steps,
                difference(to.outputFields(), from.outputFields()),
                difference(from.outputFields(), to.outputFields()),
                difference(to.permissions(), from.permissions()),
                difference(to.requiredInputs(), from.requiredInputs()),
                from.approvalRequired() && !to.approvalRequired());
    }

    private Set<String> difference(Set<String> left, Set<String> right) {
        var values = new TreeSet<>(left);
        values.removeAll(right);
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    private boolean isBlocking(MigrationStage stage) {
        return switch (stage) {
            case VERIFY, CONTRACT, RETIRED -> true;
            default -> false;
        };
    }

    private String describe(MigrationStage stage, SkillContract from, SkillContract to) {
        return switch (stage) {
            case DRAFT -> "Review contract v" + to.version() + " and its compatibility impact";
            case EXPAND -> "Publish v" + to.version() + " alongside v" + from.version();
            case DUAL_RUN -> "Run both contract versions and compare their outputs";
            case BACKFILL -> "Backfill historical outputs where the new fields are absent";
            case VERIFY -> "Verify readers, writers, evidence and permission changes";
            case CANARY -> "Route a bounded subset of runs to v" + to.version();
            case CONTRACT -> "Make v" + to.version() + " the default while retaining rollback";
            case RETIRED -> "Retire v" + from.version() + " only after verification succeeds";
        };
    }
}
