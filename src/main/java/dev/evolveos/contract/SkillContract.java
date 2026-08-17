package dev.evolveos.contract;

import java.util.Objects;
import java.util.Set;

public record SkillContract(
        String name,
        int version,
        Set<String> requiredInputs,
        Set<String> outputFields,
        Set<String> permissions,
        boolean approvalRequired) {

    public SkillContract {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be at least 1");
        }
        requiredInputs = Set.copyOf(Objects.requireNonNull(requiredInputs, "requiredInputs"));
        outputFields = Set.copyOf(Objects.requireNonNull(outputFields, "outputFields"));
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions"));
        if (outputFields.isEmpty()) {
            throw new IllegalArgumentException("outputFields must not be empty");
        }
    }

    public boolean isBackwardCompatibleWith(SkillContract previous) {
        Objects.requireNonNull(previous, "previous");
        return name.equals(previous.name)
                && version > previous.version
                && outputFields.containsAll(previous.outputFields)
                && previous.requiredInputs.containsAll(requiredInputs)
                && previous.permissions.containsAll(permissions)
                && (!previous.approvalRequired || approvalRequired);
    }
}
