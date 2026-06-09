# Phase 15: Full-green test suite for v0.1.0 release — fix all pre-existing - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-09
**Phase:** 15-full-green-test-suite-for-v0-1-0-release-fix-all-pre-existin
**Areas discussed:** Wide-blast-radius escalation, Wide-fix regression guard, Evidence tier per diagnosis, Removal latitude, IntegrationTest fix path

---

## Wide-Blast-Radius Escalation

| Option | Description | Selected |
|--------|-------------|----------|
| Fix inline, gate-first | Green suite is the release gate; fix wide bugs here with extra rigor (byte-identity sweep + buildRom). No new phase. | ✓ |
| Checkpoint per wide fix | Stop at human-verify checkpoint per wide fix; decide inline vs route-out case-by-case. | |
| Narrow-only here, route wide out | Fix only narrow bugs + stale assertions; route any wide real bug to a NEW phase; test stays red, v0.1.0 blocked. | |

**User's choice:** Fix inline, gate-first
**Notes:** Deliberately overrides the usual route-wide-blast-radius-out rule *for the purpose of closing the release gate* — routing the fix out would block v0.1.0 indefinitely. Wide fixes earn extra verification rigor instead (see next area). → D-01.

---

## Wide-Fix Regression Guard

| Option | Description | Selected |
|--------|-------------|----------|
| Split guard | Affected example: new output verified correct + screenshot + baseline re-pinned. Other 6: byte-identical. All 7: buildRom EXIT 0. | ✓ |
| Whole-suite re-green only | No per-example byte-identity for unaffected examples; just suite green + 7× buildRom EXIT 0. Risks silent collateral drift. | |

**User's choice:** Split guard
**Notes:** A real codegen-bug fix intentionally changes the affected example's C, so byte-identity-vs-pre-phase fails there by design; the guard splits into verify-and-re-pin (affected) vs byte-identical (other 6). → D-02.

---

## Evidence Tier per Diagnosis

| Option | Description | Selected |
|--------|-------------|----------|
| Live MCP screenshot required | Every visual-truth verdict needs a live MCP emulator screenshot in evidence/. Non-visual failures use static evidence. | ✓ |
| Screenshot only if asserting stale | Live screenshot only when verdict is "stale assertion"; "real bug" verdicts may skip an extra capture. | |
| Tier by diagnosis author's call | Leave evidence tier to the executor per failure. | |

**User's choice:** Live MCP screenshot required
**Notes:** Enforces CLAUDE.md Visual Evidence Rule + standing feedback for the banks dominant-colour and platformer facing/non-uniform UAT failures — the exact failure class (Phase 07.4 SC-4) the rule was written to catch. → D-03 / D-03b.

---

## Removal Latitude (Req 7)

| Option | Description | Selected |
|--------|-------------|----------|
| Last resort, capability-retired only | Removal only when covered capability is genuinely retired; cite it in the ledger. Expectation: zero removals among the 6 known classes. | ✓ |
| Normal third option | Treat removal as an equal path alongside fix/correct. | |

**User's choice:** Last resort, capability-retired only
**Notes:** PlayerMetaspriteGeometryTest is a renamed-array *correction* (sprite_player_frame_0 → player_metasprites), not a removal. → D-04.

---

## IntegrationTest Fix Path

| Option | Description | Selected |
|--------|-------------|----------|
| Diagnose-first, prefer root-cause | Root-cause why pluginTest republish doesn't clear the skew; prefer a durable hermetic fix over a one-off fixture patch if both work; diagnosis picks. | ✓ |
| Prefer hermeticity fix | Lean toward making pluginTest reliably clean/republish stale mavenLocal artifacts. | |
| Prefer fixture fix | Lean toward the smallest change: update the fixture call site to the current SceneIR signature. | |

**User's choice:** Diagnose-first, prefer root-cause
**Notes:** No path forced ahead of evidence; durable hermetic fix preferred where both work. → D-05.

---

## Claude's Discretion
- Fresh-run inventory format + order in which classes are driven green.
- Specific hermeticity mechanism for D-05 once root cause is known.
- Behavior-neutral re-pin of metasprites byte-identity baselines if drift detected at phase start.

## Deferred Ideas
None — discussion stayed within phase scope. All 11 keyword-matched todos are behavior/codegen changes already deferred by Phase 14 and remain out of scope (reviewed, not folded — see CONTEXT.md <deferred>).
