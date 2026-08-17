# EvolveOS v0.2 pre-research hypotheses

Status: hypothesis, not a decision
Branch: `research/v0.2-discovery`

## Starting thesis

EvolveOS should not become another general agent orchestration framework. Its possible niche is the safe evolution layer around agent workflows: contract impact, approvals, execution evidence and migration gates.

## Hypotheses to test

### H1 — Differentiate on evolution, not orchestration

Existing runtimes probably already cover graphs, tasks, retries, state and model/tool calls. EvolveOS should integrate with those capabilities rather than rebuilding a scheduler.

Evidence that would reject H1:

- no existing runtime provides a usable durable core for local/open-source use;
- integration would force EvolveOS to inherit incompatible contract or approval semantics;
- the safe-evolution use case cannot be expressed without owning execution.

### H2 — V0.2 should turn approval into one observable effect

The likely smallest useful slice is:

```text
DRAFT → APPROVED → ActionExecutor → ExecutionReceipt
```

With:

- one idempotency key per approved proposal;
- one recording executor with a real observable in-memory effect;
- success/denied/failed execution results;
- an audit receipt containing proposal, contract version, permissions and effect identity;
- tests proving draft/rejected proposals never call the executor and retries do not duplicate the effect.

Evidence that would reject H2:

- primary sources show that a more fundamental state/checkpoint abstraction must come first;
- existing frameworks already expose this exact seam and EvolveOS would only wrap it;
- contract-comparison evidence is a higher-risk missing prerequisite.

### H3 — Durable pause/resume is a later adapter boundary

V0.2 should model enough state for replay and receipts, but it should not implement its own distributed durable workflow engine.

Evidence that would reject H3:

- approval cannot remain trustworthy without durable checkpoint semantics in the same slice;
- a local single-process checkpoint implementation is necessary to test recovery correctly;
- adapters cannot preserve the required EvolveOS audit invariants.

### H4 — Contract migration needs runtime evidence, not only a static diff

A useful migration gate likely needs comparison evidence from old and new workflow versions:

- output shape compatibility;
- permission changes;
- approval-policy changes;
- execution result differences;
- error and retry behavior;
- enough trace identity to reproduce a mismatch.

Evidence that would reject H4:

- existing schema tooling fully covers the required compatibility semantics;
- output comparison cannot be made meaningful without domain-specific evaluators;
- v0.2 would become too broad if execution and migration evidence are combined.

## Decision gates after research

### Gate 1 — Duplication

Which proposed v0.2 capabilities already exist in current open-source runtimes?

### Gate 2 — Differentiation

Can EvolveOS state a narrower job than "run agents reliably"?

### Gate 3 — Testable effect

Can the selected slice demonstrate one real effect and one prevented duplicate without external infrastructure?

### Gate 4 — Portability

Can the core remain Java 21, local, deterministic and free of API keys?

### Gate 5 — Integration path

Is there a credible future adapter boundary for MCP and at least one durable runtime?

## Current default if evidence is inconclusive

Build the smallest `ActionExecutor + ExecutionReceipt + idempotency` slice, keep durable orchestration external, and treat runtime comparison evidence as v0.3.
