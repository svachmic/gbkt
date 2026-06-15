---
phase: 20
slug: codegen-fixes-banks-and-sprite-transparency
status: verified
threats_open: 0
asvs_level: 1
created: 2026-06-14
---

# Phase 20 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| (none new) | Phase 20 is verification/test/docs only — it ran existing JVM tests, built existing ROMs, authored UAT test classes driving an embedded local emulator, wrote a markdown audit doc, and captured hash/PNG/console evidence. No production code path, no user input, no network I/O. | None |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-20-01-NA | n/a | FIX-03 re-verify gate (test runs + evidence capture) | accept | No new attack surface; no production code, no user input, no network I/O. | closed |
| T-20-02-NA | n/a | FIX-03 audit doc authoring (`20-AUDIT-FIX-03.md`) | accept | Documentation only; no production code, no user input, no network I/O. | closed |
| T-20-03-NA | n/a | FIX-04 UAT screenshot-capture test classes | accept | Test-only code driving embedded Coffee-GB on local ROMs, writing PNG/JSON evidence under `build/` and `.planning/`. No user input, no network I/O, no production code. | closed |
| T-20-04-NA | n/a | Byte-identity hash sweep | accept | Runs existing `generateC` + `sha256sum`, writes hash-text evidence. No user input, no network I/O, no production code. | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| R-20-01 | T-20-01..04-NA | Phase added zero production code (verification/test/docs only); the 20-04 byte-identity oracle proved 7/7 affected-example generated-C files are byte-identical, i.e. no new runtime surface. All STRIDE entries authored at plan time carry `accept` dispositions with no applicable block-on. | Michal Švácha | 2026-06-14 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-06-14 | 4 | 4 | 0 | /gsd-secure-phase (short-circuit: threats_open=0, register_authored_at_plan_time=true) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-06-14
