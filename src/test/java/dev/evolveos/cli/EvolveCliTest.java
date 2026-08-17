package dev.evolveos.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class EvolveCliTest {

    @Test
    void demoPrintsACompleteDeterministicWorkflow() {
        var bytes = new ByteArrayOutputStream();
        var cli = new EvolveCli(new PrintStream(bytes, true, StandardCharsets.UTF_8));

        var exitCode = cli.run("demo");
        var output = bytes.toString(StandardCharsets.UTF_8);

        assertEquals(0, exitCode);
        assertTrue(output.contains("EvolveOS v0.1 demo"));
        assertTrue(output.contains("Proposal: proposal-task-1 [DRAFT]"));
        assertTrue(output.contains("Approved: APPROVED"));
        assertTrue(output.contains("Executed: EXECUTED"));
        assertTrue(output.contains("Migration: morning-review v1 -> v2"));
        assertTrue(output.contains("Stages: DRAFT -> EXPAND -> DUAL_RUN -> BACKFILL -> VERIFY -> CANARY -> CONTRACT -> RETIRED"));
        assertTrue(output.contains("Audit events: 6"));
    }
}
