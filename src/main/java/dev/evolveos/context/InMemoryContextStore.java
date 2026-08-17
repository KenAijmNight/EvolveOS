package dev.evolveos.context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class InMemoryContextStore implements ContextStore {
    private final Map<String, ContextItem> items = new LinkedHashMap<>();

    @Override
    public void add(ContextItem item) {
        Objects.requireNonNull(item, "item");
        if (items.putIfAbsent(item.id(), item) != null) {
            throw new IllegalArgumentException("context id already exists: " + item.id());
        }
    }

    @Override
    public List<ContextItem> all() {
        return List.copyOf(items.values());
    }

    @Override
    public List<ContextItem> search(String query) {
        var matches = new ArrayList<ContextItem>();
        for (var item : items.values()) {
            if (item.matches(query)) {
                matches.add(item);
            }
        }
        return List.copyOf(matches);
    }
}
