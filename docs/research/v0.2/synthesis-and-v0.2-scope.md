# EvolveOS v0.2 research synthesis and selected scope

Status: proposed decision
Branch: `research/v0.2-discovery`

## Executive decision

EvolveOS v0.2 should implement one narrow execution boundary:

```text
immutable effect proposal
→ approval artifact
→ idempotent ActionExecutor
→ ExecutionReceipt
```

The release should prove that one approval authorizes one exact effect and that retrying the same logical execution cannot produce a second effect.

V0.2 should **not** implement a workflow scheduler, distributed durable execution engine, generic checkpoint database, tracing backend, MCP client ecosystem, or full JSON Schema compatibility engine.

## What the research changed

The pre-research default was already `ActionExecutor + ExecutionReceipt + idempotency`, with durable orchestration deferred. The research strengthened that choice but changed the required evidence:

1. The effect must be bound to an immutable contract/proposal fingerprint, not only a proposal ID.
2. Approval must become an artifact tied to exact arguments, permissions and policy, not just a boolean/state transition.
3. A duplicate execution attempt must return or reference the first receipt without invoking the effect again.
4. The receipt must contain enough stable identity to link future runtime traces and migration evidence.
5. V0.2 must state that its idempotency proof is process-local; crash-safe outbox/recovery remains a later persistence slice.

## Source-backed findings

### Do not build another runtime

