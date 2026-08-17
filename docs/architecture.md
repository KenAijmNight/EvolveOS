# Architecture

EvolveOS v0.1 is a deliberately small, framework-free Java core. The architecture keeps policy in typed domain objects and treats integrations as future adapters rather than dependencies of the core.

## Boundary map

| Boundary | Responsibility | v0.1 implementation |
| --- | --- | --- |
| Context | Accept and search source material | `ContextStore`, `InMemoryContextStore` |
| Contract | Describe skill inputs, outputs, permissions, and approval policy | `SkillContract` |
| Skill | Convert context into a proposal | `DeterministicMorningReviewSkill` |
| Proposal | Guard human approval and execution transitions | `Proposal`, `ProposalInbox` |
| Migration | Plan safe contract evolution | `ContractMigrationPlanner` |
| Audit | Record accepted domain changes in order | `EventLog`, sealed `DomainEvent` hierarchy |
| Orchestration | Compose one vertical workflow | `EvolveEngine` |
| Delivery | Demonstrate the workflow without external services | `EvolveCli` |

## Domain invariants

### Skill contracts

A skill contract has a stable name and monotonically increasing version. The compatibility check treats a new contract as backward compatible only when it:

- keeps the same skill name;
- increments the version;
- preserves all old output fields;
- does not add required inputs;
- does not add permissions;
- does not remove an existing approval requirement.

A breaking contract can still be introduced, but it must use an explicit migration plan. The plan reports added and removed outputs, added permissions, added required inputs, and removal of an approval requirement.

### Proposal lifecycle

```text
             ┌──────────→ REJECTED
             │
DRAFT ───────┤
             │
             └──────────→ APPROVED ──────────→ EXECUTED
```

No other state transition is valid. In v0.1 `EXECUTED` means the proposal crossed the policy boundary; no external write is performed.

### Migration lifecycle

1. **DRAFT** — review the proposed contract and impact.
2. **EXPAND** — publish the new version alongside the old version.
3. **DUAL_RUN** — execute both versions and compare outputs.
4. **BACKFILL** — populate missing historical fields.
5. **VERIFY** — inspect readers, writers, evidence, and permissions.
6. **CANARY** — route a bounded subset to the new version.
7. **CONTRACT** — make the new version the default while retaining rollback.
8. **RETIRED** — remove the old version only after verification.

`VERIFY`, `CONTRACT`, and `RETIRED` are represented as blocking gates. The v0.1 planner is descriptive: it does not deploy versions, run backfills, advance stages, or retire a contract.

### Audit log

The in-memory audit log assigns a positive, monotonically increasing sequence to every accepted domain event. Failed commands do not append events. This keeps test output deterministic and leaves room for a durable event store adapter later.

## Dependency direction

```text
CLI → EvolveEngine → domain packages
                     ↑
             future adapters
```

The core has no Spring, database, HTTP, MCP, or model-provider dependency. This is intentional: future integrations should implement narrow ports without changing the domain rules.

## Testing strategy

The v0.1 suite covers:

- contract compatibility and invalid definitions;
- context search and duplicate identity protection;
- forbidden proposal transitions;
- ordered migration stages and contract diffs;
- the complete context-to-migration vertical slice;
- deterministic CLI output.

CI runs `./mvnw verify` and then executes the packaged demo jar.

## Deferred decisions

The following require separate design and migration work:

- durable JSON/SQLite/PostgreSQL storage;
- provider and MCP connector interfaces;
- asynchronous or concurrent execution;
- persisted approval identities and signatures;
- real side-effect handlers and idempotency keys;
- UI/API boundaries.
