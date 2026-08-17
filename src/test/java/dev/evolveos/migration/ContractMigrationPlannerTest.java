package dev.evolveos.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evolveos.contract.SkillContract;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContractMigrationPlannerTest {

    @Test
    void plansEveryExpandContractStageAndReportsContractChanges() {
        var v1 = new SkillContract(
                "morning-review", 1, Set.of("context"),
                Set.of("priority", "evidence"), Set.of("tasks:write"), true);
        var v2 = new SkillContract(
                "morning-review", 2, Set.of("context"),
                Set.of("priority", "evidence", "confidence"),
                Set.of("tasks:write", "audit:write"), true);

        var plan = new ContractMigrationPlanner().plan(v1, v2);

        assertEquals(List.of(MigrationStage.values()),
                plan.steps().stream().map(MigrationStep::stage).toList());
        assertEquals(Set.of("confidence"), plan.addedOutputFields());
        assertEquals(Set.of(), plan.removedOutputFields());
        assertEquals(Set.of("audit:write"), plan.addedPermissions());
        assertTrue(plan.requiresVerification());
    }

    @Test
    void reportsNewRequiredInputsAndAWeakenedApprovalBoundary() {
        var v1 = new SkillContract(
                "morning-review", 1, Set.of("context"),
                Set.of("priority"), Set.of("tasks:write"), true);
        var v2 = new SkillContract(
                "morning-review", 2, Set.of("context", "calendar"),
                Set.of("priority"), Set.of("tasks:write"), false);

        var plan = new ContractMigrationPlanner().plan(v1, v2);

        assertEquals(Set.of("calendar"), plan.addedRequiredInputs());
        assertTrue(plan.approvalRequirementRemoved());
        assertTrue(plan.requiresVerification());
    }

    @Test
    void rejectsCrossSkillAndNonForwardMigrations() {
        var v1 = contract("morning-review", 1);

        assertThrows(IllegalArgumentException.class,
                () -> new ContractMigrationPlanner().plan(v1, contract("other", 2)));
        assertThrows(IllegalArgumentException.class,
                () -> new ContractMigrationPlanner().plan(v1, contract("morning-review", 1)));
    }

    private SkillContract contract(String name, int version) {
        return new SkillContract(
                name, version, Set.of("context"), Set.of("priority"),
                Set.of("tasks:write"), true);
    }
}
