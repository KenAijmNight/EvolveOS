package dev.evolveos.migration;

import java.util.Objects;

public record MigrationStep(MigrationStage stage, String description, boolean blocking) {
    public MigrationStep {
        stage = Objects.requireNonNull(stage, "stage");
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
    }
}
