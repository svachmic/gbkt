# Phase 14: cleanup for v0.1.0 release - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-06
**Phase:** 14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff
**Areas discussed:** Run-verification method, Byte-identity baseline strategy, V2 rename execution, Track ordering, CI workflow rewrite extent

---

## Run-Verification Method — what counts as "run" evidence?

| Option | Description | Selected |
|--------|-------------|----------|
| Live MCP screenshot each | Boot each KEEP ROM in MCP emulator, capture screenshot it renders. Matches Visual Evidence Rule. | ✓ |
| Tiered: screenshot + tests | Screenshot for visual/playable; buildRom+JVM tests for headless infra examples | |
| buildRom + existing tests | buildRom EXIT 0 + existing emulator/UAT suite passing | |

**User's choice:** Live MCP screenshot each.

### Run-Verification Method — run-check depth

| Option | Description | Selected |
|--------|-------------|----------|
| Boot + one input cycle | Boot to first screen, capture, drive one input, capture again. Reuse PLAYBOOK.md. | ✓ |
| Boot screenshot only | Single first-frame screenshot | |
| Full scripted play-through | Full UAT/playbook end-to-end, multiple checkpoints | |

**User's choice:** Boot + one input cycle.
**Notes:** racer = RETIRE (known-dead, not repaired).

---

## Byte-Identity Baseline Strategy — capture scope / source of truth

| Option | Description | Selected |
|--------|-------------|----------|
| Full generated-C snapshot, all KEEP | Snapshot full main.c/bank*.c (SHA) for every KEEP into evidence/; committed sprite tests as 2nd gate | ✓ |
| Lean on committed sprite tests | Trust existing 2 sprite byte-identity tests; ad-hoc diff for the rest | |
| Full snapshot + commit as fixtures | Full snapshot AND commit as permanent new ByteIdentity test fixtures | |

**User's choice:** Full generated-C snapshot, all KEEP.

### Byte-Identity Baseline Strategy — capture timing / gate frequency

| Option | Description | Selected |
|--------|-------------|----------|
| Capture at phase start; gate after each track | Snapshot pre-phase HEAD; diff after rename AND after sweep — localizes drift | ✓ |
| Capture at start; gate once at end | Single diff at the very end | |

**User's choice:** Capture at phase start; gate after each track.

---

## V2 Rename Execution — tooling

| Option | Description | Selected |
|--------|-------------|----------|
| Semantic rename, then textual sweep | rename_symbol for big symbols → sed for filenames/KDoc/test-class names; byte-identity backstop | ✓ |
| Pure semantic rename | rename_symbol only (slow at scale, misses filenames/docs) | |
| Pure textual sed | Scripted find+sed on V2 token (fast, risks false matches) | |

**User's choice:** Semantic rename, then textual sweep.

---

## Track Ordering

| Option | Description | Selected |
|--------|-------------|----------|
| Audit → retire → baseline → sweep → rename → CI/docs | Smallest rename surface; collision pre-cleared by sweep before rename | ✓ |
| Audit → baseline → rename → sweep → retire → CI/docs | Larger surface; hits generate()/generateV2 collision before sweep clears it | |
| Let planner sequence it | Lock only the SPEC collision constraint, planner decides rest | |

**User's choice:** Audit → retire → baseline → sweep → rename → CI/docs.

---

## CI Workflow Rewrite Extent

| Option | Description | Selected |
|--------|-------------|----------|
| Wire all KEEP examples | build + generateC for every survivor; no buildRom (GBDK-free CI) | ✓ |
| Minimal swap | Drop explorer, substitute one or two survivors | |
| Let planner decide breadth | Lock "remove archived refs, build+generateC only", planner picks list | |

**User's choice:** Wire all KEEP examples (build + generateC only, no buildRom).

---

## Claude's Discretion

- Exact dead-code reachability-proof technique (within the conservative mandate).
- Whether `rpgregistry-clear-never-called` folds into the Req-4 sweep — decided at sweep time based on the non-reachability proof.

## Deferred Ideas

- Behavior/correctness todos reviewed but not folded (out of scope for cleanup-only phase): `compilerom-silent-mbc5-fallback-warning`, `configbuilder-cartridge-setter-api-consistency`, `easetozero-oscillates-when-by-greater-than-one`, `orelse-may-attach-to-wrap-guard-ifop`, `triggersystem-ref-registry-validation`, `wrapat-decrement-asymmetry-mask-vs-compare`, `wrapat-zero-silent-always-reset`, `13.8-palette-bank-codegen-followups`, `13.6-07-convertsprites-hardening-followups`.
- `metasprites-byte-identity-baseline-stale-since-12.8` — folded (effectively addressed by 13.6-07 re-pin + full-snapshot gate).
