# EvolveOS v0.2 discovery research

Status: complete, awaiting review
Branch: `research/v0.2-discovery`

## Research question

What is the smallest v0.2 slice that makes EvolveOS materially more trustworthy without turning it into another general-purpose agent framework?

## Source hierarchy

Use sources in this order:

1. specifications and protocol documents;
2. official product/framework documentation;
3. source code and tests in the owning repository;
4. first-party design notes or release documentation.

Secondary blog posts can suggest questions but cannot support final architecture claims. GitHub stars, social engagement and unsourced benchmark claims are not treated as product evidence.

## Workstreams

- [`pre-research-hypotheses.md`](pre-research-hypotheses.md) — assumptions captured before reading the landscape;
- `durable-execution-and-hitl.md` — checkpoints, retries, pause/resume and approvals;
- `contracts-and-mcp-evolution.md` — tool/schema compatibility and capability negotiation;
- `open-source-agent-landscape.md` — duplication and differentiation map;
- `observability-evaluation-and-safety.md` — trace, evaluation and audit evidence;
- `synthesis-and-v0.2-scope.md` — final evidence gates and selected slice.

## Claim rules

Every external factual claim must link directly to a primary source. Recommendations must be labeled as EvolveOS interpretation rather than source fact. A missing source is recorded as a gap instead of being filled from memory.

## Decision output

The synthesis must classify each candidate capability as one of:

- **integrate** — use an existing runtime or protocol boundary;
- **differentiate** — build because it represents the EvolveOS thesis;
- **defer** — valuable but not required for the next coherent slice;
- **reject** — duplicates existing work or cannot be validated yet.

## Publication boundary

Research notes may be committed to the repository. LinkedIn content remains a private draft until Julian explicitly approves the final copy and publication action.
