---
phase: 11-port-banks-gbdk-example-to-gbkt
plan: 14
subsystem: phase-close
tags: [phase-close, smoke-test, seeds, roadmap, handoff]
requires:
  - 11-06-ir-test (BanksIRTest GREEN)
  - 11-07-emission-inv1-inv2 (INV-1 GREEN, INV-2 RED-by-design)
  - 11-08-emission-inv3-inv4 (INV-3 + INV-4 GREEN)
  - 11-10-named-bug-fix (trigger_<id> stub fix + arity correction)
  - 11-11-uat-anchor1-anchor2 (visual UAT — failed; routed to 11.1)
  - 11-13-anchor3-noi (Anchor 3 GREEN + 4th-signal)
provides:
  - Phase 11 closure verdict (RED — 2 visual anchors fail)
  - Phase 11.1 placeholder (TERMINAL) inserted in ROADMAP
  - 3 surplus seeds captured (SEED-014, SEED-015, SEED-016)
  - One-page verification handoff at evidence/handoff.md
affects:
  - Phase 11.1 (TERMINAL closer subphase — discuss-phase + research required)
  - .planning/seeds/ inventory grew from 13 to 16
  - ROADMAP.md Phase 13 unchanged (no DSL-shaping gaps surfaced)
tech-stack:
  added: []
  patterns: [seed-sweep, conditional-terminal-subphase, codegen-pass-vs-dsl-gap-distinction]
key-files:
  created:
    - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/final-buildrom.log
    - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/handoff.md
    - .planning/seeds/SEED-014-banks-bkg-tiles-load-banked-gating.md
    - .planning/seeds/SEED-015-banks-trampoline-body-inheritance.md
    - .planning/seeds/SEED-016-banks-anchor4-sram-not-executed.md
    - .planning/phases/11-port-banks-gbdk-example-to-gbkt/11-14-SUMMARY.md
  modified:
    - .planning/ROADMAP.md (Phase 11.1 placeholder inserted between Phase 11 and Phase 12)
decisions:
  - Phase Verdict is RED, not GREEN — 2/4 visual anchors fail (Anchor 1+2) on a single shared root cause (SEED-014 _bkg_tiles_load_banked gating); honest handoff per memory feedback_quality_over_shortcuts.md
  - Phase 11.1 TERMINAL marker explicit in ROADMAP — per CONTEXT D-19 + memory feedback_many_small_plans_terminal_subphase, no Phase 11.1.1
  - Phase 13 NOT edited — surplus defects are codegen-pass bugs (incorrect gating expression in GBDKPipelineV2) NOT DSL-shaping surface gaps; routed to Phase 11.1 instead per CONTEXT D-17
  - SEED-014 flagged WIDE blast radius in seed file — Phase 11.1 plan MUST run /gsd-discuss-phase + research-phase first per memory feedback_route_to_proper_phase_when_blast_radius_is_wide
metrics:
  duration: ~22 minutes (smoke test + 5 file writes + 4 commits)
  completed: 2026-05-20
---

# Phase 11 Plan 14: phase-close Summary

Closed Phase 11 (banks port) with an honest **RED** verdict: BLOCKING smoke test PASSES and the ROM produces cleanly (`banks.gb`, 64 KB, MBC5 `0x1b`), but 2 visual UAT anchors fail and 1 was not executed — all routed to the conditionally-inserted Phase 11.1 terminal closer.

## What Shipped

1. **BLOCKING smoke test artifact** (`evidence/final-buildrom.log`, 7.2 KB) — `./gradlew :gbkt-examples:banks:clean :gbkt-examples:banks:buildRom` exits 0 cleanly. No `warning:`, `error:`, `SDCC:`, `undefined identifier`, `unknown address`, `unknown value`, or `BUILD FAILED` patterns. ROM materialized at 65536 bytes. Banks test suite 13/14 GREEN (INV-2 RED-by-design sentinel intentionally preserved).
2. **3 surplus seeds** captured under `.planning/seeds/`:
   - SEED-014: `_bkg_tiles_load_banked` helper gated behind sport-racing genre (WIDE blast radius — root cause of INV-2 RED + Anchor 1+2 visual failure)
   - SEED-015: Scene trampoline body inheritance bug from Plan 11-05 D11-05-1 (LOCAL blast radius)
   - SEED-016: Phase 11 Anchor 4 (SRAM GBST round-trip) not executed (UAT execution gap)
