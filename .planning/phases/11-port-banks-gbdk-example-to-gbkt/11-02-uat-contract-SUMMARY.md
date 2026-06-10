---
phase: 11-port-banks-gbdk-example-to-gbkt
plan: 02
subsystem: testing
tags: [uat, banking, mbc5, sram, visual-evidence-rule, gbst, mcp]

requires:
  - phase: 11-port-banks-gbdk-example-to-gbkt
    provides: Plan 11-01 wave-0 scaffold (gbkt-examples/banks gradle subproject placeholder)
provides:
  - 4-anchor UAT contract document (binding evidence-shape spec) for banks port
  - Reserved evidence paths (uat-screenshots/anchor{1,2}-*.png; evidence/anchor{3,4}-*.txt)
  - MCP script skeletons for all 4 anchors (scene nav, zone tilemap, MBC5 byte, SRAM round-trip)
  - GBST save-state/load-state pattern locked as anchor-4 mechanism (per RESEARCH §Pitfall 3)
  - Verbatim Visual Evidence Rule quote from CLAUDE.md (visual anchors 1+2; mechanism anchors 3+4)
affects:
  - 11-03-playbook-PLAN.md (consumes anchor names verbatim for PLAYBOOK.md MCP scripts)
  - 11-09-first-buildrom-bug-naming-PLAN.md (UAT contract drives bug naming target)
  - 11-10-named-bug-fix-PLAN.md (defines the 4 contract surfaces the named bug must restore)
  - 11-11-uat-anchor1-anchor2-PLAN.md (executes anchors 1+2; screenshot evidence)
  - 11-12-uat-anchor4-sram-PLAN.md (executes anchor 4; GBST round-trip)
  - 11-13-anchor3-noi-PLAN.md (executes anchor 3 ROM-byte file evidence)
  - 11-14-phase-close-PLAN.md (closes phase against this contract)

tech-stack:
  added: []  # markdown-only contract; no code deps
  patterns:
    - "UAT-first sequencing (Phase 9 D-03 / Phase 10 D-03 / Phase 11 D-11) — contract locked before any DSL"
    - "Visual-vs-mechanism evidence split (CLAUDE.md Visual Evidence Rule + corollary)"
    - "GBST save-state round-trip as SRAM-persistence test substitute (RESEARCH §Pitfall 3)"
    - "4-anchor cap as ONE-TIME EXCEPTION (CONTEXT D-09); future ports must justify any expansion"

key-files:
  created:
    - "gbkt-examples/banks/11-UAT.md (binding 4-anchor UAT contract)"
  modified: []

key-decisions:
  - "Anchors 1+2 classified as visual truths → require emulator_screenshot at climax; anchors 3+4 classified as mechanism truths → variable/file evidence only"
  - "Anchor 4 mechanism = GBST emulator_save_state + emulator_load_state round-trip (NOT emulator_stop + emulator_start) per RESEARCH §Pitfall 3 (Coffee-GB MemoryBattery does not persist SRAM)"
  - "Anchor 3 expects ROM byte 0x1b at offset 0x0147 (MBC5+RAM+BATT) — DSL must use cartridge = \"MBC5_RAM_BATTERY\" (NOT \"MBC5\" which yields 0x19 without BATT) per RESEARCH §Pitfall 5"
  - "4-anchor cap is ONE-TIME EXCEPTION to Phase 9/10's 3-anchor pattern, justified by BANKED contract's four distinct surfaces (ROM code-banks, ROM data-banks, MBC byte, SRAM)"
  - "Visual Evidence Rule quoted verbatim from CLAUDE.md — single source of truth, no paraphrase drift"

patterns-established:
  - "UAT contract document structure mirrors 10-UAT.md (frontmatter + Visual Evidence Rule + Tests + Anti-overfitting + Summary)"
  - "Per-anchor field schema: Behavior / Evidence type / Evidence path / mcp_script / Expected / Result"
  - "Evidence path convention: uat-screenshots/<slug>.png for visual; evidence/<slug>.txt for mechanism"
  - "MCP-script comments explicitly cite RESEARCH §Pitfall N when the recipe deviates from naive expectation (anchor 4 cites §Pitfall 3 verbatim)"

