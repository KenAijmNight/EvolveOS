package dev.evolveos.context;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record ContextItem(String id, String title, String body, Set<String> tags) {

    public ContextItem {
        id = requireText(id, "id");
        title = requireText(title, "title");
        body = requireText(body, "body");
        tags = Set.copyOf(Objects.requireNonNull(tags, "tags"));
    }

    boolean matches(String query) {
        var normalized = requireText(query, "query").toLowerCase(Locale.ROOT);
        return title.toLowerCase(Locale.ROOT).contains(normalized)
                || body.toLowerCase(Locale.ROOT).contains(normalized)
                || tags.stream().anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(normalized));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