LangGraph already provides persisted graph checkpoints, interrupts and recovery boundaries; Temporal provides Event History replay, Activity retries and durable message passing. Both document effect-repetition windows that require idempotent side effects rather than magical exactly-once execution ([LangGraph interrupts](https://docs.langchain.com/oss/python/langgraph/interrupts), [Temporal Activity idempotency](https://docs.temporal.io/activity-definition#idempotency)).

The broader landscape comparison reaches the same product conclusion: LangGraph, the OpenAI Agents SDK, AutoGen and CrewAI already own execution loops, state, pause/resume and observability surfaces. Rebuilding those systems would not validate EvolveOS's contract-evolution thesis; the control plane should sit beside or above a runtime ([landscape research](open-source-agent-landscape.md)).

### Approval is not enough without effect identity

Temporal documents that an Activity may execute or partially execute more than once while the Workflow observes one completion, and proposes stable run/activity identity for idempotency ([Temporal Activity definition](https://docs.temporal.io/activity-definition#idempotency)). LangGraph documents that code before an interrupt runs again after resume, so side effects before that boundary must be idempotent or isolated ([LangGraph interrupt rules](https://docs.langchain.com/oss/python/langgraph/interrupts#side-effects-called-before-interrupt-must-be-idempotent)).

For EvolveOS, this means `APPROVED → EXECUTED` is not a sufficient contract. The system must identify the logical effect, record attempts and return one stable receipt.

### Contract identity has more than one version axis

The current MCP specification negotiates a date-based protocol revision and capabilities per request, while Semantic Versioning applies to a declared public API. JSON Schema selects a dialect and validates instance shape but does not itself decide whether a schema change is backward compatible ([MCP versioning](https://modelcontextprotocol.io/specification/2026-07-28/basic/versioning), [SemVer 2.0.0](https://semver.org/spec/v2.0.0.html), [JSON Schema Core](https://json-schema.org/draft/2020-12/json-schema-core)).

A future EvolveOS contract therefore needs separate protocol, release, contract revision, schema dialect and content fingerprint fields. V0.2 does not need to implement the full schema engine, but it should introduce a canonical fingerprint seam now ([contract research](contracts-and-mcp-evolution.md)).

### Runtime telemetry is not governance evidence

Existing runtimes already expose tracing and checkpoint history. EvolveOS should link to native run/trace identifiers instead of creating another trace viewer. Its differentiated record is narrower: which contract and permissions were proposed, who approved them, what logical effect was attempted and which receipt proves the outcome ([landscape research](open-source-agent-landscape.md)).

The observability research adds an evidence constraint: decisions and outcomes are separate records linked by durable IDs, while trace IDs provide operational correlation rather than audit identity. Receipts should default to structured fingerprints and redacted summaries, not raw prompts, tool arguments, credentials or provider payloads ([observability research](observability-evaluation-and-safety.md)).

## Candidate capability classification

### Integrate

- durable checkpointing, retry scheduling and crash recovery from Temporal/LangGraph-class runtimes;
- framework-native run, thread and trace identifiers;
- MCP capability and tool discovery;
- OpenTelemetry-compatible tracing exports;
- provider-level idempotency keys where supported.

### Differentiate

- immutable agent/tool contract fingerprints;
- compatibility rules that include permissions and approval policy;
- approval artifacts bound to exact effect proposals;
- execution receipts usable as migration evidence;
- evidence-backed expand/dual-run/verify/canary/retire gates across runtimes.

### Defer

- durable database and transactional outbox;
- restart-safe human requests and worker leases;
- complete JSON Schema inclusion/compatibility engine;
- current MCP adapter and capability cache;
- executable migration registry and consumer inventory;
- second runtime adapter;
- visual control plane.

### Reject for the EvolveOS core

- a new LLM agent loop or graph scheduler;
- a generic multi-agent protocol;
- a broad connector marketplace;
- a proprietary tracing backend;
- a general chat-memory/vector database.

## Selected v0.2 domain slice

### New concepts

#### EffectProposal

An immutable proposal containing:

- `proposalId`;
- `contractName`, `contractVersion` and `contractFingerprint`;
- canonical `argumentsHash`;
- proposed action/effect type;
- required permissions;
- risk/effect summary;
- deterministic `logicalEffectId`.

#### ApprovalArtifact

A decision bound to:

- approval ID and proposal ID;
- proposal/effect fingerprint;
- granted permissions;
- policy version;
- approver identity string for the local reference implementation;
- decision time supplied by an injected clock;
- approved/rejected decision.

Changing arguments, target, fingerprint or permissions invalidates the artifact.

#### ActionExecutor

A one-method port:

```java
ExecutionReceipt execute(ApprovedEffect effect);
```

V0.2 ships a deterministic `RecordingActionExecutor`. It performs one observable in-memory effect and records invocation count by logical effect ID.

#### ExecutionReceipt

A stable result containing:

- `evidenceId` and evidence schema version;
- receipt ID;
- logical effect ID and idempotency key;
- proposal and approval IDs as explicit parent evidence;
- contract fingerprint and relevant policy/config versions;
- status: `SUCCEEDED`, `DENIED` or `FAILED`;
- attempt number;
- executor/provider reference;
- effect-result hash or normalized redacted result summary;
- occurrence/recording time supplied by an injected clock;
- data classification and redaction rule-set version;
- integrity digest;
- optional future `runtimeRunId`, `traceId` and `spanId` fields.

#### ExecutionRegistry

A process-local registry keyed by logical effect/idempotency key. The first accepted execution stores its receipt. A repeated call returns the existing receipt and must not call the executor again.

## Required acceptance tests

1. A newly created effect proposal is not executable without a matching approval artifact.
2. A rejected proposal never invokes the executor.
3. An approval for a different proposal fingerprint, arguments hash or permission set is rejected.
4. The first approved execution invokes the recording executor exactly once and returns `SUCCEEDED`.
5. Repeating the same execution returns the original receipt and keeps invocation count at one.
6. A genuinely new logical effect uses a new idempotency key and may execute once.
7. A failing executor returns a stable `FAILED` receipt; retry semantics are explicit and cannot silently create a second unkeyed effect.
8. Every accepted and denied attempt appends an attributable audit event with proposal, approval, contract and effect identity.
9. All outputs remain deterministic under a fixed clock and deterministic ID factory.
10. The packaged CLI demo shows proposal → approval artifact → one effect → duplicate retry → same receipt.

## Non-goals for v0.2

- No network calls or real email/payment/task provider.
- No required runtime trace identity; the receipt only reserves optional run/trace/span references.
- No raw prompts, credentials, tool arguments or provider payloads in general receipt telemetry.
- No claim of crash-safe durability or exactly-once external execution.
- No database, outbox, lease or background worker.
- No full durable pause/resume.
- No MCP transport adapter.
- No full JSON Schema compatibility analysis.
- No UI, auth system or multi-user approval service.
- No production approver identity/signature guarantee.

## Decision gates

### Gate 1 — Duplication: PASS

The slice does not rebuild scheduling, checkpointing or tracing. It adds EvolveOS-specific policy/effect binding around a port.

### Gate 2 — Differentiation: CONDITIONAL PASS

The idea is differentiated only when contract fingerprint, permission set and approval artifact remain part of the execution receipt. A generic command bus would fail this gate.

### Gate 3 — Testable effect: PASS BY DESIGN

The recording executor provides one observable effect and an invocation count without external infrastructure.

### Gate 4 — Portability: PASS BY DESIGN

The slice remains Java 21, deterministic, local and API-key free.

### Gate 5 — Future integration path: PASS BY INTERFACE

`ActionExecutor`, receipt references and contract fingerprints form seams for a later MCP or durable-runtime adapter without putting runtime semantics into the core.

## Outcome against pre-research hypotheses

| Hypothesis | Outcome | Reason |
|---|---|---|
| H1 — differentiate on evolution, not orchestration | Supported | Every reviewed runtime already owns stronger execution/state primitives; the uncovered gap is semantic contract governance. |
| H2 — one observable effect in v0.2 | Supported and strengthened | The effect must be idempotency-keyed and bound to an approval/contract fingerprint. |
| H3 — durable pause/resume later | Supported | Source research shows it is a substantial runtime concern; v0.2 should expose adapter seams but not build it. |
| H4 — migrations need runtime evidence | Supported, deferred | Execution receipts are the first evidence primitive; dual-run comparison and executable migration gates follow later. |

## Recommended sequence after this research branch

1. Review and approve this scope.
2. Convert the selected slice into test-first tickets.
3. Implement it on a new feature branch from `main`.
4. Keep the research and LinkedIn package in a separate reviewable documentation PR.
5. Publish the first post only after the final v0.2 scope is approved; do not imply v0.2 already exists.
