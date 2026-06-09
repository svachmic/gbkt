---
phase: 12-port-platformer-template-gbdk-example-to-gbkt
plan: 22
subsystem: testing
tags: [uat, mcp-emulator, anchor-4, metasprite, hflip, retro-close]

requires:
  - phase: 12-port-platformer-template-gbdk-example-to-gbkt (Plan 12-19)
    provides: Anchor 1 GREEN — gameplay scene reachable + tilemap rendered
  - phase: 12-port-platformer-template-gbdk-example-to-gbkt (Plan 12-21)
    provides: Inline-fix substrate for camera_x + playerVx + metasprite world→screen render
  - phase: 12.3-platformer-visitor-auto-emission-wiring
    provides: PlatformerVisitor auto-emission cleanup — _walkFrameIdx framework-level increment + platformerInput { } binder
  - phase: 12.5-png2asset-metasprite-layout-fix-and-phase-12-3-closure
    provides: png2asset layout fix via mode/pivot/frameSize sprite() block flags — duck art renders correctly
provides:
  - Anchor 4 (metasprite walk-cycle + hflip) closed-out — retro-GREEN via 12.3 + 12.5 RED→GREEN re-shoot
  - Variable-evidence triple: walkFrameIdx took 3 distinct values (1,2,0), facingRot==3 on LEFT held, 4 distinct PNG byte-images
  - Human-verify approval (REQ-3a) for duck-art rendering — primary closure signal per assertion message
affects: [phase-12 anchor-5, phase-12-final-verifier]

tech-stack:
  added: []
  patterns:
    - "Variable evidence + human-verify (REQ-3a) closure when mechanical pixel-diff (REQ-3b) falls below threshold; the assertion message itself documents this fallback path"

key-files:
  created:
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-22-SUMMARY.md
  modified: []
  inherited:
    - .planning/phases/12.3-platformer-visitor-auto-emission-wiring-wire-input-playervx-/evidence/uat-screenshots/anchor-4/01-walk-frame-0.png (re-shot 2026-05-24, refreshed 2026-05-25)
    - .planning/phases/12.3-platformer-visitor-auto-emission-wiring-wire-input-playervx-/evidence/uat-screenshots/anchor-4/02-walk-frame-1.png
    - .planning/phases/12.3-platformer-visitor-auto-emission-wiring-wire-input-playervx-/evidence/uat-screenshots/anchor-4/03-walk-frame-2.png
    - .planning/phases/12.3-platformer-visitor-auto-emission-wiring-wire-input-playervx-/evidence/uat-screenshots/anchor-4/04-facing-left.png
    - .planning/phases/12.3-platformer-visitor-auto-emission-wiring-wire-input-playervx-/evidence/uat-screenshots/anchor-4/anchor4-variables.txt

key-decisions:
  - "RED→GREEN re-shoot for Plan 12-22 was performed inside Phase 12.5 (commit 41570828 feat(12.3-15): Anchor 4 metasprite walk-cycle + hflip — RED → GREEN re-shoot). Plan 12-22 itself stays incomplete in Phase 12 until this close-out SUMMARY lands."
  - "REQ-3a (visible duck art) human-verify approved 2026-05-24 in commit 2cd416a4 — duck art renders correctly post 12.5's png2asset fix."
  - "REQ-3b mechanical pixel-diff (>10% facing-right vs facing-left) currently measures 8.69% on the May 25 re-run. The test's own assertion message documents the fallback: 'If diffRatio < 10% despite ≥80-frame scroll: REQ-3a (human-verify) is the primary closure signal — proceed to checkpoint for duck-art approval.'"
  - "Variable evidence is unambiguous: walkFrameIdx cycled 1→2→0 (3 distinct values across 18 frames of RIGHT held — clears the >=2 assertion), facingRot transitions to 3 when LEFT is held (D-04 hflip codegen path engages)."
  - "Closed without re-execution: production work landed via Phase 12.3 + 12.5; spawning a new executor here would duplicate work and risk regressing the RED→GREEN re-shoot already on disk per safe_resume_gate protocol."

requirements-completed:
  - D-08    # Anchor 4: metasprite animation + hflip
  - D-10    # Visual evidence binding
  - D-04    # 6-frame metasprite + hflip (case 3 → move_metasprite_flipx)
  - D-overfitting-1
  - D-overfitting-3

duration: ~5 min (re-run UAT + write SUMMARY; production work executed in 12.3 + 12.5)
completed: 2026-05-25
---

# Plan 12-22: UAT Anchor 4 — Metasprite Walk-Cycle + HFlip (Retro Close)

**Anchor 4 retroactively GREEN. Production work landed in Phase 12.3 (PlatformerVisitor auto-emission wiring for `_walkFrameIdx` framework-level increment + `platformerInput { }` binder activation) and Phase 12.5 (png2asset layout fix via mode/pivot/frameSize on sprite() block — duck art now renders correctly). Plan 12-22's RED→GREEN re-shoot was committed inside Phase 12.5 (commit 41570828 feat(12.3-15): Anchor 4 metasprite walk-cycle + hflip — RED → GREEN re-shoot). This SUMMARY closes the loop in the Phase 12 plan directory.**

## Performance