3. **Phase 11.1 placeholder** inserted in ROADMAP.md between Phase 11 and Phase 12 with explicit `TERMINAL` marker, "Seeds to close:" list, and `/gsd-discuss-phase 11.1 --research-phase 11.1` requirement flagged.
4. **One-page verification handoff** at `evidence/handoff.md` — 11-row verdict table (4 anchors + 4 INVs + 4th-signal + BLOCKING smoke + named-bug) + detail sections + Phase Verdict `**RED**` + ROADMAP follow-up state. This is the plan-checker / verifier entry point.

## Quick Verdict Table (cross-reference)

| Signal | Status | Notes |
|--------|--------|-------|
| Anchor 1 (cross-bank scene nav) | FAIL (visual) / PASS (variable) | SEED-014 root cause; routed to Phase 11.1 |
| Anchor 2 (zone tilemap visible) | FAIL | Same SEED-014 root cause |
| Anchor 3 (MBC5 byte 0x0147) | PASS | `0x1b` confirmed |
| Anchor 4 (SRAM GBST round-trip) | NOT EXECUTED | SEED-016 — Plan 11-12 was skipped |
| INV-1 (BANKED keyword) | PASS | `play_enter`/`play_frame`/`play_exit` all BANKED |
| INV-2 (SWITCH_ROM wrapper) | FAIL (RED-by-design) | locks SEED-014 routing gate |
| INV-3 (mbcType propagation) | PASS | `cartridge=MBC5_RAM_BATTERY mbcType=0x1B` |
| INV-4 (SRAM write + trigger_saves) | PASS | `ENABLE_RAM` + SRAM byte writes + `trigger_saves()` |
| 4th-signal (.noi bank ≤ 16384) | PASS | banks 0/1/2 at 0/51/1 bytes |
| BLOCKING smoke test | PASS | BUILD SUCCESSFUL, 65536 byte ROM |
| Named codegen bug | CLOSED | Plan 11-10 commits 56b70d74 + d4be4679 |

## Phase Verdict

**RED.** The verdict is honest: codegen + linker contract is sound (Anchor 3 + INV-1/INV-3/INV-4 + 4th-signal all GREEN; ROM produces; named bug fixed), but 2 visual anchors fail. The 2 visual failures share a single root cause (SEED-014) routed to Phase 11.1 with the WIDE-blast-radius discipline that memory `feedback_route_to_proper_phase_when_blast_radius_is_wide` requires.

## Reasoning — Why no Phase 13 edit

Phase 13 is the "framework primitives surfaced by example ports (rolling)" collector for **DSL-shaping** gaps — typed enums replacing magic strings, declarative primitives that absorb hand-rolled patterns, asset-driven imports that hide codegen plumbing. Per CONTEXT D-17 + RESEARCH §"Pitfall 1", a Phase 13 edit is warranted only when "a framework-shaping DSL gap surfaced after the port works".

The surplus defects this port produced are codegen-pass bugs:

