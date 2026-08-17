# Contracts and MCP evolution for EvolveOS v0.2

## Scope and executive conclusion

This note uses only owner-published primary sources: the fixed Model Context Protocol (MCP) `2026-07-28` specification and project policy, JSON Schema 2020-12, Semantic Versioning 2.0.0, and the EvolveOS repository itself. The MCP revision matters: `2026-07-28` replaced the legacy initialization handshake with stateless, per-request protocol metadata and added mandatory server discovery; an adapter designed around older `initialize` semantics would encode the wrong lifecycle for this revision ([MCP key changes](https://modelcontextprotocol.io/specification/2026-07-28/changelog), [MCP versioning and compatibility](https://modelcontextprotocol.io/specification/2026-07-28/basic/versioning)).

The central conclusion is that EvolveOS v0.2 should stop treating a contract as only sets of required input names, output names, permissions, and one approval boolean. That v0.1 model intentionally classifies adding an output as compatible and adding a required input or permission as breaking ([EvolveOS architecture](../../architecture.md#skill-contracts)). The rule is a useful conservative start, but it cannot represent JSON types, enum/range constraints, open versus closed objects, protocol capabilities, schema dialects, authorization scopes, or the exact action a human approved.

For v0.2, compatibility should be evaluated across **five independent dimensions**:

1. MCP wire-protocol revision and negotiated extensions;
2. structural input compatibility;
3. structural output compatibility;
4. authorization and approval-policy compatibility;
5. semantic behavior and side-effect compatibility.

A change is safe only when every applicable dimension passes. A JSON-Schema-compatible change can still be unsafe because it adds a permission, changes a side effect, weakens approval, or relies on a capability the peer did not declare.

## 1. Keep version axes separate

MCP protocol versions are date strings carried on every modern request. If a server does not implement the requested version, it must return `UnsupportedProtocolVersionError` with its supported versions; the client should retry using a mutually supported version or surface an error ([MCP protocol version negotiation](https://modelcontextprotocol.io/specification/2026-07-28/basic/versioning#protocol-version-negotiation)). This is not Semantic Versioning.

Semantic Versioning applies only after a project declares a precise public API. It assigns incompatible API changes to MAJOR, backward-compatible functionality to MINOR, and backward-compatible bug fixes to PATCH; marking public functionality deprecated also requires a MINOR release. SemVer also explicitly warns that `0.y.z` is initial development and its public API should not be considered stable ([Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html)).

EvolveOS should therefore persist separate values rather than overloading one integer:

| Axis | Example | Purpose | v0.2 rule |
| --- | --- | --- | --- |
| MCP protocol revision | `2026-07-28` | Select wire semantics | Negotiate exactly as MCP specifies; never compare as SemVer. |
| EvolveOS release | `0.2.0` | Version the application/library public API | Follow SemVer, while documenting that `0.x` remains unstable. |
| Managed contract identity and revision | `morning-review@2` or `morning-review@2.0.0` | Identify an immutable tool/workflow contract | Never mutate a published revision; classify the next revision from compatibility evidence. |
| JSON Schema dialect | `https://json-schema.org/draft/2020-12/schema` | Select keyword semantics | Persist it explicitly with each input and output schema. |
| Contract fingerprint | e.g. SHA-256 of a canonical snapshot | Bind execution and approval to exact content | Recompute on every published snapshot; do not infer it from a version label. |

If per-contract SemVer is adopted, EvolveOS must declare what belongs to that contract's public API: schemas, side effects, errors, permissions, approval policy, and observable semantics. Otherwise an immutable monotonic revision plus an independently computed compatibility classification is less ambiguous.

## 2. Version and capability negotiation in current MCP

### What the protocol requires

Modern MCP is stateless. Every request supplies `io.modelcontextprotocol/protocolVersion` and `io.modelcontextprotocol/clientCapabilities` in `_meta`; servers must not infer those values from a connection or prior request. A server must reject missing required metadata and must return `MissingRequiredClientCapabilityError` when processing needs a capability the client did not declare ([MCP statelessness](https://modelcontextprotocol.io/specification/2026-07-28/basic#statelessness), [MCP per-request protocol fields](https://modelcontextprotocol.io/specification/2026-07-28/basic#meta)).

Every server must implement `server/discover`. Its result advertises supported protocol versions, server capabilities, optional instructions, and self-reported server identity. Calling it first is optional for a client, because the client may instead attempt an RPC and recover from a version error ([MCP server discovery](https://modelcontextprotocol.io/specification/2026-07-28/server/discover)). The self-reported `serverInfo` is for display, logging, and debugging and must not drive security decisions ([MCP `DiscoverResult`](https://modelcontextprotocol.io/specification/2026-07-28/server/discover#data-types)).

Optional extensions are advertised in the `extensions` capability map and require support by both peers. If only one side supports an extension, it must fall back to core behavior or reject the request; each extension should document its fallback ([MCP extension negotiation](https://modelcontextprotocol.io/specification/2026-07-28/basic/versioning#extension-negotiation)).

### Implications for EvolveOS v0.2

1. **Make the adapter protocol-profiled.** Target `2026-07-28` explicitly. Put legacy `initialize` support, if required at all, behind a separate dual-era profile; do not blend legacy state into the modern domain model. MCP defines transport-specific probing and fallback for dual-era implementations ([MCP backward compatibility](https://modelcontextprotocol.io/specification/2026-07-28/basic/versioning#backward-compatibility-with-initialization-based-versions)).
2. **Discover, then bind.** Store a capability snapshot with the protocol revision, server endpoint/configuration identity, authorization context, retrieval time/TTL, and extension settings. Do not treat an implementation version or `serverInfo.name` as proof of capability or trust.
3. **Send only truthful per-request capabilities.** Build `_meta` from the capabilities available for that invocation, not from a process-global optimistic union.
4. **Treat coarse capability and concrete contract discovery separately.** `capabilities.tools` says the feature exists; `tools/list` supplies the current tool descriptors. A tools list may change over time and may vary with the authorization on the request ([MCP tool capabilities](https://modelcontextprotocol.io/specification/2026-07-28/server/tools#capabilities)).
5. **Preserve unknown namespaced metadata and extensions.** Unknown optional data should not silently enable behavior. Core fallback or an explicit unsupported-extension error is safer than guessing.
6. **Key caches by security context.** Because available tools may differ by granted scopes, a cache keyed only by server URL could expose a tool descriptor obtained under a different principal or permission set ([MCP tool capabilities](https://modelcontextprotocol.io/specification/2026-07-28/server/tools#capabilities)).

## 3. JSON Schema is the contract language, not the compatibility policy

MCP tool `inputSchema` is required and `outputSchema` is optional. Both follow MCP's JSON Schema rules: a missing `$schema` means JSON Schema 2020-12, implementations must support 2020-12, and an explicit `$schema` may choose another supported dialect ([MCP JSON Schema usage](https://modelcontextprotocol.io/specification/2026-07-28/basic#json-schema-usage), [MCP Tool data type](https://modelcontextprotocol.io/specification/2026-07-28/server/tools#tool)). JSON Schema itself defines `$schema` as the schema dialect selector and `$id` as a schema resource identifier/base URI ([JSON Schema Core: `$schema`](https://json-schema.org/draft/2020-12/json-schema-core#name-the-schema-keyword), [JSON Schema Core: `$id`](https://json-schema.org/draft/2020-12/json-schema-core#name-the-id-keyword)).

JSON Schema defines whether an instance is valid; it does not label a change “backward compatible.” EvolveOS must supply that policy. Let `L(S)` mean the set of JSON instances valid against schema `S`:

- **Input backward compatibility for old callers:** `L(oldInput) ⊆ L(newInput)`. Every previously valid request must remain accepted by the new provider.
- **Output backward compatibility for old consumers:** `L(newOutput) ⊆ L(oldOutput)`. Every result the new provider may emit must remain acceptable to an old consumer.

The opposite subset direction is intentional. Providers consume inputs but produce outputs.

### Open and closed objects change the answer

The `properties` keyword validates a named property only when that property is present. Omitting `required` behaves like an empty required list ([JSON Schema `properties`](https://json-schema.org/draft/2020-12/json-schema-core#name-properties), [JSON Schema `required`](https://json-schema.org/draft/2020-12/json-schema-validation#name-required)). `additionalProperties` applies to properties not covered by `properties` or `patternProperties`, and omitting it has the same assertion behavior as an empty schema—unknown properties are therefore not rejected by default ([JSON Schema `additionalProperties`](https://json-schema.org/draft/2020-12/json-schema-core#name-additionalproperties)). JSON Schema 2020-12 also provides `unevaluatedProperties` for closing properties not evaluated through adjacent applicators ([JSON Schema `unevaluatedProperties`](https://json-schema.org/draft/2020-12/json-schema-core#name-unevaluatedproperties)).

Consequently, “adding an output field is compatible” is true only for tolerant old consumers. It is false when an old consumer uses a closed schema, rejects unknown fields in generated bindings, hashes exact objects, or otherwise depends on the previous shape. EvolveOS must record consumer posture or conservatively classify an additive output as **conditional** until compatibility is demonstrated.

The JSON Schema `default` keyword is metadata associated with a schema; validation does not require a validator to insert that value. Adding an optional input with `default` is not a migration by itself—EvolveOS must define and test which component materializes defaults ([JSON Schema `default`](https://json-schema.org/draft/2020-12/json-schema-validation#name-default)).

### Structural change matrix

| Schema change | New input schema versus old callers | New output behavior versus old consumers | v0.2 classification |
| --- | --- | --- | --- |
| Add an optional property | Usually widening/equal for old requests | Additive output may fail a closed or strict old consumer | Input-compatible; output-conditional. |
| Add a required input property | Old requests omit it and fail | N/A | Breaking. |
| Remove an input from `required` | Old requests remain valid | N/A | Backward-compatible for the new provider, but new callers may fail against the old provider during coexistence. |
| Tighten input `type`, `enum`, range, length, pattern, or object closure | Rejects some formerly valid requests | N/A | Breaking. JSON Schema `type` and `enum` are validation assertions ([`type`](https://json-schema.org/draft/2020-12/json-schema-validation#name-type), [`enum`](https://json-schema.org/draft/2020-12/json-schema-validation#name-enum)). |
| Widen accepted input values | Preserves old requests | N/A | Structurally compatible; still review semantics and security. |
| Remove a previously guaranteed output property or make it optional | N/A | New results may violate old consumers' `required` constraint | Breaking. |
| Add an output enum variant or widen output type | N/A | New results may fall outside the old accepted set | Breaking. |
| Narrow possible output values without removing required data | N/A | New results remain within the old structural set | Structurally compatible, but semantic review is required. |
| Change from open to closed input object | Previously accepted extra inputs may fail | N/A | Breaking. |
| Add or change `default` | Does not by itself reject old input | Does not guarantee materialization | Never use as sole compatibility evidence. |
| Set `deprecated: true` | Annotation only; it does not reject the instance | Annotation only | Lifecycle signal, not a validation or retirement gate ([JSON Schema `deprecated`](https://json-schema.org/draft/2020-12/json-schema-validation#name-deprecated)). |

Schema subset checks are necessary but not sufficient. General JSON Schema inclusion is difficult in the presence of references, regular expressions, conditionals, and composition. A v0.2 checker should therefore produce `COMPATIBLE`, `BREAKING`, or `UNKNOWN`, explain the decisive paths, and require evidence for `UNKNOWN`; it should never convert “not proven breaking” into “compatible.”

## 4. Tool input and output evolution

A current MCP `Tool` is identified by `name` and includes description/metadata plus `inputSchema` and optional `outputSchema`; the documented Tool fields do not provide a protocol-level per-tool contract revision. Tool names are scoped to a server, and aggregating clients must handle collisions ([MCP Tool data type and names](https://modelcontextprotocol.io/specification/2026-07-28/server/tools#tool)).

Structured tool results use `structuredContent`, which may be any JSON value. If an `outputSchema` is declared, the server must conform and the client should validate. For backward compatibility, a server returning structured content should also include serialized JSON in a text content block ([MCP structured content and output schema](https://modelcontextprotocol.io/specification/2026-07-28/server/tools#structured-content)). Servers must validate tool inputs, and clients should validate results before giving them to the model ([MCP tool security considerations](https://modelcontextprotocol.io/specification/2026-07-28/server/tools#security-considerations)).

### Implications for EvolveOS v0.2

- **Require both schemas for EvolveOS-managed tools.** MCP permits an omitted `outputSchema`, but EvolveOS cannot make a strong output-compatibility claim without one.
- **Persist immutable descriptor snapshots.** Store the exact tool name, title/description, schemas, dialect, permissions, approval policy, error contract, side-effect classification, and content hash observed or published at a revision.
- **Publish breaking revisions side by side.** Because MCP has no per-tool revision negotiation, expose a new unambiguous tool name or endpoint mapping for a breaking contract while retaining the old descriptor during EXPAND and DUAL_RUN. Do not change a tool's schema in place and hope a cached client notices.
- **Keep the old textual representation during transition.** When adopting `structuredContent`, retain the MCP-recommended serialized JSON text block until legacy consumers are verified migrated.
- **Re-list on invalidation.** If the server declares `listChanged`, consume the tools-list change notification and fetch a fresh descriptor before future planning/execution ([MCP list-changed notification](https://modelcontextprotocol.io/specification/2026-07-28/server/tools#list-changed-notification)).
- **Validate at both boundaries.** Validate arguments against the selected immutable input snapshot before approval and again immediately before transport; validate `structuredContent` against the selected output snapshot before accepting the run.
- **Separate protocol errors from tool execution errors.** MCP uses JSON-RPC errors for malformed/unknown-tool requests and `isError: true` results for actionable execution or business failures ([MCP tool error handling](https://modelcontextprotocol.io/specification/2026-07-28/server/tools#error-handling)). EvolveOS should version and test both error surfaces.

## 5. Capability, authorization, and human approval are different gates

MCP tools are model-controlled at the protocol level, but the specification's trust guidance calls for a human able to deny invocations, clear disclosure of exposed tools and invocation, and confirmation prompts. Tool annotations must be treated as untrusted unless they come from a trusted server ([MCP tool interaction model](https://modelcontextprotocol.io/specification/2026-07-28/server/tools#user-interaction-model), [MCP tool annotations warning](https://modelcontextprotocol.io/specification/2026-07-28/server/tools#tool)). The top-level MCP security principles also require explicit user consent and control for data access and tool operations ([MCP security and trust principles](https://modelcontextprotocol.io/specification/2026-07-28)).

For HTTP transports, MCP authorization is OAuth-based. Clients should request least privilege; a server can return an operation-specific scope challenge, and runtime insufficient scope is represented by `403 Forbidden` plus `WWW-Authenticate`. A user-facing client should perform bounded step-up authorization and retry, while preserving previously requested scopes ([MCP scope selection and step-up authorization](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization#scope-selection-strategy), [MCP runtime insufficient-scope handling](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization#scope-challenge-handling)). Tokens must be audience-bound to the MCP server, and token passthrough is forbidden ([MCP access-token handling](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization#token-handling)). The official security guidance recommends progressive, least-privilege scopes and identifies silent scope-semantic changes without versioning as a common mistake ([MCP scope minimization](https://modelcontextprotocol.io/docs/2026-07-28/tutorials/security/security_best_practices#scope-minimization)).

EvolveOS should model three independent questions:

1. **Capability:** can this peer perform `tools/call` under this protocol/extension profile?
2. **Authorization:** does this principal's audience-bound credential carry the server-enforced scopes for this exact operation?
3. **Approval:** did an authorized human approve this exact proposed effect under current policy?

Execution should require all applicable gates:

```text
protocol supported
AND tool discovered under this authorization context
AND arguments valid against the selected contract snapshot
AND required scopes granted
AND local policy allows the effect
AND approval is valid for this snapshot, target, scopes, and arguments
```

### Replace the approval boolean with an approval artifact

The v0.1 `approvalRequired` boolean correctly makes removal of an existing approval requirement breaking ([EvolveOS architecture](../../architecture.md#skill-contracts)), but it cannot prove what was approved. In v0.2 an approval record should bind at least:

- proposal and approver identity;
- server/transport target and authenticated principal;
- tool name plus contract revision and fingerprint;
- canonical arguments hash and human-readable effect summary;
- requested and granted permission/scope set;
- approval policy/risk class;
- issue time, expiry/reuse constraints, and revocation state.

Any change to target, tool fingerprint, arguments, effect classification, or permissions must invalidate the approval and return the proposal to review. Added permissions are not merely a schema change: they require step-up authorization where applicable and fresh human approval. A capability declaration or untrusted tool annotation must never satisfy either gate.

## 6. Deprecation and removal

MCP gives specification features the states Active, Deprecated, and Removed. Deprecation must document a migration path and a minimum window of at least twelve months before eligibility for removal; expedited removal for an active security risk still has a minimum ninety-day window. Eligibility is not automatic removal, which remains a maintainer decision ([MCP feature lifecycle policy](https://modelcontextprotocol.io/community/feature-lifecycle)). The current registry says new implementations should not adopt deprecated features and records the revision, migration, and earliest removal for each one ([MCP deprecated-feature registry](https://modelcontextprotocol.io/specification/2026-07-28/deprecated)).

SemVer independently requires a MINOR release when public API functionality is marked deprecated and a MAJOR release when it is incompatibly removed ([Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html)). JSON Schema's `deprecated: true` only tells applications to refrain from using the annotated location and that it may be removed; it does not enforce a deadline or invalidate data ([JSON Schema `deprecated`](https://json-schema.org/draft/2020-12/json-schema-validation#name-deprecated)).

### Implications for EvolveOS v0.2

Add an explicit contract lifecycle with `DRAFT`, `ACTIVE`, `DEPRECATED`, and `RETIRED`, plus:

- `deprecatedSinceRevision` and timestamp;
- rationale and replacement contract;
- migration instructions;
- earliest removal date/policy;
- observed consumer/caller inventory and last-use evidence;
- rollback owner and retirement decision record.

For public stable contracts, mirror MCP's minimum twelve-month window unless an explicitly documented security exception applies. For experimental/private contracts, the policy may be shorter, but it must still be explicit before consumers integrate. A timer alone must never retire a contract: RETIRED should require migration evidence, no unapproved permission/approval regression, bounded residual usage, and a deliberate decision.

MCP `2026-07-28` already marks Roots, Sampling, Logging, Dynamic Client Registration, older `includeContext` values, and HTTP+SSE as deprecated with migration paths ([MCP deprecated-feature registry](https://modelcontextprotocol.io/specification/2026-07-28/deprecated)). A new EvolveOS adapter should therefore avoid adding dependencies on those features merely because an older SDK still exposes them.

## 7. Fit with EvolveOS's expand-contract lifecycle

The existing `DRAFT → EXPAND → DUAL_RUN → BACKFILL → VERIFY → CANARY → CONTRACT → RETIRED` sequence remains a strong organizing model ([EvolveOS migration lifecycle](../../architecture.md#migration-lifecycle)). v0.2 should strengthen the evidence at each stage:

| Stage | Required v0.2 evidence |
| --- | --- |
| DRAFT | Immutable old/new snapshots; protocol/dialect identity; directional input/output diff; semantic, permission, approval, and deprecation diff; `COMPATIBLE`, `BREAKING`, or `UNKNOWN` per dimension. |
| EXPAND | Old and new tool mappings coexist; discovery returns deterministic descriptors; cache and list-change behavior tested; no in-place mutation of the old revision. |
| DUAL_RUN | The same eligible proposal is evaluated through both contracts without duplicating side effects; inputs and outputs validate; semantic invariants and error surfaces are compared. |
| BACKFILL | Only when persisted consumers need new fields; default materialization is explicit, deterministic, and audited rather than inferred from JSON Schema `default`. |
| VERIFY | Reader/caller inventory, schema-validation results, permission delta, approval binding, OAuth scope behavior, protocol-version matrix, and rollback test all pass. `UNKNOWN` compatibility remains blocking. |
| CANARY | Routing is bounded by principal/tool/revision and carries explicit state handles when cross-call state is needed; authorization is rechecked on every call. MCP has no implicit protocol session ([MCP statelessness](https://modelcontextprotocol.io/specification/2026-07-28/basic#statelessness)). |
| CONTRACT | New revision becomes the default mapping, while the old mapping and rollback remain available. |
| RETIRED | Deprecation policy satisfied, residual use below the declared threshold, no active approvals bound to the old fingerprint, and a recorded human retirement decision. |

## 8. Recommended v0.2 acceptance criteria

1. A managed contract persists full JSON input/output schemas, explicit dialect, immutable identity/revision, canonical fingerprint, permissions, approval policy, error contract, and side-effect summary.
2. The compatibility engine implements the directional input/output subset rules and reports exact JSON paths and reasons. Unsupported constructs return `UNKNOWN`, never an optimistic pass.
3. Every managed MCP tool has an `outputSchema`; every invocation validates input before approval and transport, and validates structured output before acceptance.
4. The MCP adapter supports `server/discover`, per-request version/capability metadata, mutual-version retry, truthful extension negotiation, and a tested explicit failure or separate fallback path for legacy servers.
5. Capability and tool caches are bound to protocol revision and authorization context and are invalidated by TTL/list-change signals.
6. Breaking tool revisions coexist under distinct mappings through EXPAND, DUAL_RUN, VERIFY, and CANARY; published descriptors are not mutated in place.
7. An approval is cryptographically/logically bound to the exact contract fingerprint, target, arguments, effects, scopes, principal, and expiry. Any material delta invalidates it.
8. Added or semantically broadened permissions are blocking even when schemas are compatible; scope elevation is bounded, visible, and separately approved.
9. Deprecation records include replacement, migration, earliest removal, observed usage, and decision owner. Retirement is an evidence-backed action, not an elapsed timer.
10. Test fixtures cover open and closed consumers, required/optional changes, enum and constraint changes, structured/text dual output, protocol mismatch, missing capabilities, authorization-context-dependent tool lists, scope step-up denial, stale approvals, and deprecation/retirement gates.

## Sources

| # | Primary source | Used for |
| --- | --- | --- |
| 1 | [MCP Specification 2026-07-28](https://modelcontextprotocol.io/specification/2026-07-28) | Protocol scope, security/trust principles, protocol primitives. |
| 2 | [MCP Versioning and Compatibility](https://modelcontextprotocol.io/specification/2026-07-28/basic/versioning) | Per-request version negotiation, modern/legacy interoperability, extensions. |
| 3 | [MCP Base Protocol](https://modelcontextprotocol.io/specification/2026-07-28/basic) | Statelessness, request metadata, JSON Schema dialect rules. |
| 4 | [MCP Server Discovery](https://modelcontextprotocol.io/specification/2026-07-28/server/discover) | `server/discover`, supported versions/capabilities, identity caveat. |
| 5 | [MCP Tools](https://modelcontextprotocol.io/specification/2026-07-28/server/tools) | Discovery, schemas, structured output, changes, validation, errors, human interaction. |
| 6 | [MCP Authorization](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization) | OAuth scopes, least privilege, step-up flow, audience-bound token rules. |
| 7 | [MCP Security Best Practices](https://modelcontextprotocol.io/docs/2026-07-28/tutorials/security/security_best_practices) | Scope minimization, consent and authorization-boundary risks. |
| 8 | [MCP 2026-07-28 Key Changes](https://modelcontextprotocol.io/specification/2026-07-28/changelog) | Removal of initialization/session semantics and current-revision changes. |
| 9 | [MCP Deprecated Features](https://modelcontextprotocol.io/specification/2026-07-28/deprecated) | Current deprecations, migrations, earliest-removal records. |
| 10 | [MCP Feature Lifecycle and Deprecation Policy](https://modelcontextprotocol.io/community/feature-lifecycle) | Active/Deprecated/Removed states and removal windows. |
| 11 | [JSON Schema Core 2020-12](https://json-schema.org/draft/2020-12/json-schema-core) | Dialects, identifiers, object applicators, open/closed object behavior. |
| 12 | [JSON Schema Validation 2020-12](https://json-schema.org/draft/2020-12/json-schema-validation) | `required`, type/enum assertions, `default`, and `deprecated`. |
| 13 | [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html) | Public API declaration, MAJOR/MINOR/PATCH meaning, deprecation, `0.y.z`. |
| 14 | [EvolveOS v0.1 Architecture](../../architecture.md) | Existing contract, approval, and expand-contract invariants. |
