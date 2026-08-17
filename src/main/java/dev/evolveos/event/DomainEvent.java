package dev.evolveos.event;

public sealed interface DomainEvent
        permits ContextAdded, ProposalSubmitted, ProposalStatusChanged, MigrationPlanned {
}
