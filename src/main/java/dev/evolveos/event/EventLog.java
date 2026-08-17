package dev.evolveos.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class EventLog {
    private final List<EventEnvelope> events = new ArrayList<>();

    public EventEnvelope append(DomainEvent event) {
        Objects.requireNonNull(event, "event");
        var envelope = new EventEnvelope(events.size() + 1L, event);
        events.add(envelope);
        return envelope;
    }

    public List<EventEnvelope> all() {
        return List.copyOf(events);
    }
}
