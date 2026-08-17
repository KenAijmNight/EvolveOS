# Contributing to EvolveOS

Thanks for helping make AI workflow evolution safer.

## Before opening a change

1. Keep the change inside one clear contract or safety boundary.
2. Add a failing behavior test first.
3. Implement the smallest code needed to pass it.
4. Run the full verification gate:

```bash
./mvnw verify
java -jar target/evolveos-0.1.0-SNAPSHOT.jar demo
```

## Pull requests

A pull request should explain:

- the problem and intended behavior;
- affected contract or proposal invariants;
- migration impact, if a public shape changes;
- verification commands and results;
- explicit non-goals.

Do not add real credentials, customer data, private exports, or generated local databases. External side effects must remain behind an explicit proposal and approval boundary.

## Code style

- target Java 21;
- prefer immutable records and small final classes;
- keep domain packages framework-free;
- use descriptive tests that assert behavior rather than implementation details;
- make collection ownership defensive with immutable copies.
