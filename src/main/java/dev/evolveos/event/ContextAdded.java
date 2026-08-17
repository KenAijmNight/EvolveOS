package dev.evolveos.event;

public record ContextAdded(String contextId) implements DomainEvent {
    public ContextAdded {
        if (contextId == null || contextId.isBlank()) {
            throw new IllegalArgumentException("contextId must not be blank");
        }
    }
}
