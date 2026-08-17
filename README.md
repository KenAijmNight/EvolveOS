# EvolveOS

**Safely evolve AI workflow contracts.**

EvolveOS is an experimental Java control plane for AI workflows. The v0.1 reference implementation runs entirely locally and focuses on the part most agent frameworks leave until later: changing skills, permissions, outputs, and approval boundaries without silently breaking existing consumers.

> **Status:** v0.1 foundation. The current release is a deterministic reference implementation, not a production agent runtime.

## Why EvolveOS?

Agent demos are easy to build. Safely changing a running agent system is harder.

EvolveOS applies the database **expand-contract** pattern to AI workflow contracts:

```text
DRAFT → EXPAND → DUAL_RUN → BACKFILL → VERIFY → CANARY → CONTRACT → RETIRED
```

It also makes a safety rule explicit:

> A workflow produces a proposal before it produces a side effect.

## What v0.1 proves

The first vertical slice runs entirely locally and without API keys:

```text
context items
    ↓
deterministic morning-review skill
    ↓
proposal + confidence + evidence + permissions
    ↓
explicit approve/reject state transition
    ↓
execution state (no external side effect in v0.1)
    ↓
v1 → v2 expand-contract migration plan
    ↓
append-only audit events
```

Included:

- immutable Java records for context, skill contracts, proposals, migration plans, and events;
- a deterministic in-memory context store;
- a proposal inbox with guarded `DRAFT → APPROVED/REJECTED → EXECUTED` transitions;
- a complete eight-stage expand-contract migration planner;
- a sequenced append-only event log;
- a runnable CLI demo;
- 13 unit and integration tests;
- GitHub Actions CI on Java 21.

## Quickstart

Requirements:

- Java 21 or newer;
- no locally installed Maven is required—the repository contains Maven Wrapper 3.9.9.

```bash
git clone https://github.com/KenAijmNight/EvolveOS.git
cd EvolveOS
./mvnw verify
java -jar target/evolveos-0.1.0-SNAPSHOT.jar demo
```

Expected demo:

```text
EvolveOS v0.1 demo
Proposal: proposal-task-1 [DRAFT]
Confidence: 0.91
Evidence: [context:task-1, tag:urgent]
Permissions: [tasks:write]
Approved: APPROVED
Executed: EXECUTED
Migration: morning-review v1 -> v2
Stages: DRAFT -> EXPAND -> DUAL_RUN -> BACKFILL -> VERIFY -> CANARY -> CONTRACT -> RETIRED
Added outputs: [confidence]
Audit events: 6
```

## Core invariants

1. **Proposal before side effect** — a draft cannot execute before explicit approval.
2. **Terminal rejection** — a rejected proposal cannot later be approved or executed.
3. **Forward-only migrations** — a migration must keep the same skill identity and increase its version.
4. **Safe compatibility** — removing outputs, adding required inputs, adding permissions, or weakening an approval requirement is breaking.
5. **Verification before retirement** — the old contract is not retired until the verification stages pass.
6. **Deterministic auditability** — every accepted context item, proposal transition, and migration plan receives a monotonically increasing event sequence.

## Project structure

```text
src/main/java/dev/evolveos/
├── context/     # context records and the local in-memory store
├── contract/    # typed, versioned skill contracts
├── proposal/    # proposal state machine and inbox
├── migration/   # expand-contract plans and stages
├── event/       # sealed domain events and append-only log
├── skill/       # deterministic morning-review reference skill
├── cli/         # runnable local demo
└── EvolveEngine.java
```

See [docs/architecture.md](docs/architecture.md) for boundaries and design decisions, and [examples/morning-review.md](examples/morning-review.md) for the full sample workflow.

## Deliberate non-goals for v0.1

- no web UI;
- no real LLM or model-provider integration;
- no autonomous external side effects;
- no durable database or JSON persistence;
- no multi-tenancy, auth, billing, or hosted service;
- no production connectors.

These are intentionally deferred until the contract and safety model has earned trust through tests.

## Project tracking

GitHub is the source of truth for code, commits, CI, branches, and pull requests. Product decisions, milestones, worklogs, and project knowledge are tracked separately in Worklog Buddy. The discovery contract is committed as [`worklogger_secondbrain.json`](worklogger_secondbrain.json); it contains tool metadata and environment-variable names, never secret values.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md). Small, test-first changes that preserve the core invariants are preferred.

## Security

EvolveOS v0.1 performs no network calls and contains no real provider credentials. See [SECURITY.md](SECURITY.md) for reporting guidance.

## License

[MIT](LICENSE)
