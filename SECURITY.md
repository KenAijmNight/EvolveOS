# Security policy

EvolveOS v0.1 is an experimental local reference implementation. It performs no network calls and ships no external provider integration.

## Reporting a vulnerability

Please do not open a public issue for a vulnerability involving credential exposure, unsafe approval bypasses, permission escalation, or arbitrary code execution. Instead, use GitHub's private security advisory flow for this repository.

Include:

- the affected version or commit;
- reproduction steps;
- the expected safety boundary;
- the observed behavior;
- any suggested mitigation.

Never include real API keys, private datasets, or personal exports in a report.

## Supported versions

Until the first tagged release, only the latest commit on `main` is considered for security fixes.
