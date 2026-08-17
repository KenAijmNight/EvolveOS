package dev.evolveos.contract;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SkillContractTest {

    @Test
    void addingOutputFieldsKeepsAContractBackwardCompatible() {
        var v1 = contract(1, Set.of("priority", "evidence"));
        var v2 = contract(2, Set.of("priority", "evidence", "confidence"));

        assertTrue(v2.isBackwardCompatibleWith(v1));
    }

    @Test
    void removingAnOutputFieldBreaksCompatibility() {
        var v1 = contract(1, Set.of("priority", "evidence"));
        var v2 = contract(2, Set.of("priority"));

        assertFalse(v2.isBackwardCompatibleWith(v1));
    }

    @Test
    void addingARequiredInputOrPermissionBreaksCompatibility() {
        var v1 = contract(1, Set.of("priority"));
        var newInput = new SkillContract(
                "morning-review", 2, Set.of("context", "calendar"),
                Set.of("priority"), Set.of("tasks:write"), true);
        var newPermission = new SkillContract(
                "morning-review", 2, Set.of("context"),
                Set.of("priority"), Set.of("tasks:write", "email:send"), true);

        assertFalse(newInput.isBackwardCompatibleWith(v1));
        assertFalse(newPermission.isBackwardCompatibleWith(v1));
    }

    @Test
    void rejectsInvalidIdentityAndVersion() {
        assertThrows(IllegalArgumentException.class, () ->
                new SkillContract(" ", 1, Set.of(), Set.of("priority"), Set.of(), true));
        assertThrows(IllegalArgumentException.class, () ->
                new SkillContract("morning-review", 0, Set.of(), Set.of("priority"), Set.of(), true));
    }

    private SkillContract contract(int version, Set<String> outputs) {
        return new SkillContract(
                "morning-review", version, Set.of("context"), outputs,
                Set.of("tasks:write"), true);
    }
}
