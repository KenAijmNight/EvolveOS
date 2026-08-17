package dev.evolveos.context;

import java.util.List;

public interface ContextStore {
    void add(ContextItem item);

    List<ContextItem> all();

    List<ContextItem> search(String query);
}
