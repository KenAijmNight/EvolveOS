package dev.evolveos.skill;

import dev.evolveos.context.ContextItem;
import dev.evolveos.contract.SkillContract;
import dev.evolveos.proposal.Proposal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DeterministicMorningReviewSkill {

    public SkillContract contractV1() {
        return new SkillContract(
                "morning-review",
                1,
                Set.of("context"),
                Set.of("priority", "evidence", "proposedAction"),
                Set.of("tasks:write"),
                true);
    }

    public SkillContract contractV2() {
        return new SkillContract(
                "morning-review",
                2,
                Set.of("context"),
                Set.of("priority", "evidence", "proposedAction", "confidence"),
                Set.of("tasks:write", "audit:write"),
                true);
    }

    public Proposal run(List<ContextItem> context) {
        if (context == null || context.isEmpty()) {
            throw new IllegalStateException("morning-review requires at least one context item");
        }

        var selected = context.stream()
                .sorted(Comparator
                        .comparingInt(this::score)
                        .reversed()
                        .thenComparing(ContextItem::id))
                .findFirst()
                .orElseThrow();

        var evidence = new ArrayList<String>();
        evidence.add("context:" + selected.id());
        if (selected.tags().contains("urgent")) {
            evidence.add("tag:urgent");
        } else if (selected.body().toLowerCase(Locale.ROOT).contains("blocked")) {
            evidence.add("signal:blocked");
        } else {
            evidence.add("title:" + selected.title());
        }

        return Proposal.draft(
                "proposal-" + selected.id(),
                contractV1().name(),
                contractV1().version(),
                "Prioritize: " + selected.title(),
                confidence(selected),
                evidence,
                "Mark " + selected.id() + " as high priority",
                contractV1().permissions());
    }

    private int score(ContextItem item) {
        var score = 0;
        if (item.tags().contains("urgent")) {
            score += 100;
        }
        if (item.body().toLowerCase(Locale.ROOT).contains("blocked")) {
            score += 50;
        }
        if (item.tags().contains("release")) {
            score += 20;
        }
        return score;
    }

    private double confidence(ContextItem item) {
        if (item.tags().contains("urgent")) {
            return 0.91;
        }
        if (item.body().toLowerCase(Locale.ROOT).contains("blocked")) {
            return 0.82;
        }
        return 0.65;
    }
}
