package dev.evolveos.migration;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record MigrationPlan(
        String skillName,
        int fromVersion,
        int toVersion,
        List<MigrationStep> steps,
        Set<String> addedOutputFields,
        Set<String> removedOutputFields,
        Set<String> addedPermissions) {

    public MigrationPlan {
        if (skillName == null || skillName.isBlank()) {
            throw new IllegalArgumentException("skillName must not be blank");
        }
        if (fromVersion < 1 || toVersion <= fromVersion) {
            throw new IllegalArgumentException("migration must move to a higher version");
        }
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        addedOutputFields = Set.copyOf(Objects.requireNonNull(addedOutputFields, "addedOutputFields"));
        removedOutputFields = Set.copyOf(Objects.requireNonNull(removedOutputFields, "removedOutputFields"));
        addedPermissions = Set.copyOf(Objects.requireNonNull(addedPermissions, "addedPermissions"));
    }

    public boolean requiresVerification() {
        return !addedOutputFields.isEmpty()
                || !removedOutputFields.isEmpty()
                || !addedPermissions.isEmpty();
    }
}
