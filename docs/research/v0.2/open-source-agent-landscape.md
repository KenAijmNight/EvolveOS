# Open-source agent landscape: EvolveOS duplication and differentiation

## Scope and method

This is a decision-focused comparison, not an exhaustive census. It is deliberately bounded to the four open-source agent frameworks whose current official documentation was reviewed for this v0.2 pass: LangGraph, the OpenAI Agents SDK, Microsoft AutoGen, and CrewAI. EvolveOS is included as the proposed control plane. Claims are grounded in official documentation or first-party repositories; GitHub stars and other popularity proxies are not used as evidence.

The comparison separates six capabilities that are often conflated:

1. **Durable state** — whether an interrupted or failed run can resume from persisted execution state, rather than merely retaining chat history.
2. **Approvals** — whether a side effect can be paused, reviewed, approved or rejected, and then resumed with an explicit decision.
3. **Contract/version handling** — whether the system understands compatibility between versions of agent-facing inputs, outputs, permissions, and approval policy.
4. **Auditability** — whether it records enough ordered evidence to reconstruct accepted decisions and execution, not just emit debugging telemetry.
5. **Local operation** — whether the useful core can run without a mandatory hosted control plane.
6. **Extensibility** — whether models, tools, state stores, policies, and observability sinks can be replaced or extended.

A capability is marked **first-class** only when the framework supplies the relevant abstraction and lifecycle. “Can be implemented in application code” is not treated as equivalent.

## Executive conclusion

- **EvolveOS should not become another agent runtime.** LangGraph already supplies graph execution, checkpointed state, interrupts, and time travel; the OpenAI Agents SDK supplies a compact agent loop, sessions, tool approvals, and tracing; AutoGen supplies composable single- and multi-agent runtimes with state snapshots and intervention hooks; CrewAI supplies crews, event-driven flows, persistence, human feedback, and an event system.
- **Most of EvolveOS v0.1's mechanisms are individually duplicated.** Local state, state machines, approval pauses, event streams, schema-shaped outputs, and observability hooks all exist elsewhere in stronger runtime implementations.
- **The defensible gap is narrower and more specific:** none of the reviewed frameworks makes a versioned *agent contract*—inputs, outputs, permissions, and approval policy—the unit of compatibility analysis, then manages a staged expand/dual-run/verify/canary/retire migration for that contract. This is a bounded conclusion about the reviewed official surfaces, not a claim about every agent project.
- **EvolveOS's differentiation is presently a thesis, not an operational advantage.** The repository explicitly says its migration planner is descriptive and that durable storage, asynchronous execution, persisted approver identities, side-effect handlers, and idempotency remain deferred ([architecture](../../architecture.md#deferred-decisions)). v0.2 must prove the contract-evolution layer against at least one real runtime rather than reproducing that runtime beneath it.

## Baseline: what EvolveOS actually provides

