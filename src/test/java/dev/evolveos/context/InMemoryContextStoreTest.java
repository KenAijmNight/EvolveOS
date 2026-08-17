package dev.evolveos.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;

class InMemoryContextStoreTest {

    @Test
    void searchesTitlesBodiesAndTagsCaseInsensitivelyInInsertionOrder() {
        var store = new InMemoryContextStore();
        var first = new ContextItem("task-1", "Ship release", "Blocked by CI", Set.of("urgent"));
        var second = new ContextItem("task-2", "Write docs", "Document the release demo", Set.of("docs"));
        store.add(first);
        store.add(second);

        assertEquals(java.util.List.of(first, second), store.search("RELEASE"));
        assertEquals(java.util.List.of(first), store.search("URGENT"));
        assertEquals(java.util.List.of(first), store.search("blocked"));
        assertEquals(java.util.List.of(second), store.search("write"));
    }

    @Test
    void rejectsDuplicateContextIds() {
        var store = new InMemoryContextStore();
        store.add(new ContextItem("task-1", "First", "Body", Set.of()));

        assertThrows(IllegalArgumentException.class, () ->
                store.add(new ContextItem("task-1", "Second", "Body", Set.of())));
    }
}
