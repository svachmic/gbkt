# Phase 20: Codegen Fixes — Banks and Sprite Transparency - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-14
**Phase:** 20-codegen-fixes-banks-and-sprite-transparency
**Areas discussed:** Banks evidence form (FIX-03), tRNS visual oracle (FIX-04), Byte-identity sweep scope (Crit 5)

---

## Area selection

| Option | Description | Selected |
|--------|-------------|----------|
| Banks evidence form (FIX-03) | Standalone audit doc vs inline; SEED-014 re-verify-first | ✓ |
| tRNS visual oracle (FIX-04) | Capture harness + twin-shot regression guard | ✓ |
| Byte-identity sweep scope (Crit 5) | Per-commit cadence interpretation | ✓ |
| Inherit Phase 19 verbatim | Skip discussion, adapt Phase 19 directly | |

**User's choice:** Discuss all three (declined verbatim inherit).

---

## Banks evidence form (FIX-03)

| Option | Description | Selected |
|--------|-------------|----------|
| Standalone audit doc + gate | 20-AUDIT-FIX-03.md mapping seeds→sentinels; gate on fresh BanksEmissionTest GREEN re-verifying hasZoneSceneBinder first | ✓ |
| Inline evidence in VERIFICATION.md | Lighter, no standalone deliverable | |
| Standalone doc, no re-verify gate | Skips roadmap's explicit SEED-014-first instruction | |

**User's choice:** Standalone audit doc + gate (Recommended).
**Notes:** Mirrors Phase 19's `19-AUDIT-FIX-02.md`. Re-verify SEED-014 first per roadmap/REQUIREMENTS flag that `hasZoneSceneBinder` may already satisfy on master. → CONTEXT D-01/D-02/D-03.

---

## tRNS visual oracle (FIX-04)

| Option | Description | Selected |
|--------|-------------|----------|
| Reuse JVM UatTest harness, twin shots | StepAgent captureAndRename; metasprites outline + platformer player-transparency shots, GBC mode | ✓ |
| MCP gbkt-emulator capture | Interactive, not committed as repeatable test | |
| You decide example/harness split | Defer to planner/executor | |

**User's choice:** Reuse JVM UatTest harness, twin shots (Recommended).
**Notes:** Commit-traceable + deterministic; MCP wraps same StepAgent so equivalent. Clean ROM rebuild + correct GBC target mode required. → CONTEXT D-04/D-05.

---

## Byte-identity sweep scope (Crit 5)

| Option | Description | Selected |
|--------|-------------|----------|
| Affected-examples diff per commit + final full sweep | Phase 19-style procedural diff on banks/metasprites/platformer per commit + one full 7-example sweep at close | ✓ |
| Literal full 7-ROM sweep after every commit | Maximally strict, heavy build cost, pong noise | |
| Single full 7-ROM sweep at phase close only | Lightest, loses per-commit attribution | |

**User's choice:** Affected-examples diff per commit + final full sweep (Recommended).
**Notes:** Interprets Criterion 5's "after every commit" as per-commit attribution on the affected set + a single full-coverage proof at close. No-codegen-change phase → only affected examples can drift; pong non-determinism makes literal per-commit full sweep noisy. → CONTEXT D-06.

---

## Claude's Discretion

- Exact test method/assertion names, evidence PNG filenames, precise hashing command for byte-identity diffs, and whether any FIX-03 guard gap actually needs a new assertion (audit may find full existing coverage).

## Deferred Ideas

- Platformer `cEmit()` escapes + remaining DSL/tooling seeds → Phase 21 (FIX-05/FIX-06).
- Merging PR #77 (S3776 burn-down) → held open until Phases 19/20/21 complete.
- Reviewed-not-folded todos: `13.8-palette-bank-codegen-followups.md` (WR-01/02/03),
  `configbuilder-cartridge-setter-api-consistency.md`, `easetozero-oscillates-when-by-greater-than-one.md`,
  `orelse-may-attach-to-wrap-guard-ifop.md`, `compilerom-silent-mbc5-fallback-warning.md` —
  all new robustness/API work, out of scope for a no-codegen-change confirmation phase.