EvolveOS v0.1 describes itself as an experimental, framework-free Java control plane rather than a production agent runtime ([README](../../../README.md#evolveos)). Its implemented domain model includes:

- a stable skill name and increasing version;
- required inputs, outputs, permissions, and an approval requirement;
- a compatibility rule that treats removed outputs, new required inputs, new permissions, or removal of an approval requirement as breaking;
- a guarded `DRAFT → APPROVED/REJECTED → EXECUTED` proposal lifecycle;
- an eight-stage `DRAFT → EXPAND → DUAL_RUN → BACKFILL → VERIFY → CANARY → CONTRACT → RETIRED` migration plan; and
- positive, monotonically increasing in-memory audit-event sequence numbers ([architecture: domain invariants](../../architecture.md#domain-invariants)).

Those semantics are important, but the current implementation does **not** persist state across restarts, execute migration stages, call a model, perform a side effect, identify or authenticate an approver, or provide concurrency/idempotency guarantees ([README: non-goals](../../../README.md#deliberate-non-goals-for-v01), [architecture: deferred decisions](../../architecture.md#deferred-decisions)). The landscape comparison therefore evaluates EvolveOS as a policy and migration model, not as a mature runtime.

## Capability matrix

| System | Durable state | Approval boundary | Contract/version handling | Auditability | Local operation | Extensibility |
| --- | --- | --- | --- | --- | --- | --- |
| **EvolveOS v0.1** | **Modeled only.** Context, proposals, and events are in memory; durable adapters are deferred. | **Explicit domain state machine, not durable.** A proposal cannot execute before approval and rejection is terminal, but identity/signature persistence is absent. | **First-class and distinctive.** Compatibility covers inputs, outputs, permissions, and approval weakening; a staged migration plan is generated. The stages are not executed. | **Ordered but in-memory.** Accepted events receive a sequence; this is not yet a tamper-resistant or restart-safe ledger. | **Yes.** Deterministic Java core; no network or provider dependency. | **Architecturally intended, minimally implemented.** Future adapters are isolated from the domain, but provider/MCP/store ports are deferred. |
| **LangGraph** | **First-class.** Checkpointers persist per-thread graph snapshots and stores hold cross-thread data; persistence supports continuation, fault recovery, human-in-the-loop, and time travel ([persistence](https://docs.langchain.com/oss/python/langgraph/persistence)). | **First-class pause/resume primitive, application-defined policy.** `interrupt()` checkpoints the graph and returns control; the caller resumes with a value, which can encode approve/reject ([interrupts](https://docs.langchain.com/oss/python/langgraph/interrupts)). It does not define EvolveOS-style proposal identity, permission policy, or approver evidence. | **Operational graph migration, not semantic contract evolution.** LangGraph documents updating graph definitions used by existing threads and constraints around changed nodes/state ([Graph API](https://docs.langchain.com/oss/python/langgraph/graph-api#graph-migrations)). It does not classify a new permission or weakened approval as a breaking consumer contract. | **Strong execution forensics.** Checkpoint history can be inspected, replayed, and forked through time travel ([time travel](https://docs.langchain.com/oss/python/langgraph/use-time-travel)). That is not by itself an immutable governance ledger. | **Yes.** The library is available in the first-party open-source repository and includes in-memory/local execution paths ([repository](https://github.com/langchain-ai/langgraph)). Production durability depends on the selected checkpointer/store. | **Strong.** Graph nodes, state, checkpointers, stores, tools, and runtime context are composable through the graph APIs ([Graph API](https://docs.langchain.com/oss/python/langgraph/graph-api)). |
| **OpenAI Agents SDK** | **Partial.** Sessions automatically retrieve history before a run and store new items after it, with SQLite, Redis, SQLAlchemy, Dapr, MongoDB, and hosted conversation implementations ([sessions](https://openai.github.io/openai-agents-python/sessions/)). Interrupted run state can be resumed, but the SDK is not a general crash-replay workflow engine ([running agents](https://openai.github.io/openai-agents-python/running_agents/)). | **First-class for tool calls.** Tools can require approval; a run returns interruptions, the application approves or rejects them, and execution resumes from the resulting state ([human-in-the-loop](https://openai.github.io/openai-agents-python/human_in_the_loop/)). Approver identity, signatures, and organization policy remain application concerns. | **No first-class migration lifecycle in the reviewed surface.** Typed agent outputs and tool schemas constrain one version at runtime, but sessions, run state, and approvals do not compare old/new consumer contracts or stage their rollout. | **Strong tracing, weaker governance audit.** Built-in traces capture agent runs, generations, function calls, guardrails, handoffs, and custom spans, and processors can export them ([tracing](https://openai.github.io/openai-agents-python/tracing/)). Tracing is observability; it is not specified as an append-only approval or migration ledger. | **Mostly.** The MIT-licensed SDK and SQLite sessions run locally ([repository](https://github.com/openai/openai-agents-python), [sessions](https://openai.github.io/openai-agents-python/sessions/)). The default provider is OpenAI, while the model abstraction and third-party adapters allow other providers ([models](https://openai.github.io/openai-agents-python/models/)). | **Strong.** Function tools, handoffs, guardrails, MCP integration, model providers, session implementations, and trace processors are extension points documented by the SDK ([repository](https://github.com/openai/openai-agents-python)). |
| **Microsoft AutoGen** | **Partial and explicit.** Agents, teams, and termination conditions expose `save_state()`/`load_state()`; the official guide frames external persistence as the application's responsibility ([managing state](https://microsoft.github.io/autogen/stable/user-guide/agentchat-user-guide/tutorial/state.html)). This is portable snapshotting, not automatic durable replay of every side effect. | **Hooks rather than a durable approval domain.** AgentChat documents user input during a run and stop/continue patterns ([human-in-the-loop](https://microsoft.github.io/autogen/stable/user-guide/agentchat-user-guide/tutorial/human-in-the-loop.html)); Core intervention handlers can inspect or modify messages such as tool calls ([intervention handlers](https://microsoft.github.io/autogen/stable/user-guide/core-user-guide/framework/intervention.html)). Applications still own the decision record and authorization policy. | **No first-class agent-contract migration in the reviewed surface.** Serialized component state contains a state-model version, but that version protects framework serialization; it does not analyze changes to a skill's outputs, permissions, or approval requirement ([managing state](https://microsoft.github.io/autogen/stable/user-guide/agentchat-user-guide/tutorial/state.html)). | **Observability rather than an audit ledger.** AutoGen integrates logging and OpenTelemetry tracing for agent and runtime activity ([telemetry](https://microsoft.github.io/autogen/stable/user-guide/core-user-guide/framework/telemetry.html)); no reviewed primitive establishes an append-only approval/migration record. | **Yes.** AutoGen's Core, AgentChat, and Extensions packages are published from the first-party open-source repository ([repository](https://github.com/microsoft/autogen)); deployment and persistence remain under application control. | **Strong.** The layered Core/AgentChat/Extensions architecture, custom agents, model clients, tools, runtimes, and intervention handlers are explicit extension surfaces ([repository](https://github.com/microsoft/autogen), [intervention handlers](https://microsoft.github.io/autogen/stable/user-guide/core-user-guide/framework/intervention.html)). |
| **CrewAI** | **Partial to strong for flows.** Flows carry structured or unstructured state, and persistence can be applied to a method or an entire flow; the documented default persistence implementation uses SQLite ([Flows](https://docs.crewai.com/en/concepts/flows#persistence)). This is useful local checkpointing but is not presented as deterministic replay across arbitrary external side effects. | **First-class human feedback point, application-defined policy.** CrewAI supports human input during execution and human-in-the-loop flow patterns ([human-in-the-loop](https://docs.crewai.com/en/learn/human-in-the-loop)). It does not supply EvolveOS's versioned permission/approval compatibility rule. | **No first-class migration lifecycle in the reviewed surface.** Pydantic flow state and structured task output describe current shapes; the docs do not expose old/new compatibility classification, dual-run evidence, or staged retirement of a contract. | **Rich events/tracing, not an immutable governance log.** Event listeners receive execution lifecycle events and can feed custom monitoring ([event listeners](https://docs.crewai.com/en/concepts/event-listener)); CrewAI tracing records execution details for observability ([tracing](https://docs.crewai.com/en/observability/tracing)). Durability and immutability of a compliance record are not guaranteed by those facilities. | **Yes, with optional services.** The framework is distributed from its first-party open-source repository ([repository](https://github.com/crewAIInc/crewAI)); flows and default SQLite persistence can run locally. LLM configuration is pluggable rather than fixed to one provider ([LLMs](https://docs.crewai.com/en/concepts/llms)). | **Strong.** Crews, flows, tools, custom LLMs, persistence implementations, and event listeners provide multiple extension seams ([Flows](https://docs.crewai.com/en/concepts/flows), [event listeners](https://docs.crewai.com/en/concepts/event-listener)). |

## Where EvolveOS would duplicate existing work

| If EvolveOS builds… | Existing first-class capability | Why that is duplication | v0.2 boundary |
| --- | --- | --- | --- |
| A graph/agent scheduler, retry loop, or multi-agent conversation runtime | LangGraph graphs; OpenAI agent runs/handoffs; AutoGen Core/AgentChat; CrewAI crews/flows | These projects already own execution semantics, ecosystem integrations, and edge cases. A new Java loop would begin far behind and would not validate contract evolution. | **Do not build.** Integrate one runtime through a narrow adapter and treat run/execution IDs as foreign references. |
| A generic checkpoint or chat-memory database | LangGraph checkpointers/stores; OpenAI sessions; AutoGen state snapshots; CrewAI flow persistence | Storage is necessary infrastructure but not the product thesis. Owning another generic state store creates migration, concurrency, and recovery work without differentiating EvolveOS. | **Do not build as a platform.** Persist only EvolveOS-owned contract, decision, migration, and evidence records; let runtime adapters retain execution state. |
| A generic pause/resume or human-input widget | LangGraph interrupts; OpenAI tool approvals; AutoGen user/intervention hooks; CrewAI human feedback | Every reviewed runtime can stop for a person. The missing layer is the durable policy meaning of that decision, not the pause itself. | **Reuse the runtime pause.** Add stable proposal/contract IDs, required role/policy, decision identity, reason, timestamp, and evidence hash around it. |
| A generic tracing backend or dashboard | LangGraph checkpoint history/time travel; OpenAI tracing; AutoGen OpenTelemetry; CrewAI events/tracing | Execution observability is mature and framework-specific. Reimplementing spans would fragment rather than improve forensic evidence. | **Emit and ingest standard trace references.** Keep an append-only governance event stream that links to—not replaces—runtime traces. |
| A model, tool, MCP, or plugin ecosystem | All four frameworks expose model/tool/runtime extension points | Connector breadth is expensive and unrelated to compatibility policy. | **Do not compete on connectors.** Define a small adapter contract for normalized manifests, proposals, decisions, executions, and evidence. |
| A second workflow versioning mechanism that merely tags code or JSON | LangGraph graph migration guidance and versioned/snapshotted state in other runtimes | Version labels alone do not prove consumer compatibility or safety. | **Build only semantic version handling:** diff the contract dimensions and require evidence-backed migration gates. |

The implication is architectural: EvolveOS should sit **beside or above** a runtime, not underneath it.

```text
agent runtime                         EvolveOS control plane
-------------                         ----------------------
execute / checkpoint / resume   --->  register contract version
interrupt for human input       --->  evaluate approval policy
emit run + trace identifiers    --->  append decision/evidence event
run old and new variants        --->  compare declared + observed compatibility
route traffic                   <---  authorize canary/default/retirement transition
```

## What is genuinely differentiated

### 1. The compatibility unit is semantic, not merely executable state

The closest adjacent concept in the reviewed set is LangGraph's graph migration support, which concerns changing graph topology/state while threads and checkpoints exist ([Graph API](https://docs.langchain.com/oss/python/langgraph/graph-api#graph-migrations)). AutoGen versions serialized state ([managing state](https://microsoft.github.io/autogen/stable/user-guide/agentchat-user-guide/tutorial/state.html)); the OpenAI Agents SDK and CrewAI validate current structured values. These mechanisms protect runtime continuity or current-shape correctness.

EvolveOS instead proposes compatibility rules over a consumer-facing capability:

```text
contract identity
+ required inputs
+ promised outputs
+ requested permissions
+ approval policy
```

Treating **a new permission as an escalation** and **removing approval as a breaking change** is the strongest part of the thesis. It makes safety policy part of the public contract rather than a callback hidden inside one runtime.

### 2. Evolution is a governed rollout, not a deployment event

None of the reviewed official surfaces combines semantic contract diffing with an explicit `EXPAND → DUAL_RUN → BACKFILL → VERIFY → CANARY → CONTRACT → RETIRED` lifecycle. LangGraph can preserve and revisit execution state; the OpenAI Agents SDK can resume approved tools; AutoGen can save and restore components; CrewAI can persist flows. Those capabilities could *host* a migration, but they do not define its evidence or retirement gates.

The differentiated product should therefore be a **migration control plane** that answers:

- Which producers and consumers still use version N?
- Which declared changes are backward compatible, permission-expanding, or approval-weakening?
- What old/new executions were compared, using which fixtures or production samples?
- What acceptance thresholds and human decisions allowed each stage to advance?
- Is rollback still possible, and what evidence permits retirement of N?

### 3. Governance events can connect policy to runtime evidence

Existing tracing systems are valuable but answer “what executed?” EvolveOS can add the narrower governance chain “which contract and policy allowed it, who decided, what evidence was reviewed, and why did the migration advance?” The distinction only holds if v0.2 persists immutable decision facts and links them to native run/trace IDs; a second generic trace viewer would be duplication.

### 4. Runtime neutrality is potentially valuable—but only if demonstrated

Each reviewed framework has its own state and interruption model. A normalized contract manifest and migration record that can govern more than one runtime would avoid encoding safety policy in graph nodes, Python decorators, or framework-specific callbacks. However, “runtime-neutral” is not established by a framework-free Java core. It requires at least one real adapter in v0.2 and a second adapter or conformance fixture soon after.

## Recommended v0.2 product boundary

### Build

1. **A canonical, serializable contract manifest** with stable identity, explicit version, inputs, outputs, permissions/capabilities, approval policy, and compatibility metadata.
2. **A deterministic compatibility engine** that emits machine-readable reasons and distinguishes additive, consumer-breaking, permission-expanding, and approval-weakening changes.
3. **A durable contract and migration registry** whose records survive restart and use optimistic concurrency or equivalent guards.
4. **An executable migration state machine** with explicit gate preconditions, rollback rules, and terminal retirement—not only a generated list of stages.
5. **Evidence records** linking old/new contract versions to fixtures, dual-run outputs, runtime execution IDs, native trace IDs, reviewer decisions, and hashes of compared artifacts.
6. **One thin runtime adapter** that maps the runtime's pause/resume and run identifiers into EvolveOS proposals and evidence. LangGraph is the strongest first validation target because it already supplies durable checkpoints, interrupts, and historical replay; EvolveOS can test whether it adds policy rather than reimplementing execution.
7. **A conformance suite** proving that an adapter cannot bypass approval, advance a gate without evidence, silently add permission, or retire a version with active consumers.

### Do not build in v0.2

- an LLM agent loop, graph engine, multi-agent protocol, or retry scheduler;
- a general-purpose memory/vector store;
- a provider or tool marketplace;
- a proprietary tracing stack;
- a broad visual workflow builder; or
- a migration planner that cannot actually persist and enforce stage transitions.

## Falsifiable differentiation tests

EvolveOS has demonstrated a distinct control-plane value only if v0.2 can pass tests such as:

1. **Permission escalation:** a contract that adds `tasks:write` is rejected as compatible even when its JSON/output schema is otherwise additive.
2. **Approval weakening:** changing `approvalRequired: true` to `false` cannot reach default or retired state without an explicitly authorized breaking migration.
3. **Dual-run evidence:** old and new versions run against the same evidence set in an external runtime, and the migration record retains both native run/trace IDs plus a deterministic comparison result.
4. **Restart safety:** a process restart cannot lose an approval, duplicate a stage transition, or reset audit ordering.
5. **Consumer retirement gate:** version N cannot retire while a registered consumer or in-flight runtime execution still declares N.
6. **Runtime substitution:** the same contract diff and gate policy can govern a second adapter without changing core compatibility rules.

If EvolveOS cannot pass these tests, it is currently a small reimplementation of facilities already available in agent frameworks. If it can, its defensible position is not “safer agents” in general, but **safe evolution of agent contracts across runtimes**.

## Source table

| # | Primary source | What it supports |
| ---: | --- | --- |
| 1 | [EvolveOS README](../../../README.md) | Project thesis, current invariants, implemented scope, and deliberate non-goals. |
| 2 | [EvolveOS architecture](../../architecture.md) | Contract compatibility, proposal and migration lifecycles, audit semantics, and deferred production work. |
| 3 | [LangGraph: Persistence](https://docs.langchain.com/oss/python/langgraph/persistence) | Checkpointers, stores, thread state, continuation, fault tolerance, and human-in-the-loop persistence. |
| 4 | [LangGraph: Interrupts](https://docs.langchain.com/oss/python/langgraph/interrupts) | Checkpointed pause/resume and human decision patterns. |
| 5 | [LangGraph: Time travel](https://docs.langchain.com/oss/python/langgraph/use-time-travel) | State history, replay, and forked execution. |
| 6 | [LangGraph: Graph API](https://docs.langchain.com/oss/python/langgraph/graph-api) | Graph composition, state/runtime extension points, and graph migration guidance. |
| 7 | [LangGraph repository](https://github.com/langchain-ai/langgraph) | First-party open-source implementation and local library surface. |
| 8 | [OpenAI Agents SDK: Sessions](https://openai.github.io/openai-agents-python/sessions/) | Session lifecycle and built-in local/external persistence implementations. |
| 9 | [OpenAI Agents SDK: Running agents](https://openai.github.io/openai-agents-python/running_agents/) | Run lifecycle, state, and resumption. |
| 10 | [OpenAI Agents SDK: Human-in-the-loop](https://openai.github.io/openai-agents-python/human_in_the_loop/) | Tool approval interruptions and approve/reject resumption. |
| 11 | [OpenAI Agents SDK: Tracing](https://openai.github.io/openai-agents-python/tracing/) | Trace/span coverage and processor extensibility. |
| 12 | [OpenAI Agents SDK: Models](https://openai.github.io/openai-agents-python/models/) | Model abstraction and alternative-provider integration. |
| 13 | [OpenAI Agents SDK repository](https://github.com/openai/openai-agents-python) | First-party MIT-licensed implementation and extension surface. |
| 14 | [AutoGen AgentChat: Managing state](https://microsoft.github.io/autogen/stable/user-guide/agentchat-user-guide/tutorial/state.html) | Agent/team/termination state save and load, including serialized state versions. |
| 15 | [AutoGen AgentChat: Human-in-the-loop](https://microsoft.github.io/autogen/stable/user-guide/agentchat-user-guide/tutorial/human-in-the-loop.html) | Human input, stop, and continuation patterns. |
| 16 | [AutoGen Core: Intervention handlers](https://microsoft.github.io/autogen/stable/user-guide/core-user-guide/framework/intervention.html) | Runtime interception/policy extension points. |
| 17 | [AutoGen Core: Telemetry](https://microsoft.github.io/autogen/stable/user-guide/core-user-guide/framework/telemetry.html) | Logging and OpenTelemetry observability. |
| 18 | [AutoGen repository](https://github.com/microsoft/autogen) | First-party open-source packages and layered extension architecture. |
| 19 | [CrewAI: Flows](https://docs.crewai.com/en/concepts/flows) | Event-driven workflows, state, routing, and flow persistence. |
| 20 | [CrewAI: Human-in-the-loop](https://docs.crewai.com/en/learn/human-in-the-loop) | Human input/feedback during execution. |
| 21 | [CrewAI: Event listeners](https://docs.crewai.com/en/concepts/event-listener) | Lifecycle event interception and custom observability sinks. |
| 22 | [CrewAI: Tracing](https://docs.crewai.com/en/observability/tracing) | Execution tracing and observability scope. |
| 23 | [CrewAI: LLMs](https://docs.crewai.com/en/concepts/llms) | Pluggable model/provider configuration. |
| 24 | [CrewAI repository](https://github.com/crewAIInc/crewAI) | First-party open-source implementation and local framework surface. |