- **SEED-014** is a gating-expression bug in `GBDKPipelineV2.kt:972-980` (`hasSportRacing && bank > 1` is the wrong predicate — should be `gameIR.zones.isNotEmpty()`-equivalent). The DSL surface is fine; the codegen pipeline misroutes the helper emission.
- **SEED-015** is a trampoline-emission-loop bug in `GBDKPipelineV2.buildSceneNavigationFunction` (inherits previous banked scene's body for HOME-resident scenes). Again, DSL surface is fine; codegen pipeline mis-emits.
- **SEED-016** is a UAT execution gap (Plan 11-12 skip), not a DSL or codegen gap.

None of these point at "the DSL forced me to do X" or "I had to escape via raw() because Y" — the patterns memory `feedback_route_to_proper_phase_when_blast_radius_is_wide` lists as Phase 13 triggers. Phase 11 used the existing DSL surface end-to-end (`config { cartridge = "MBC5_RAM_BATTERY"; ramBanks = 2 }`, `zone { tileset(asset(...)) }`, `saveData { slots(2); ... }`, `triggerSystem("saves")`) without needing new DSL primitives.

Candidates considered and explicitly rejected:

- **Typed `Cartridge` enum.** Already Phase 13 item 1. No new edit needed.
- **SRAM-bank-assignment DSL.** Rejected per CONTEXT D-17: SaveDataBuilder covers SRAM honestly; no re-route.
- **SaveDataBuilder fluent `save.write()` surface** (replacing `triggerSystem("saves")` magic-string). The port did NOT prove this was a barrier — Plan 11-10's codegen fix unblocked it via the existing API. A future port may sharpen this; until then, no Phase 13 item.
- **Cartridge dual-source (DSL `config` + Gradle `gbkt {}`).** Mentioned in CONTEXT/RESEARCH but did NOT surface as a Phase 11 blocker. Subsumed under Phase 13 item 1 (typed Cartridge) when that lands.

## Deviations from Plan

**None — plan executed as written.**

Conditional branches that fired:

- Task 2 found 3 surplus seeds → Task 3 inserted the Phase 11.1 placeholder (conditional path GREEN).
- Task 4 found 0 framework-shaping DSL gaps → no Phase 13 edit (conditional path skipped, reasoning documented in handoff.md + this SUMMARY).
- BLOCKING smoke test acceptance gate read: `BUILD SUCCESSFUL` present, no `warning:`/`error:`/`SDCC:`/forbidden patterns. The lone Kotlin compiler note about reified `T` inference in `GenerateCTask.kt:564` is a language-level info note unrelated to Phase 11 and pre-existed the phase.

## Files Modified / Created

| File | Action | Purpose |
|------|--------|---------|
| `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/final-buildrom.log` | created | BLOCKING smoke test artifact |
| `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/handoff.md` | created | One-page verification entry point |
| `.planning/seeds/SEED-014-banks-bkg-tiles-load-banked-gating.md` | created | Surplus seed (WIDE) |
| `.planning/seeds/SEED-015-banks-trampoline-body-inheritance.md` | created | Surplus seed (LOCAL) |
| `.planning/seeds/SEED-016-banks-anchor4-sram-not-executed.md` | created | Surplus seed (UAT exec gap) |
| `.planning/ROADMAP.md` | modified | Phase 11.1 placeholder inserted (TERMINAL) |

## Commits

| Hash | Type | Description |
|------|------|-------------|
| `da807b04` | test(11-14) | BLOCKING final clean buildRom smoke test PASS |
| `96ca8c50` | docs(11-14) | sweep surplus codegen defects to seeds |
| `0b24780d` | docs(11-14) | insert Phase 11.1 placeholder (INSERTED, TERMINAL) |
| `7407cdd2` | docs(11-14) | write one-page Phase 11 verification handoff |

## Open Items for Verifier (Task 6 — checkpoint:human-verify)

Per Plan 11-14 Task 6:

1. Read `evidence/handoff.md` — spot-check the verdict table against underlying evidence files.
2. Open `evidence/uat-screenshots/anchor1-play-scene.png` and `anchor2-tilemap.png` — confirm visually they are blank 413-byte frames (per CLAUDE.md Visual Evidence Rule, this is the BINDING evidence that proves Anchor 1+2 FAIL).
3. Read last 30 lines of `evidence/final-buildrom.log` — confirm `BUILD SUCCESSFUL`.
4. Check ROADMAP.md §1354: Phase 11.1 entry present AND marked TERMINAL; no Phase 11.1.1; Phase 13 unchanged.
5. Confirm scope cap: ONE example shipped (`gbkt-examples/banks/`), ONE named codegen bug-fix (`trigger_<id>` in `visitSaveSystem`), surplus → seeds.
6. Run `git status` / `git log --oneline -10` to review the diff before merge.

**Resume signal options:**

- `approved` — Phase 11 closed (RED verdict acknowledged); proceed to `/gsd-verify-work 11`.
- `regressed: <reason>` — name the plan to re-open.
- `route to phase: <N>` — wide-blast surplus that should NOT live in Phase 11.1.

## Self-Check: PASSED

Verified before this SUMMARY commit:

- `test -f .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/final-buildrom.log` → FOUND
- `grep -q "BUILD SUCCESSFUL" .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/final-buildrom.log` → FOUND
- `test -f gbkt-examples/banks/build/gbkt/output/banks.gb` → FOUND (65536 bytes)
- `test -f .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/handoff.md` → FOUND
- `test -f .planning/seeds/SEED-014-banks-bkg-tiles-load-banked-gating.md` → FOUND
- `test -f .planning/seeds/SEED-015-banks-trampoline-body-inheritance.md` → FOUND
- `test -f .planning/seeds/SEED-016-banks-anchor4-sram-not-executed.md` → FOUND
- `grep -q "^### Phase 11.1:" .planning/ROADMAP.md` → FOUND (line 1354)
- `grep -q "TERMINAL" .planning/ROADMAP.md` → FOUND (Phase 11.1 block, lines 1354 + 1356)
- `grep -q "^### Phase 11.1.1" .planning/ROADMAP.md` → NOT FOUND (correct — terminal rule)
- `grep -c "^### Phase 13" .planning/ROADMAP.md` → 1 (correct — no duplicate)
- Commits da807b04, 96ca8c50, 0b24780d, 7407cdd2 → FOUND in `git log --oneline -8`
