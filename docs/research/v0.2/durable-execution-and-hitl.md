# Durable Agent Execution and Human-in-the-Loop Patterns for EvolveOS v0.2

## Scope and evidence standard

This note compares Temporal and LangGraph using only first-party documentation. It covers durable execution, checkpoints, retries, human-in-the-loop (HITL) pause/resume, idempotency, and failure recovery. The optional third runtime is intentionally omitted so the analysis stays focused on the strongest collected evidence.

Sections labeled **Source facts** report documented runtime behavior. Sections labeled **Recommendations for EvolveOS** are design judgments, not claims about either runtime.

## Executive synthesis — source facts

- Temporal reconstructs workflow state by deterministically replaying commands against an Event History; after failure, execution resumes from the last recorded event rather than from a serialized program counter ([Temporal: Workflow Execution — Replays](https://docs.temporal.io/workflow-execution#replays)).
- LangGraph persists graph-state snapshots at super-step boundaries, with task-level pending writes inside a super-step; recovery therefore resumes at graph/node boundaries rather than at an arbitrary source line ([LangGraph: Checkpointers — Checkpoints and super-steps](https://docs.langchain.com/oss/python/langgraph/checkpointers#checkpoints)).
- Neither model eliminates duplicate external execution by itself: a Temporal Activity can execute more than once even though the workflow observes one completion, while a LangGraph node containing an interrupt restarts from its beginning on resume ([Temporal: Activity retry policy](https://docs.temporal.io/activity-definition#activity-retry-policy); [LangGraph: Rules of interrupts](https://docs.langchain.com/oss/python/langgraph/interrupts#rules-of-interrupts)).
- Temporal exposes asynchronous Signals, synchronous tracked Updates, and read-only Queries for live workflow interaction; LangGraph exposes persisted interrupts resumed with a `Command` on the same thread ([Temporal: Workflow message passing](https://docs.temporal.io/encyclopedia/workflow-message-passing); [LangGraph: Interrupts](https://docs.langchain.com/oss/python/langgraph/interrupts)).
- The common durable-agent lesson is boundary-based recovery: persist control state before relinquishing control, assume an effect attempt may be repeated, and make resume a durable command rather than an in-memory callback. The first two premises follow directly from the runtimes’ documented recovery and replay behavior ([Temporal: Activity idempotency](https://docs.temporal.io/activity-definition#idempotency); [LangGraph: Checkpointers — fault tolerance](https://docs.langchain.com/oss/python/langgraph/checkpointers#why-use-checkpointers)).

# Part I — Source facts

## 1. Temporal

### 1.1 Durable record and recovery model

A Temporal Workflow Execution is documented as durable and fully recoverable, with persisted state surviving Worker or service outages ([Temporal: Workflow Execution overview](https://docs.temporal.io/workflow-execution)). Replay re-executes workflow code, compares the emitted Commands with the existing Event History, and resumes after the last recorded event ([Temporal: Workflow Execution — Replays](https://docs.temporal.io/workflow-execution#replays)). The Worker’s in-memory workflow cache is an optimization: if cached state is evicted, the Worker restores it by replaying Event History ([Temporal: Workflow cache](https://docs.temporal.io/workflow-execution#workflow-cache)).

Workflow code must emit the same Workflow API calls in the same sequence for the same history; non-deterministic operations such as API calls, database queries, and LLM invocations belong in Activities outside the replay path ([Temporal: Deterministic constraints](https://docs.temporal.io/workflow-definition#deterministic-constraints)). Temporal documents Worker failures as infrastructure conditions from which workflow state and progress recover, rather than as reasons for the Workflow Execution itself to fail ([Temporal: Handling unreliable Worker Processes](https://docs.temporal.io/workflow-definition#unreliable-worker-processes)).

Temporal’s Event History grows with workflow progress, and `Continue-As-New` passes relevant state into a new run under the same Workflow ID with a fresh Event History; Temporal describes this as checkpointing workflow state to start a fresh workflow ([Temporal: Continue-As-New](https://docs.temporal.io/workflow-execution/continue-as-new)).

### 1.2 Retries and effect semantics

Activities have an automatic Retry Policy by default, whereas Workflow Executions do not have a Retry Policy by default ([Temporal: Retry Policy — default behavior](https://docs.temporal.io/encyclopedia/retry-policies#default-behavior)). The default Activity policy uses exponential backoff, and the policy can constrain intervals, attempts, and non-retryable error types ([Temporal: Retry Policy — properties](https://docs.temporal.io/encyclopedia/retry-policies#properties)).

A completed Activity is not re-executed merely because workflow code replays, but an Activity that completed on a Worker and failed to report completion can be dispatched again ([Temporal: Activity idempotency](https://docs.temporal.io/activity-definition#idempotency)). Temporal therefore recommends idempotent Activities and explicitly states that an Activity may execute or partially execute multiple times while its completion is observed once by the Workflow ([Temporal: Activity retry policy](https://docs.temporal.io/activity-definition#activity-retry-policy)). The documentation proposes a Workflow Run ID plus Activity ID as a stable idempotency key across Activity retry attempts ([Temporal: Activity idempotency](https://docs.temporal.io/activity-definition#idempotency)).

For crash detection, Start-To-Close Timeout bounds one Activity attempt and allows retry after a Worker disappears ([Temporal: Detecting Activity failures — Start-To-Close](https://docs.temporal.io/encyclopedia/detecting-activity-failures#start-to-close-timeout)). Long-running Activities can heartbeat with an application payload; after a heartbeat timeout, the next attempt can read that payload and continue application-level progress ([Temporal: Activity Heartbeat](https://docs.temporal.io/encyclopedia/detecting-activity-failures#activity-heartbeat)).

### 1.3 HITL and pause/resume

Temporal defines Queries as non-blocking reads, Signals as asynchronous writes with no response, and Updates as synchronous tracked writes whose sender may await completion or failure ([Temporal: Workflow message passing](https://docs.temporal.io/encyclopedia/workflow-message-passing)). Workflow code can suspend while waiting on SDK-provided awaitables, and the Worker sends accumulated Commands to the service when the workflow can no longer progress without an awaited result ([Temporal: Commands and awaitables](https://docs.temporal.io/workflow-execution#commands-and-awaitables)). Together, those primitives support a workflow that durably waits for an external decision while no application call stack must remain alive ([Temporal: Workflow message passing](https://docs.temporal.io/encyclopedia/workflow-message-passing); [Temporal: Workflow durability](https://docs.temporal.io/workflow-execution#what-is-a-workflow-execution)).

Temporal also documents a distinct operational Workflow Pause control. In its documented pre-release form, Pause stops dispatch of new Workflow and Activity Tasks, accepts and records Signals, lets timers and timeouts continue, and does not interrupt already running Activity attempts ([Temporal: Workflow Pause](https://docs.temporal.io/encyclopedia/workflow/workflow-pause)). Unpause resumes from existing state and processes pending signals and fired timers; the feature is explicitly described as an operator control rather than an API intended for workflow code ([Temporal: Workflow Pause — Unpause and limitations](https://docs.temporal.io/encyclopedia/workflow/workflow-pause#unpause)).

## 2. LangGraph

### 2.1 Durable record and checkpoints

A LangGraph checkpointer saves a graph-state snapshot at every super-step and organizes checkpoints by thread ([LangGraph: Checkpointers](https://docs.langchain.com/oss/python/langgraph/checkpointers)). A `thread_id` is the primary key used to save and reload a thread’s checkpoints; without it, the checkpointer cannot resume after an interrupt ([LangGraph: Threads](https://docs.langchain.com/oss/python/langgraph/checkpointers#threads)).

A full checkpoint is committed at a super-step boundary, while each successfully completed node in an in-progress super-step writes task-level output. If a sibling node fails, those pending writes prevent already successful sibling nodes from being rerun on resume ([LangGraph: Pending writes and super-steps](https://docs.langchain.com/oss/python/langgraph/checkpointers#pending-writes)). Replaying from an older checkpoint skips nodes before it and re-executes nodes after it, including LLM calls, API requests, and interrupts ([LangGraph: Checkpointer replay](https://docs.langchain.com/oss/python/langgraph/checkpointers#replay)).

Durability depends on both backend and write mode. LangGraph documents `exit` mode as persisting only when execution exits, `async` mode as allowing a small checkpoint-loss window on process crash, and `sync` mode as persisting every checkpoint before the next step begins ([LangGraph: Durability modes](https://docs.langchain.com/oss/python/langgraph/checkpointers#durability-modes)). Its in-memory saver loses checkpoints on process restart, while the documentation identifies SQLite for local workflows and Postgres for production-oriented persistence ([LangGraph: Checkpointer libraries](https://docs.langchain.com/oss/python/langgraph/checkpointers#checkpointer-libraries)).

### 2.2 Retries and failure recovery

LangGraph Retry Policies rerun a failed node attempt according to exception matching and backoff settings; retry exhaustion can feed an error handler that updates state or routes to a compensation branch ([LangGraph: Fault tolerance — Retries and error handling](https://docs.langchain.com/oss/python/langgraph/fault-tolerance#retries)). Per-node timeouts can turn a stalled asynchronous attempt into a `NodeTimeoutError`, clear writes from that failed attempt, and pass the failure to the retry policy ([LangGraph: Fault tolerance — Timeouts](https://docs.langchain.com/oss/python/langgraph/fault-tolerance#timeouts)).

Checkpointing permits a failed graph to restart from the last successful step, and pending writes preserve successful nodes within a partially failed parallel super-step ([LangGraph: Checkpointers — fault tolerance](https://docs.langchain.com/oss/python/langgraph/checkpointers#why-use-checkpointers)). LangGraph also documents cooperative drain: after current super-step work completes, the graph saves a resumable checkpoint and can later continue with the same configuration ([LangGraph: Graceful shutdown](https://docs.langchain.com/oss/python/langgraph/fault-tolerance#graceful-shutdown)).

### 2.3 HITL and idempotency

`interrupt()` saves graph state through the checkpointer and waits indefinitely for external input; resumption reinvokes the graph with `Command(resume=...)` and the same `thread_id` ([LangGraph: Interrupts](https://docs.langchain.com/oss/python/langgraph/interrupts)). The interrupt payload and resume value must be serializable, and the thread ID acts as the persistent cursor identifying which checkpoint to load ([LangGraph: Pause and resume](https://docs.langchain.com/oss/python/langgraph/interrupts#pause-using-interrupt)).

On resume, LangGraph restarts the entire node containing the interrupt rather than continuing at the exact source line, so code before the interrupt runs again ([LangGraph: Rules of interrupts](https://docs.langchain.com/oss/python/langgraph/interrupts#rules-of-interrupts)). The official guidance is therefore to make pre-interrupt side effects idempotent, move effects after the interrupt, or isolate effects in separate nodes ([LangGraph: Side effects before interrupt](https://docs.langchain.com/oss/python/langgraph/interrupts#side-effects-called-before-interrupt-must-be-idempotent)).

## 3. Direct comparison of documented behavior

| Concern | Temporal | LangGraph | Design implication exposed by the comparison |
|---|---|---|---|
| Durable source of truth | [Append-only Event History plus deterministic workflow replay](https://docs.temporal.io/workflow-execution#replays). | [Serialized graph-state checkpoints plus task-level writes](https://docs.langchain.com/oss/python/langgraph/checkpointers#checkpoints). | EvolveOS must choose and document its authoritative recovery record; an in-memory agent object is insufficient. |
| Resume granularity | [Last recorded history event drives replay](https://docs.temporal.io/workflow-execution#replays); Activity retry restarts the Activity except for application progress carried in heartbeats ([heartbeats](https://docs.temporal.io/encyclopedia/detecting-activity-failures#activity-heartbeat)). | [Full recovery occurs at super-step boundaries](https://docs.langchain.com/oss/python/langgraph/checkpointers#super-steps); an interrupted node restarts from its beginning ([interrupt rules](https://docs.langchain.com/oss/python/langgraph/interrupts#rules-of-interrupts)). | Public semantics should promise boundary-level resume, not line-level continuation. |
| Determinism | [Workflow code must reproduce the same command sequence](https://docs.temporal.io/workflow-definition#deterministic-constraints). | [Checkpoint replay reruns post-checkpoint nodes and their LLM/API calls](https://docs.langchain.com/oss/python/langgraph/checkpointers#replay), without a Temporal-style command-history matching contract. | Persist non-deterministic outputs or treat their regeneration as an explicit new attempt. |
| Retry scope | [Activities retry by default; Workflows do not](https://docs.temporal.io/encyclopedia/retry-policies#default-behavior). | [Retry policy is configured on nodes or tasks](https://docs.langchain.com/oss/python/langgraph/fault-tolerance#retries). | Retry policy belongs to the effectful step, not indiscriminately to the whole agent run. |
| Duplicate effects | [Activity execution may repeat after an ambiguous completion](https://docs.temporal.io/activity-definition#idempotency). | [Pre-interrupt code reruns and replay can repeat post-checkpoint API calls](https://docs.langchain.com/oss/python/langgraph/interrupts#side-effects-called-before-interrupt-must-be-idempotent). | Stable idempotency keys and deduplication are correctness requirements. |
| HITL primitive | [Signals, Updates, and Queries](https://docs.temporal.io/encyclopedia/workflow-message-passing). | [`interrupt()` plus `Command(resume=...)` on a thread](https://docs.langchain.com/oss/python/langgraph/interrupts#resuming-interrupts). | Model a human response as a durable, validated command correlated to a pending request. |
| Operational pause | [Pre-release Workflow Pause stops new dispatch but does not freeze timers, timeouts, or in-flight Activities](https://docs.temporal.io/encyclopedia/workflow/workflow-pause#what-happens-when-you-pause-a-workflow). | [Cooperative drain stops after a super-step and saves a resumable checkpoint](https://docs.langchain.com/oss/python/langgraph/fault-tolerance#graceful-shutdown); semantic HITL uses interrupts. | Separate “waiting for a decision,” “operator-paused,” and “worker draining” states. |
| Long-running state growth | [`Continue-As-New` carries relevant state into a fresh history](https://docs.temporal.io/workflow-execution/continue-as-new). | [Checkpoints accumulate and can require retention or pruning](https://docs.langchain.com/oss/python/langgraph/checkpointers#optimize-checkpoint-storage). | Define snapshot, compaction, and audit-retention policy before histories become unbounded. |
| Partial parallel failure | Activity and workflow progress is represented through [recorded Events and Commands](https://docs.temporal.io/workflow-execution#what-is-a-command). | [Pending writes retain successful sibling-node outputs when another node fails](https://docs.langchain.com/oss/python/langgraph/checkpointers#pending-writes). | Parallel branches need per-task commit records, not one all-or-nothing in-memory result. |

# Part II — Recommendations for EvolveOS v0.2

The remainder of this note is a proposed EvolveOS design. It is intentionally normative and does not describe guarantees already provided by the current codebase.

## 4. Choose step-boundary checkpointing for v0.2

Use a LangGraph-like boundary-checkpoint model for v0.2 rather than attempting to build a Temporal-like deterministic replay engine now.

Reasons:

1. The agent domain already has natural boundaries: model call, tool proposal, approval, tool execution, observation, and finalization.
2. Persisted state plus an explicit `next_step` is simpler to inspect, migrate, and expose in UI than replay-compatible application code.
3. Temporal’s stronger replay model also imposes deterministic workflow-code and versioning obligations that would materially expand v0.2 scope ([Temporal: Deterministic constraints and workflow versioning](https://docs.temporal.io/workflow-definition#deterministic-constraints)).
4. The design can retain an append-only event log beside snapshots, leaving a later path to stronger replay and audit capabilities.

**Required semantic contract:** EvolveOS resumes from the last committed step boundary. It does not claim to resume from the exact source line or to provide exactly-once execution of external effects.

## 5. Make run state a durable state machine

Persist one authoritative run record with a monotonic `revision` and one of these explicit statuses:

- `queued`
- `running`
- `waiting_retry`
- `waiting_human`
- `paused_operator`
- `succeeded`
- `failed`
- `cancelled`

Every accepted transition should atomically write:

- prior and next status;
- run ID, step ID, and attempt ID;
- workflow-definition and state-schema versions;
- state snapshot or content-addressed snapshot reference;
- next executable step;
- triggering command/event ID;
- timestamp, actor, and reason;
- monotonic run revision.

Use compare-and-swap on the expected revision so two workers, two resume requests, or a retry racing an operator action cannot both advance the run.

## 6. Separate three kinds of “pause”

1. **Semantic wait (`waiting_human`)** — the workflow requires a decision or missing input. No worker remains occupied. A durable human request is the next step.
2. **Operational hold (`paused_operator`)** — an operator prevents new step dispatch during an incident or investigation. In-flight effects are not implied to be cancelled.
3. **Worker drain** — infrastructure stops assigning new work and lets the current atomic step reach a checkpoint. This is not persisted as a business decision unless it changes the run.

Do not overload one boolean `paused` field for these states; their permissions, timer behavior, and audit meaning differ.

## 7. Treat human input as a durable command

Create a `human_requests` record before returning control to the UI or notification layer. It should contain:

- stable `request_id` and `run_id`;
- originating `step_id` and expected run `revision`;
- request kind: `approve`, `edit`, `choose`, or `provide_input`;
- JSON-schema-validated response shape;
- immutable proposed action plus an action hash;
- risk summary and the minimum context needed to decide;
- allowed actors/roles;
- created, expiry, and resolved timestamps;
- resolution command ID and resolver identity.

Resume should be an idempotent command keyed by `command_id`. Accept it only if the request is unresolved, the expected run revision still matches, the actor is authorized, and the action hash still identifies the proposal being approved. Record stale, duplicate, unauthorized, and malformed decisions as rejected audit events without advancing execution.

Approval must authorize a specific immutable effect proposal, not merely “continue.” If a reviewer edits tool arguments, create a new proposal hash and persist the edited payload before execution.

## 8. Isolate effects and design for at-least-once attempts

Each external side effect should be its own step with a stable idempotency key derived from durable identity, for example:

`effect_key = run_id + step_id + logical_effect_index`

Keep that key stable across retries; use a new key only when the user or policy creates a genuinely new logical effect. Pass it to providers that support idempotency and maintain an EvolveOS deduplication record for providers that do not.

Use an effect outbox:

1. Atomically checkpoint the approved effect intent and enqueue an outbox item.
2. Dispatch the item with its stable idempotency key.
3. Persist provider receipt/result and mark the outbox item complete.
4. Atomically advance the run from that persisted result.

A crash after dispatch but before acknowledgement must cause reconciliation or redispatch with the same key, never an unkeyed second effect. Where a provider offers neither idempotency nor query-by-client-reference, expose the result as `effect_status=unknown` and require reconciliation or human action rather than silently retrying a destructive operation.

## 9. Make retries typed, bounded, and observable

Define policy per step type, not per whole run:

- **Transient:** retry automatically with bounded exponential backoff and jitter.
- **Rate-limited:** honor provider retry hints, then apply bounded backoff.
- **Permanent/input:** fail fast or create a human request.
- **Policy/authorization:** never retry unchanged input automatically.
- **Ambiguous external outcome:** reconcile by idempotency key or client reference before any redispatch.
- **Model-quality failure:** route through a bounded repair loop whose attempts and outputs are persisted.

Persist attempt number, normalized error class, provider code, retry decision, next-at timestamp, and terminal reason. After exhaustion, transition explicitly to `failed` or a compensation/remediation step; never leave the run indefinitely “running.”

## 10. Recover through leases, heartbeats, and committed boundaries

Workers should claim a step with a renewable lease. A recovery sweeper may reclaim an expired lease only after verifying that no committed completion exists for that attempt.

For long-running steps, heartbeat durable progress only when it is meaningful and restartable, such as a completed batch cursor or uploaded-part number. A heartbeat is not a substitute for an effect receipt or a committed step result.

Recovery behavior should be deterministic from stored records:

| Failure window | Required v0.2 behavior |
|---|---|
| Before effect intent is committed | No effect is eligible to run. |
| After intent commit, before dispatch | Outbox dispatcher sends it. |
| After dispatch, before receipt commit | Reconcile or redispatch with the same idempotency key. |
| After result commit, before run advance | Reapply the idempotent state transition from the stored result. |
| While waiting for a human | Reload the unresolved request; no agent stack or worker must be present. |
| Worker dies during a pure computation | Lease expires; start a new attempt from the last checkpoint. |
| Worker dies during an external effect | Reconcile first; do not assume failure. |
| Duplicate or stale resume arrives | Deduplicate by command ID and reject revision mismatch. |

## 11. Persist non-determinism deliberately

For every model invocation, persist enough provenance to distinguish replay from a new attempt:

- invocation ID and attempt ID;
- provider, model identifier, and relevant generation settings;
- prompt/template version and input references;
- tool schema version;
- returned content/tool call or normalized error;
- usage and latency metadata where available.

Once a model result has been checkpointed, ordinary crash recovery should reuse it. Regeneration should be an explicit new attempt or branch, not an accidental consequence of process restart.

Random choices, current-time decisions, and external reads that affect routing should likewise become persisted step outputs. This keeps recovery stable without requiring Temporal-style command replay.

## 12. Version long-lived runs and bound history

Pin each run to a workflow-definition version and state-schema version. Support additive readers first; require an explicit migration or compatibility adapter before newer workers mutate old snapshots.

Retain an immutable audit event stream while periodically writing compact snapshots. Compaction may remove redundant intermediate snapshots only after verifying that:

- the latest snapshot reconstructs current state;
- unresolved human requests and outbox items remain reachable;
- effect receipts and security/audit events remain retained;
- the workflow and schema versions needed to interpret state are available.

## 13. Minimum verification gates for v0.2

Before calling execution durable, automate crash-injection tests at every persistence/effect boundary:

1. kill before and after checkpoint commit;
2. kill before and after outbox dispatch;
3. lose the effect response after the provider accepted it;
4. restart during retry backoff;
5. restart while waiting for approval;
6. submit the same approval twice;
7. race approval against cancellation and operator pause;
8. expire a worker lease during a long step;
9. deploy a new workflow version while an old run is waiting;
10. fail one branch of a parallel step after another branch committed output.

Acceptance criteria should assert both final state and external-effect count. A run that reaches the right state after sending an email or charge twice is not a successful durability test.

## 14. Recommended v0.2 invariants

1. **Committed-state invariant:** only a committed checkpoint determines the next step.
2. **Single-advancer invariant:** a run revision is advanced at most once.
3. **Stable-effect invariant:** retries of one logical effect reuse one idempotency key.
4. **Decision-binding invariant:** a human decision is bound to one request, proposal hash, and expected revision.
5. **No-live-stack invariant:** waiting and backoff states require no resident worker or call stack.
6. **Explicit-ambiguity invariant:** uncertain external outcomes are represented as uncertain, not guessed to be failed.
7. **Version invariant:** every snapshot is interpreted by a declared workflow and schema version.
8. **Audit invariant:** every accepted or rejected command and every effect attempt is attributable to an actor or worker.

These invariants give EvolveOS an honest v0.2 promise: durable, inspectable step-boundary recovery with at-least-once attempts and idempotent effect handling—not magical exactly-once execution.

## Sources

| # | First-party source | Used for |
|---:|---|---|
| 1 | [Temporal Workflow Execution overview](https://docs.temporal.io/workflow-execution) | Durability, replay, awaitables, Event History recovery, workflow cache |
| 2 | [Temporal Workflow Definition](https://docs.temporal.io/workflow-definition) | Determinism, Activity isolation, Worker failure recovery |
| 3 | [Temporal Activity Definition](https://docs.temporal.io/activity-definition) | Re-execution, idempotency, ambiguous completion, effect keys |
| 4 | [Temporal Retry Policies](https://docs.temporal.io/encyclopedia/retry-policies) | Default Activity versus Workflow retries and policy controls |
| 5 | [Temporal Workflow message passing](https://docs.temporal.io/encyclopedia/workflow-message-passing) | Signals, Updates, Queries, external interaction semantics |
| 6 | [Temporal Detecting Activity failures](https://docs.temporal.io/encyclopedia/detecting-activity-failures) | Timeouts, crash detection, heartbeats, progress payloads |
| 7 | [Temporal Continue-As-New](https://docs.temporal.io/workflow-execution/continue-as-new) | History rollover and state checkpointing into a fresh run |
| 8 | [Temporal Workflow Pause](https://docs.temporal.io/encyclopedia/workflow/workflow-pause) | Operational pause/unpause semantics and limitations |
| 9 | [LangGraph Checkpointers](https://docs.langchain.com/oss/python/langgraph/checkpointers) | Checkpoints, threads, pending writes, durability modes, replay |
| 10 | [LangGraph Interrupts](https://docs.langchain.com/oss/python/langgraph/interrupts) | HITL pause/resume, node restart, serialization, idempotency |
| 11 | [LangGraph Fault tolerance](https://docs.langchain.com/oss/python/langgraph/fault-tolerance) | Retries, timeouts, error handlers, compensation, graceful drain |