requirements-completed:
  - BANK-01
  - BANK-02
  - BANK-03
  - BANK-04

duration: ~6min
completed: 2026-05-20
---

# Phase 11 Plan 02: UAT contract Summary

**4-anchor UAT contract locked for banks port — visual anchors 1+2 reserve screenshot
paths, mechanism anchors 3+4 reserve text-evidence paths, GBST round-trip pinned as
anchor-4 mechanism per RESEARCH §Pitfall 3, Visual Evidence Rule quoted verbatim from
CLAUDE.md.**

## Performance

- **Duration:** ~6 min
- **Started:** 2026-05-20T05:00:21Z (approx — session start)
- **Completed:** 2026-05-20T05:06:41Z
- **Tasks:** 1
- **Files modified:** 1 (created)

## Accomplishments

- Created `gbkt-examples/banks/11-UAT.md` with 4 named anchors (one-time exception to
  Phase 9/10's 3-anchor cap, per CONTEXT D-09).
- Each anchor has 6 labeled fields: Behavior / Evidence type / Evidence path /
  `mcp_script` block / Expected / Result.
- Visual Evidence Rule from CLAUDE.md quoted verbatim, with explicit visual-vs-mechanism
  split (anchors 1+2 = visual + screenshot binding; anchors 3+4 = mechanism + variable/file
  binding).
- Anchor 4 mcp_script uses `emulator_save_state` + `emulator_load_state` GBST round-trip
  pattern (per RESEARCH §Pitfall 3); explicit prose comment in the script body cites the
  pitfall and explains why `emulator_stop` + `emulator_start` is rejected (Coffee-GB
  `MemoryBattery` is in-memory; `SavestateManager` captures WRAM/OAM/HRAM but NOT SRAM
  0xA000–0xBFFF).
- Anchor 3 expectation pinned to `0x1b` (MBC5+RAM+BATT) with explicit DSL guidance:
  `cartridge = "MBC5_RAM_BATTERY"` (NOT `"MBC5"` which yields `0x19`) per RESEARCH §Pitfall 5.
- Anti-overfitting note documents the 4-anchor cap as a ONE-TIME EXCEPTION and
  reaffirms D-overfitting-1/2/3 (UAT verifies BANKED contract, not GBDK reference
  text-rendering shape).
- Evidence directory paths reserved: `evidence/uat-screenshots/anchor{1,2}-*.png` and
  `evidence/anchor{3,4}-*.txt` — populated by Plans 11-11/12/13.

## Task Commits

Each task was committed atomically:

1. **Task 1: Author 11-UAT.md with 4 anchor behaviors** — `73e31fb7` (docs)

## Files Created/Modified

- `gbkt-examples/banks/11-UAT.md` (created) — 214 lines; binding 4-anchor UAT contract
  with frontmatter, Visual Evidence Rule quote, per-anchor schema, MCP-script
  skeletons, anti-overfitting note, and Summary tally (total: 4, passed: 0,
  pending: 4).

## Decisions Made

None beyond what the plan / CONTEXT already prescribed. The plan was executed exactly
as specified — anchor names, evidence paths, MCP-script skeleton, Visual Evidence Rule
quote location, and anti-overfitting note all match `<action>` and
`<acceptance_criteria>` literals.

## Deviations from Plan

None — plan executed exactly as written.

The plan specified all anchor IDs, evidence path slugs, mcp_script skeletons, and
acceptance literals; the executor copied the Visual Evidence Rule paragraph verbatim
from CLAUDE.md (lines beginning "For verification truths shaped..." plus the
following paragraph on `assertVariable` corollary) and produced the 4-anchor doc.

## Issues Encountered

None.

## Verification

Plan `<automated>` gate:
```bash
test -f gbkt-examples/banks/11-UAT.md && \
  grep -c "Anchor [1-4]:" gbkt-examples/banks/11-UAT.md | grep -qE "^4$"
# → PASS (file exists; exactly 4 anchor headings)
```

Acceptance criteria literal-grep gates (from `<acceptance_criteria>`):

| Literal | Count | Status |
|---------|-------|--------|
| `### Anchor [1-4]:` headings | 4 | PASS |
| `evidence/uat-screenshots/anchor1-play-scene.png` | 2 (mcp_script + Evidence path) | PASS |
| `evidence/uat-screenshots/anchor2-tilemap.png` | 2 | PASS |
| `emulator_save_state(` | 1 | PASS |
| `emulator_load_state(` | 1 | PASS |
| `MBC5_RAM_BATTERY` | 1 | PASS |
| `0x1b` / `0x1B` | 5 lowercase + others | PASS |
| `Visual Evidence Rule` | 4 mentions (section header + quote + corollary + anti-overfitting) | PASS |
| `emulator_stop()` followed by `emulator_start()` as anchor-4 mechanism | 0 (only present in prose explaining why it is rejected) | PASS |

Per-anchor schema audit (6 fields × 4 anchors = 24 OK; 0 MISSING):

| Anchor | Behavior | Evidence type | Evidence path | mcp_script | Expected | Result |
|--------|----------|---------------|---------------|------------|----------|--------|
| 1      | OK       | OK            | OK            | OK         | OK       | OK     |
| 2      | OK       | OK            | OK            | OK         | OK       | OK     |
| 3      | OK       | OK            | OK            | OK         | OK       | OK     |
| 4      | OK       | OK            | OK            | OK         | OK       | OK     |

`key_links` pattern (from frontmatter):

| from | to | pattern | match |
|------|----|---------|-------|
| 11-UAT.md anchor 4 mcp_script | RESEARCH §Pitfall 3 | `emulator_save_state\(.*anchor4-pre-reboot` | `emulator_save_state(path=".planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/anchor4-pre-reboot.gbst")` — MATCH |

## User Setup Required

None — markdown-only contract; no external service configuration required.

## Next Phase Readiness

Contract is binding. Downstream plans inherit:

- **Plan 11-03 (playbook):** uses anchor IDs verbatim for `PLAYBOOK.md` MCP scripts +
  controls table (per CONTEXT D-claude-2 / D-11).
- **Plans 11-09 / 11-10 (first build + named bug fix):** UAT contract defines the 4
  surfaces the named bug must restore; bug-naming target derives from whichever
  anchor first fails at first build (RESEARCH §"Open Questions" 1/2 + D-13
  candidates a–e).
- **Plan 11-11 (anchors 1+2):** writes screenshots to
  `.planning/phases/11-.../evidence/uat-screenshots/anchor{1,2}-*.png`; binding
  evidence per Visual Evidence Rule.
- **Plan 11-12 (anchor 4):** uses GBST round-trip pattern locked here; writes
  `evidence/anchor4-sram-persistence.txt` + `evidence/anchor4-pre-reboot.gbst`.
- **Plan 11-13 (anchor 3):** writes `evidence/anchor3-cartridge-byte.txt` containing
  the ROM-byte hex dump from the built `banks.gb`.
- **Plan 11-14 (phase close):** flips per-anchor `Result: pending` → `pass` / `fail`
  and aggregates into the phase-close artifact.

No blockers.

## Self-Check: PASSED

- File `gbkt-examples/banks/11-UAT.md` exists — FOUND
- Commit `73e31fb7` (`docs(11-02): lock 4-anchor UAT contract for banks port`) in
  `git log` — FOUND
- All 7 acceptance literals present at expected counts — VERIFIED
- All 24 per-anchor fields present (Behavior / Evidence type / Evidence path /
  mcp_script / Expected / Result × 4 anchors) — VERIFIED
- Anchor-4 mechanism is GBST round-trip (not `emulator_stop`+`emulator_start`) —
  VERIFIED
- `key_links` pattern `emulator_save_state\(.*anchor4-pre-reboot` matches — VERIFIED

---
*Phase: 11-port-banks-gbdk-example-to-gbkt*
*Plan: 02 (uat-contract)*
*Completed: 2026-05-20*