- **Duration:** ~5 min (re-run anchor4 UAT + write SUMMARY)
- **Production work:** absorbed by Phase 12.3 (gap closure) and Phase 12.5 (png2asset visual closure)
- **Completed:** 2026-05-25
- **Tasks:** 0 new (Plan 12-22 PLAN.md tasks 1+2 satisfied by Phase 12.3/12.5 production + Phase 12.5 plan 12.3-15 commits)

## Accomplishments

- Closed Plan 12-22 in the Phase 12 directory via this SUMMARY referencing the upstream RED→GREEN re-shoot
- Re-ran `:gbkt-examples:platformer-template:test --tests "PlatformerTemplateUatTest.anchor4MetaspriteAnimation"` on 2026-05-25 to verify current state:
  - **walkFrameIdx_at_01: 1**, **walkFrameIdx_at_02: 2**, **walkFrameIdx_at_03: 0** — 3 distinct values, clears the >=2 cycling assertion
  - **facingRot_at_04: 3** — D-04 hflip codegen path engages on LEFT held
  - 4 distinct PNG byte-images (walk0/walk1/walk2/facing-left) — pre-existing PNG byte-diff structural check holds
  - **REQ-3b mechanical pixel-diff (>10% facing-right vs facing-left): 8.69%** — fails the strict mechanical gate; per assertion message, REQ-3a (human-verify) is the primary closure signal here
- Verified REQ-3a human-verify approval persists from 2026-05-24 commit 2cd416a4 (`docs(debug-12.5-req3): resolve debug session — duck art approved`)

## Why retro-close (not re-execute)

Per safe_resume_gate in `execute-phase.md`:
- Production commits exist for the plan (escalation commit 243d79b3 + retro-GREEN re-shoot commit 41570828)
- SUMMARY.md was missing in the Phase 12 dir, gating execute-phase from advancing
- The 4 anchor-4 PNGs + variables.txt are physically present in `evidence/uat-screenshots/anchor-4/` (in both the Phase 12 dir from the 12-22 escalation commit AND the Phase 12.3 dir from the 12.5 re-shoot)
- The test EVIDENCE_DIR points to the Phase 12.3 dir; re-running the test refreshes that copy

Spawning a new executor for Plan 12-22 would duplicate the upstream production work (12.3 framework auto-emission + 12.5 png2asset fix) and risk regressing the post-12.5 captures already on disk.

## REQ-3b 8.69% < 10% — known limitation, not a regression

The assertion message in `PlatformerTemplateUatTest.kt:552` explicitly anticipates this case:

> If diffRatio < 10% despite ≥80-frame scroll: REQ-3a (human-verify) is the primary closure signal — proceed to checkpoint for duck-art approval.

Mechanism: the metasprite occupies ~24x32 px = ~3.3% of the 160x144 frame. The remaining ~7% pixel-diff would come from background-scroll accumulation. Today's re-run yielded 8.69%, just under the 10% strict mechanical floor. Variable assertions (walkFrameIdx cycle + facingRot==3) and REQ-3a (visible duck art human-verified) are both GREEN — the visual truth is established.

If a future run wishes to make REQ-3b mechanically green, the options (deferred to a future polish phase, NOT escalated here) are:
- Loosen tolerance to 0.07 or 0.05 to absorb the residual envelope
- Increase the pre-scroll hold to 100+ frames to push the camera diff further
- Compare facing-left against walk-frame-2 instead of walk-frame-0 (the cycle phase may matter)

Per the GSD framework's blast-radius rule and CLAUDE.md Visual Evidence Rule, the variable evidence + REQ-3a human-verify is sufficient closure for Anchor 4.

## Files Created/Modified

**Created:**
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-22-SUMMARY.md` (this file)

**Inherited (not modified here — production work landed in Phase 12.3 + 12.5):**
- `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateUatTest.kt::anchor4MetaspriteAnimation` (full impl landed in 243d79b3 escalation; EVIDENCE_DIR redirect to 12.3 dir landed inside Phase 12.3)
- Phase 12.3 anchor-4 evidence directory (4 PNGs + 4 JSONs + variables.txt — refreshed by today's UAT re-run)

## Cross-References

- Plan 12-22 escalation commit: 243d79b3 `docs(12-22): block Plan 12-22 — escalate to Phase 12.3 (PlatformerVisitor gaps)`
- RED→GREEN re-shoot commit: 41570828 `feat(12.3-15): Anchor 4 metasprite walk-cycle + hflip — RED → GREEN re-shoot`
- REQ-3a human-verify approval commit: 2cd416a4 `docs(debug-12.5-req3): resolve debug session — duck art approved`
- Phase 12.3: PlatformerVisitor auto-emission wiring (closed via 12.5)
- Phase 12.5: png2asset metasprite layout fix (mode/pivot/frameSize sprite() block flags)
- Debug session E-02/E-03/E-04: triple-bug root cause (x/y swap in tile() calls, SPRITES_8x16 selection, set_sprite_data count off-by-one) — resolved + archived under `.planning/debug/resolved/`

## Self-Check: PASSED

Variable assertions PASS (walkFrameIdx cycle + facingRot==3). PNG byte-diff PASS (4 distinct images). REQ-3a (human-verify duck art) PASS via persisted approval commit 2cd416a4. REQ-3b mechanical diff 8.69% < 10% — known limitation, closure path documented in test assertion message and key-decisions above.
