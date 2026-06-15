# Phase 18: Deprecation Removals and Sonar Burn-down - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-13
**Phase:** 18-deprecation-removals-and-sonar-burn-down
**Areas discussed:** Unification direction, Removal timing, Deprecation-train doc, S3776 burn-down strategy, NOSONAR policy, Commit attribution, CHANGELOG home

---

## Unification direction (DEPR-01)

| Option | Description | Selected |
|--------|-------------|----------|
| `runIf` survives (SEED-023) | Remove `whenever`, migrate ~63 example sites + ~250 KDoc refs; coheres with runIf/unless/orElse family | ✓ |
| `whenever` survives | Remove `runIf` (8 sites); dominant idiom, smaller migration; reads awkwardly with orElse | |
| Keep both, differentiate | Give `whenever` real edge-trigger semantics; per-site RAM cost; doesn't reduce surface | |

**User's choice:** `runIf` survives.
**Notes:** Census surfaced that `whenever` is the dominant idiom (327 vs 31; 63 vs 8 example sites) and has a pool-collision overload `runIf` lacks — both points raised before the choice. User still chose the seed's direction; runIf/unless/orElse family coherence + the "whenever over-promises reactive" honesty argument won. Both `whenever` overloads migrate; `unless`/`orElse` survive unchanged (independent sugar).

---

## Removal timing (DEPR-01 + DEPR-02)

| Option | Description | Selected |
|--------|-------------|----------|
| Hard-remove now (v0.1.1) | Delete both + migrate in same change; no grace release | ✓ |
| Deprecate now, delete in v0.2.0 | @Deprecated shim this phase, delete next milestone | |
| Split by current state | Remove combatIsInState now, shim whenever to v0.2.0 | |

**User's choice:** Hard-remove now (v0.1.1).
**Notes:** Both APIs shipped un-deprecated in v0.1.0. Roadmap success-criterion 1 ("removed in the same change"), SEED-028's hard-removal precedent, and ~zero pre-1.0 adoption all favored the hard removal. CONTRIBUTING.md documents the WARNING→v+1 train for the future.

---

## Deprecation-train doc (DEPR-03)

| Option | Description | Selected |
|--------|-------------|----------|
| Two-tier rule | WARNING→v+1 default + explicit pre-1.0/Hardening hard-removal carve-out + mandatory CHANGELOG note | ✓ |
| Strict train + noted exceptions | Only the train as the rule; v0.1.1 removals are one-time inline exceptions | |
| Mechanics only | Document annotation form + cadence; no hard-removal policy | |

**User's choice:** Two-tier rule.
**Notes:** Convention must honestly describe a milestone that hard-removed three things. Cite SEED-023/025/028 as worked examples of the carve-out.

---

## S3776 burn-down — NOSONAR policy (SONAR-01)

| Option | Description | Selected |
|--------|-------------|----------|
| Last-resort, irreducible only | Default extract-method; NOSONAR only for inherently-flat dispatch; inline rationale + tracked seed; spend 0–2 of 5 | ✓ |
| Pragmatic per-finding call | Decide per finding; spend up to 5 freely | |
| Zero NOSONAR | Forbid suppressions entirely | |

**User's choice:** Last-resort, irreducible only.
**Notes:** Each suppression gets an inline rationale + a tracked v0.2.0 seed so it's revisited.

---

## S3776 burn-down — Commit attribution (SONAR-02)

| Option | Description | Selected |
|--------|-------------|----------|
| One finding/commit (emitting); batch non-emitting | Own commit + own 7-example sweep per emitting finding; non-emitting batched per-file with JVM evidence; final consolidated sweep | ✓ |
| One file/visitor per commit | Group findings per file, sweep per file; coarser attribution | |
| Wave-batched, sweep per wave | One sweep per module wave; weakest attribution | |

**User's choice:** One finding/commit for emitting code; batch non-emitting.
**Notes:** Maximizes byte-identity oracle attribution. Requirements already lock "never combine with seed-fix commits."

---

## CHANGELOG home

| Option | Description | Selected |
|--------|-------------|----------|
| New root CHANGELOG.md | Keep a Changelog format; adopter-facing; convention points here | ✓ |
| Reuse .planning/MILESTONES.md | Planning-tree only; not adopter-facing | |
| Defer the file choice to planning | Capture requirement, let planner pick location | |

**User's choice:** New root CHANGELOG.md.
**Notes:** No CHANGELOG exists today. v0.1.1 entry records whenever removal, combatIsInState(String) removal, ConfigBuilder setter migration.

---

## Claude's Discretion

- Exact extract-method decomposition of each S3776 method (names, boundaries) — research/planning, within D-05/D-06 + byte-identity.
- Whether `CombatStatesTest.kt` equivalence test is deleted or re-expressed against the typed form.

## Deferred Ideas

- `configbuilder-cartridge-setter-api-consistency.md` — broader ConfigBuilder setter-convention redesign (v0.2.0); reviewed, not folded.
- `orelse-may-attach-to-wrap-guard-ifop.md` — orElse/wrap-guard correctness bug; adjacent but belongs to a FIX phase (19–21); reviewed, not folded.
- Threading `TargetProfile.bitsPerPixel`/`screen` into codegen — v0.2.0 backlog.
- Remaining 0.6-match codegen/asset bug todos — Phases 19–21.
