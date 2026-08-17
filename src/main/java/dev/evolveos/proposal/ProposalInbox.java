package dev.evolveos.proposal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

public final class ProposalInbox {
    private final Map<String, Proposal> proposals = new LinkedHashMap<>();

    public Proposal submit(Proposal proposal) {
        Objects.requireNonNull(proposal, "proposal");
        if (proposal.status() != ProposalStatus.DRAFT) {
            throw new IllegalArgumentException("only draft proposals can be submitted");
        }
        if (proposals.putIfAbsent(proposal.id(), proposal) != null) {
            throw new IllegalArgumentException("proposal id already exists: " + proposal.id());
        }
        return proposal;
    }

    public Proposal get(String id) {
        var proposal = proposals.get(id);
        if (proposal == null) {
            throw new NoSuchElementException("proposal not found: " + id);
        }
        return proposal;
    }

    public List<Proposal> all() {
        return List.copyOf(proposals.values());
    }

    public Proposal approve(String id) {
        return replace(get(id).approve());
    }

    public Proposal reject(String id) {
        return replace(get(id).reject());
    }

    public Proposal execute(String id) {
        return replace(get(id).execute());
    }

    private Proposal replace(Proposal proposal) {
        proposals.put(proposal.id(), proposal);
        return proposal;
    }
}
