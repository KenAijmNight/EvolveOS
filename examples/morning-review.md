# Morning-review example

The v0.1 demo uses two local context items:

```text
task-2: Write documentation [docs]
task-1: Ship v0.1 [urgent, release] — blocked until the PR is green
```

The deterministic skill scores explicit signals rather than calling a model:

- `urgent`: +100;
- body contains `blocked`: +50;
- `release`: +20;
- ties are resolved by context ID.

It therefore selects `task-1` and creates:

```text
proposal-task-1
status: DRAFT
confidence: 0.91
evidence:
  - context:task-1
  - tag:urgent
required permission:
  - tasks:write
proposed action:
  - Mark task-1 as high priority
```

The proposal cannot execute while it is `DRAFT`. The demo explicitly approves it and then moves it to `EXECUTED`. No external task is modified in v0.1.

Finally, the demo compares morning-review contract v1 with v2. Version 2 adds `confidence` to the output and `audit:write` to the permission set, so the planner emits all expand-contract stages and requires verification before v1 can be retired.

Run it with:

```bash
./mvnw verify
java -jar target/evolveos-0.1.0-SNAPSHOT.jar demo
```
