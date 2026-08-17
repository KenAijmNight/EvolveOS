package dev.evolveos.event;

public record MigrationPlanned(String skillName, int fromVersion, int toVersion)
        implements DomainEvent {
    public MigrationPlanned {
        if (skillName == null || skillName.isBlank()) {
            throw new IllegalArgumentException("skillName must not be blank");
        }
        if (fromVersion < 1 || toVersion <= fromVersion) {
            throw new IllegalArgumentException("migration versions are invalid");
        }
    }
}
