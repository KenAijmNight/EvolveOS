# Observability, Evaluation, and Safety Evidence for EvolveOS v0.2

## Scope and evidence boundary

This note defines the minimum evidence EvolveOS should retain for approval, execution, evaluation, migration, privacy, and audit decisions. **Section 1 reports facts from the collected primary sources. Sections 2–8 are EvolveOS design recommendations derived from those facts; they are not presented as requirements imposed by the sources.** The sources do not define an EvolveOS-specific schema, universal pass threshold, or universal retention period.

## 1. Verified source facts

1. **Agent traces can cover the full run, not just model calls.** The [OpenAI Agents SDK tracing documentation](https://openai.github.io/openai-agents-python/tracing/) says its built-in tracing collects LLM generations, tool calls, handoffs, guardrails, and custom events, and supports debugging, visualization, and monitoring in development and production.
2. **Generative behavior requires evaluation in addition to conventional tests.** OpenAI's [evaluation best-practices guide](https://platform.openai.com/docs/guides/evaluation-best-practices) explains that generative output can vary for the same input, making traditional software testing alone insufficient for AI architectures; evals supplement those tests.
3. **A vendor-neutral telemetry vocabulary exists.** The [OpenTelemetry GenAI semantic-conventions repository](https://github.com/open-telemetry/semantic-conventions-genai) covers spans, metrics, and events for GenAI clients, MCP, and provider-specific conventions. It is the appropriate interoperability reference for EvolveOS telemetry names and attributes.
4. **Security logging should be selected by risk, not by checklist.** The [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html) says monitoring, alerting, and reporting requirements should be set during requirements and design, proportionate to information-security risk, and warns that a blind checklist can create unnecessary alarm noise.
5. **Agent and protocol security need an explicit threat model.** OWASP publishes an official [Agentic AI Threats and Mitigations guide](https://genai.owasp.org/resource/agentic-ai-threats-and-mitigations/) for autonomous/agentic systems. The current [MCP security best-practices documentation](https://modelcontextprotocol.io/docs/2026-07-28/tutorials/security/security_best_practices) organizes attacks and mitigations including confused-deputy behavior, token passthrough, and server-side request forgery (SSRF).

Taken together, these sources establish the need for broad run tracing, versioned evaluation evidence, interoperable telemetry, risk-proportionate event selection, and security-aware permission boundaries. They do **not** prescribe the concrete EvolveOS record model below.

## 2. EvolveOS evidence principles (recommendation)

EvolveOS should use a small, linked evidence graph rather than treating an undifferentiated log stream as an audit record.

1. **Record decisions and outcomes separately.** An approval proves that an action was authorized under stated constraints; an execution receipt proves what was attempted and what happened. Neither substitutes for the other.
2. **Link evidence explicitly.** Use durable identifiers and parent references; never reconstruct causality from timestamps alone.
3. **Capture structure by default, content by exception.** IDs, types, versions, statuses, fingerprints, and redacted summaries should be standard. Raw prompts, tool arguments, model outputs, and credentials should not be copied into general telemetry merely because tracing permits it.
4. **Version every basis for a decision.** Agent/workflow definitions, tool contracts, policies, prompts where applicable, model configuration, eval suites, graders, and migration criteria need stable versions or content digests.
5. **Make missing evidence visible.** Required evidence that is absent, malformed, redacted beyond evaluability, or unlinked should produce an explicit `incomplete` result—not an implicit pass.
6. **Scale collection to risk.** Side-effecting, privileged, externally visible, or sensitive actions warrant more evidence than read-only, low-risk operations, consistent with OWASP's risk-proportionate logging guidance.

## 3. Minimum evidence record (recommendation)

### 3.1 Common envelope

Every approval, receipt, evaluation, comparison, and gate decision should carry this envelope:

| Field | Minimum purpose |
| --- | --- |
| `evidence_id` | Globally unique, immutable identifier. |
| `evidence_type` / `schema_version` | Typed interpretation and forward-compatible evolution. |
| `occurred_at` / `recorded_at` | Event time and ingestion time, in UTC. |
| `trace_id` / `run_id` / `span_id` | Run correlation; `span_id` may be absent for run-level records. Use OpenTelemetry-compatible identifiers where practical. |
| `parent_evidence_ids` | Explicit causal/audit links to upstream records. |
| `subject` | Stable workflow, agent, comparison, release, or migration identifier. |
| `environment` | Development, test, staging, or production boundary. |
| `producer` | Component and version that emitted the evidence. |
| `config_refs` | Versions or digests of relevant policy, workflow, model, tool contract, and evaluator configuration. |
| `data_classification` | Sensitivity class that drives access and retention. |
| `redaction` | Redaction status, rule-set version, and affected field names/counts—never the removed secret. |
| `integrity_digest` | Digest of the canonical record or signed envelope for later substitution detection. |

Records should be append-only. Corrections should create a new record that references the superseded record rather than silently mutating history.

### 3.2 Approval decision

An `approval_decision` should minimally record:

- `approval_request_id` and the requesting human/service/agent identity;
- agent and workflow version;
- intended tool, action, target/resource, and side-effect class;
- a safe, redacted action summary plus an input fingerprint;
- the risk tier, applicable policy/rule version, and why approval was required;
- `approved`, `denied`, `expired`, or `cancelled`;
- approver identity and the authority/role under which the decision was made;
- constraints such as allowed resource, parameter bounds, use count, and expiry;
- decision time and a concise rationale.

Approval scope must be specific enough to test against the effective execution. A broad statement such as “allow this agent” is not sufficient evidence for a particular privileged side effect.

### 3.3 Execution receipt

Create one `execution_receipt` for **every attempt**, including failures and retries. It should minimally record:

- `execution_id`, attempt number, and the exact `approval_decision` reference; if approval was not required, a versioned exemption-policy reference;
- effective tool/provider/endpoint identity and version;
- effective action and target after middleware/policy transformation;
- input fingerprint and redacted summary matching the approval request;
- start/end time, status, and normalized error category when applicable;
- result fingerprint and a restricted artifact reference rather than unrestricted raw output;
- declared and observed side effects, including stable IDs of affected resources where available;
- retry or compensation linkage;
- the trace/span that contains the model/tool/handoff/guardrail context.

A receipt must distinguish `not_attempted`, `started`, `succeeded`, `failed`, `partially_succeeded`, `compensated`, and `unknown`. “The tool call returned” is not enough to prove that an external side effect completed.

### 3.4 Evaluation observation

Each `evaluation_observation` should record:

- eval suite, case, dataset, and fixture versions;
- input/reference fingerprint and restricted artifact references;
- run ID and complete candidate configuration references;
- deterministic assertion results **and** generative evaluator results;
- evaluator/grader identity, version, rubric, score or label, and concise rationale;
- safety/policy outcomes separately from task-quality outcomes;
- trial index or sample count when a case is repeated;
- `pass`, `fail`, `inconclusive`, or `incomplete`.

This keeps evals additive to ordinary unit/integration tests, as motivated by [OpenAI's evaluation guidance](https://platform.openai.com/docs/guides/evaluation-best-practices), rather than using a model grader to replace deterministic checks.

## 4. Dual-run comparison (recommendation)

A `dual_run_comparison` should compare a baseline and candidate through explicit evidence references, not screenshots or dashboard impressions.

Minimum record:

- comparison ID, purpose, owner, and predeclared comparison criteria;
- eval suite/dataset/fixture versions and case IDs;
- baseline and candidate run IDs plus all material configuration versions/digests;
- a comparability statement listing controlled inputs and any known differences;
- output-quality, safety, latency/cost (when measured), approval, and execution-receipt results as separate dimensions;
- semantic outcome differences and side-effect differences, not only text diffs;
- aggregate statistics together with case-level evidence references and trial/sample counts;
- anomalies, missing evidence, and human adjudication with rationale;
- final disposition: `candidate_better`, `equivalent_within_gate`, `candidate_worse`, or `inconclusive`.

Because generative outputs vary, EvolveOS should not require byte-identical output or infer a migration result from one replay. It should use representative, versioned cases and enough trials for the locally defined risk level. Where tools can cause side effects, the candidate should run against mocks, a sandbox, or an idempotent shadow path unless duplicate effects are explicitly authorized and contained.

## 5. Migration gates (recommendation)

A migration should pass only from a versioned `migration_gate_decision` that references a frozen evidence set. The decision should contain the baseline/candidate release IDs, gate-definition version, criteria and thresholds fixed before scoring, observed results, waivers, decision owner, decision time, and rollback trigger.

| Gate | Minimum passing evidence |
| --- | --- |
| Observability completeness | Expected LLM, tool, handoff, guardrail, and custom-event classes for the tested workflow are present where applicable; required records are schema-valid and linked. |
| Approval integrity | Every tested side-effecting execution links to an in-scope approval or explicit versioned exemption; no receipt exceeds approval constraints. |
| Receipt integrity | Every attempt has a terminal or explicit `unknown` receipt; observed side effects and errors are represented. |
| Functional quality | Conventional deterministic tests pass and versioned eval outcomes meet product-specific thresholds. |
| Dual-run non-regression | Baseline/candidate evidence meets the predeclared comparison rule; missing or non-comparable evidence cannot count as a pass. |
| Safety/security | Relevant cases derived from the [OWASP agentic guide](https://genai.owasp.org/resource/agentic-ai-threats-and-mitigations/) and [MCP security guidance](https://modelcontextprotocol.io/docs/2026-07-28/tutorials/security/security_best_practices) meet locally defined thresholds, including permission-boundary and tool-misuse cases where applicable. |
| Privacy/redaction | Prohibited-secret tests pass; content capture, access, and retention conform to the declared data class and redaction policy. |
| Operational reversibility | The deployed artifact/configuration is identifiable and rollback or compensation has a tested, owned trigger. |

The official sources do not provide universal numeric thresholds. EvolveOS owners should set them by use case and risk. A waiver should be explicit, scoped, time-bounded, owned, and linked to the failing evidence; a missing record should never become an undocumented waiver.

## 6. Privacy and redaction (recommendation)

EvolveOS should treat prompts, outputs, tool arguments, retrieved context, and external-system responses as potentially sensitive payloads.

- **Allowlist telemetry fields.** Collect the common envelope and typed summaries by default. Enable raw-content capture only for a documented purpose, environment, data class, and retention period.
- **Redact before export and durable storage.** Do not persist plaintext credentials, bearer/access tokens, session cookies, authorization headers, private keys, or connection secrets in traces, receipts, eval artifacts, or approval summaries.
- **Separate telemetry from payload storage.** General telemetry should hold opaque artifact references and fingerprints. Sensitive payloads, when genuinely required, should live in a restricted store with independent access logging and deletion controls.
- **Preserve redaction evidence.** Record the redaction rule-set version, affected field names/counts, and success/failure status so an auditor can tell “content absent by policy” from “instrumentation failed.”
- **Use safe correlation.** Prefer opaque IDs or keyed digests when equality correlation is needed; do not assume a plain hash anonymizes low-entropy personal or secret data.
- **Make retention risk-based.** Define retention and access by purpose, environment, and classification rather than retaining every event indefinitely. When payload deletion is required, retain only a non-sensitive tombstone and linkage metadata if the audit purpose permits it.
- **Fail closed on redaction failure for sensitive exports.** The business operation may follow its own risk policy, but unredacted telemetry must not be exported merely to preserve observability.

These are EvolveOS controls chosen to reconcile useful evidence with the [OWASP recommendation to select logging proportionately to risk](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html).

## 7. Audit linkage and reconstruction (recommendation)

The minimum auditable chain is:

```text
approval request
  -> approval decision
  -> execution receipt(s)
  -> trace/span events
  -> evaluation observation(s)
  -> dual-run comparison
  -> migration gate decision
  -> released artifact/configuration
```

Every arrow should be an explicit ID reference in one or both records. Trace IDs provide operational correlation; evidence IDs provide durable audit identity. Provider-specific telemetry may enrich the chain, but the stable core should align with the [OpenTelemetry GenAI conventions](https://github.com/open-telemetry/semantic-conventions-genai) so a provider change does not destroy comparability.

The evidence store should support these reconstruction queries without reading raw prompt content:

1. Which approval authorized this external side effect, under which policy and constraints?
2. What was actually attempted, what changed, and was compensation required?
3. Which model, workflow, tool, policy, and evaluator versions produced this result?
4. Which baseline and candidate runs support the migration decision?
5. Which evidence was missing, redacted, waived, or deleted, and who accepted that condition?
6. Which release incorporated the approved candidate, and what rollback trigger applies?

A gate record should reference an immutable manifest of its evidence IDs and integrity digests. This prevents later records from being silently substituted while allowing sensitive payloads to remain in separately governed storage.

## 8. Minimum implementation sequence (recommendation)

1. Define versioned schemas for the common envelope and five record types: `approval_decision`, `execution_receipt`, `evaluation_observation`, `dual_run_comparison`, and `migration_gate_decision`.
2. Create a trace/run at workflow ingress and instrument applicable LLM generations, tool calls, handoffs, guardrails, and custom events, following the event coverage demonstrated by [OpenAI Agents SDK tracing](https://openai.github.io/openai-agents-python/tracing/).
3. Emit an approval decision before a gated side effect and validate its constraints immediately before execution.
4. Emit a receipt after every execution attempt and link it to both the approval and the tool span.
5. Run conventional tests and versioned evals; retain case-level observations before aggregating them.
6. Produce dual-run comparisons from evidence IDs, with side effects suppressed or contained.
7. Evaluate migration gates from a frozen evidence manifest; reject `incomplete` required evidence unless an explicit waiver policy allows a scoped waiver.
8. Continuously test redaction and query for orphan receipts, unreceipted tool attempts, out-of-scope approvals, and gate decisions with broken evidence links.

## Source table

| Primary source | Owner | Verified fact used here | Use in this note |
| --- | --- | --- | --- |
| [Agents SDK: Tracing](https://openai.github.io/openai-agents-python/tracing/) | OpenAI | Built-in traces collect LLM generations, tool calls, handoffs, guardrails, and custom events; traces support development and production inspection. | Defines the event classes whose evidence should be linkable across a run. |
| [Evaluation best practices](https://platform.openai.com/docs/guides/evaluation-best-practices) | OpenAI | Generative outputs vary; traditional software tests alone are insufficient for AI architectures; evals supplement them. | Supports versioned, case-level eval evidence, repeated observations where risk warrants, and retaining deterministic tests. |
| [OpenTelemetry GenAI semantic conventions](https://github.com/open-telemetry/semantic-conventions-genai) | OpenTelemetry | Defines GenAI spans, metrics, and events for clients, MCP, and provider-specific conventions. | Supplies the interoperability vocabulary for trace/run evidence. |
| [Agentic AI Threats and Mitigations](https://genai.owasp.org/resource/agentic-ai-threats-and-mitigations/) | OWASP GenAI Security Project | Official threat-and-mitigation reference for agentic/autonomous AI systems. | Provides a primary-source threat-model input for safety cases and gates. |
| [Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html) | OWASP Cheat Sheet Series | Logging requirements should be set during design, proportionate to security risk; blind checklist logging can create alarm noise. | Supports risk-tiered evidence collection, redaction, and retention rather than indiscriminate payload logging. |
| [Security Best Practices](https://modelcontextprotocol.io/docs/2026-07-28/tutorials/security/security_best_practices) | Model Context Protocol | Official attack-and-mitigation guidance includes confused-deputy, token-passthrough, and SSRF concerns. | Informs approval scope, credential boundaries, tool-misuse cases, and MCP-related migration gates. |
