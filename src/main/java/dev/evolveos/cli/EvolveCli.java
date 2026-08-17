package dev.evolveos.cli;

import dev.evolveos.EvolveEngine;
import dev.evolveos.context.ContextItem;
import java.io.PrintStream;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class EvolveCli {
    private final PrintStream out;

    public EvolveCli(PrintStream out) {
        this.out = Objects.requireNonNull(out, "out");
    }

    public static void main(String[] args) {
        var exitCode = new EvolveCli(System.out).run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public int run(String... args) {
        if (args.length != 1 || !"demo".equals(args[0])) {
            out.println("Usage: java -jar evolveos.jar demo");
            return 2;
        }

        var engine = new EvolveEngine();
        engine.addContext(new ContextItem(
                "task-2",
                "Write documentation",
                "Describe the quickstart",
                Set.of("docs")));
        engine.addContext(new ContextItem(
                "task-1",
                "Ship v0.1",
                "Blocked until the PR is green",
                Set.of("urgent", "release")));

        var draft = engine.runMorningReview();

        out.println("EvolveOS v0.1 demo");
        out.println("Proposal: " + draft.id() + " [" + draft.status() + "]");
        out.printf(Locale.ROOT, "Confidence: %.2f%n", draft.confidence());
        out.println("Evidence: " + draft.evidence());
        out.println("Permissions: " + draft.requiredPermissions());

        var approved = engine.approve(draft.id());
        var executed = engine.execute(draft.id());
        var migration = engine.planMorningReviewV2();

        out.println("Approved: " + approved.status());
        out.println("Executed: " + executed.status());
        out.println("Migration: " + migration.skillName()
                + " v" + migration.fromVersion()
                + " -> v" + migration.toVersion());
        out.println("Stages: " + migration.steps().stream()
                .map(step -> step.stage().name())
                .collect(Collectors.joining(" -> ")));
        out.println("Added outputs: " + migration.addedOutputFields());
        out.println("Audit events: " + engine.events().size());
        return 0;
    }
}
