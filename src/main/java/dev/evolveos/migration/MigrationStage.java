package dev.evolveos.migration;

public enum MigrationStage {
    DRAFT,
    EXPAND,
    DUAL_RUN,
    BACKFILL,
    VERIFY,
    CANARY,
    CONTRACT,
    RETIRED
}
