package dev.evolveos.event;

import java.util.Objects;

public record EventEnvelope(long sequence, DomainEvent event) {
    public EventEnvelope {
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        event = Objects.requireNonNull(event, "event");
    }
}
