# Phase 12.7 Round-6 H3 Diagnostic — Trigger Grounded-Blind Root Cause Lock

**Date:** 2026-05-26
**Round:** 6 (TERMINAL — per feedback_many_small_plans_terminal_subphase, NO Round 7)
**Predecessor:** Plan 12.7-21 SUMMARY (status=blocked; named two candidates, did not lock fix locus)
**Successor consumers:** Plan 12.7-27 (RED test), Plan 12.7-28 (GREEN fix)

## Question

Plan 12.7-21 SUMMARY surfaced two candidate root causes for the anchor-5/00-last-gameplay.png
real-airborne-state visible at trigger-fire frame:
  (a) Codegen: level-end trigger emission lacks a `_grounded` guard.
  (b) Test harness: `anchor5LevelSwitch` presses A periodically near the trigger zone.

Which is the load-bearing fix locus? This diagnostic answers that question entirely
at JVM-tier (no ROM run, no UAT run, no codegen mutation).

## Source 1 — Generated C emit-site (C-tier)

File: `gbkt-examples/platformer-template/build/gbkt/generated/main.c`
Line range: 600-603

Verbatim:
```c
    // Level-end trigger: increment _next_level when past the right margin
    if (player_real_x > _current_level_width - 32u) {
        ++_next_level;
    }
```

Finding: the C-tier emit shows the trigger CIf fires purely on horizontal position;
no `_grounded` reference appears in the condition or thenBody. The CIf body is a
single `++_next_level` statement with NO grounded guard whatsoever.

## Source 2 — Visitor IR emit-site (Kotlin tier)

File: `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt`
Line range: 1201-1220 (the `// --- 8. Level-end trigger` section in `buildTilemapPhysicsUpdateFunction`)

Verbatim:
```kotlin
                // --- 8. Level-end trigger (mirrors player.c line 351) ------------------------
                // Plan 12-17 may switch to a `goalZone`-based trigger (D-claude-6); this plan
                // ships the explicit threshold form which is simpler and matches the reference.
                add(CComment("Level-end trigger: increment _next_level when past the right margin"))
                add(
                    CIf(
                        condition =
                            CBinaryExpr(
                                CVar("player_real_x"),
                                ">",
                                CBinaryExpr(
                                    CVar("_current_level_width"),
                                    "-",
                                    CLiteral(levelEndRightMargin),
                                ),
                            ),
                        thenBody =
                            listOf(CExprStatement(CUnaryExpr("++", CVar("_next_level")))),
                    )
                )
```

Cross-reference: `groundedSym` is in scope at the outer function (lines 579-580):
```kotlin
        val groundedSym =
            "_" + ((tcSystem?.config?.get("groundedVar") as? String) ?: "grounded")
```

Finding: the visitor IR-tier emit produces the C-tier output exactly. `groundedSym`
is resolved in scope at lines 579-580 but is NOT referenced in the trigger CIf at
lines 1204-1220. The CIf has a single `CBinaryExpr(CVar("player_real_x"), ">", ...)` condition;
there is no conjunction with `groundedSym`. No plumbing is needed for the fix — `groundedSym`
is already in-scope; the fix is to add an `&& groundedSym != 0` conjunction on the condition.

## Source 3 — UAT harness loop (test-harness tier)

File: `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateUatTest.kt`
Line range: 715-718

Verbatim:
```kotlin
            while (framesHeld < maxTraversalFrames) {
                val buttons =
                    if ((framesHeld / 8) % 3 == 0) setOf(Button.RIGHT, Button.A)
                    else setOf(Button.RIGHT)
```

Finding: A is held for 8 frames out of every 24 (8 frames hold, 16 frames release, repeat).
This deterministically schedules a jump initiation roughly every 24 frames during
traversal. At frame 1327 (the trigger-fire frame per 12.7-21 sidecar), `(1327 / 8) % 3`
evaluates to `(165) % 3 = 0` — meaning A IS held at exactly the frame the trigger fires.
This confirms the player is mid-jump at the moment of trigger crossing.

## Source 4 — 12.7-21 sidecar cross-check

File: `.planning/phases/12.7-player-levitating-physics-codegen/evidence/uat-screenshots/anchor-5/00-last-gameplay.json`

Captured frame variables at frame 1327:
  - grounded: 0 (player is NOT grounded — genuinely airborne)
  - playerVy: 416 (positive = falling downward, sub-pixel units; real_vy = 416 >> 4 = 26 px/frame)
  - playerY (subpixel): 686; real_y = 686 >> 4 = 42 (player is 42 pixels from top — mid-screen)
  - playerX (subpixel): 7192; real_x = 7192 >> 4 = 449 (past right margin at level width ~480-32=448)
  - next_level: 1 (trigger already fired — incrementing from 0 to 1 happened THIS frame)
  - current_level: 0 (still on level 0 before main()-guard navigates to nextLevelScene)

Finding: confirms the player is genuinely airborne at trigger-fire (not a render
artifact). The visible levitation in 00-last-gameplay.png matches the runtime state.
`grounded=0` and `playerVy=416` (falling arc) proves the player is mid-jump.
`real_x=449` is past `_current_level_width - 32 = ~448` — the horizontal condition is
satisfied while the player is airborne. This is the exact H3 defect: trigger fires
on horizontal position alone, regardless of vertical/grounded state.

Additional corroboration: `(1327 / 8) % 3 = (165) % 3 = 0` — the harness A-press
condition is TRUE at frame 1327, confirming the harness did cause a jump at or just
before this frame. The harness is a compounding factor, not the root cause.

## Disambiguation Matrix

| Candidate                       | Disambiguating Signal                                                                                                                                    | Verdict                                                              |
|---------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------|
| (a) Codegen grounded-blind      | Source 1 lines 600-603 has NO `_grounded` check; Source 2 visitor CIf (lines 1204-1220) has no `groundedSym` reference though it is in-scope at line 579 | **CONFIRMED — load-bearing**                                         |
| (b) UAT harness periodic A      | Source 3 schedules A every 24 frames; at frame 1327 `(1327/8)%3=0` so A IS held -> jump -> real_y=42 mid-flight (Source 4: grounded=0, vy=416)            | COMPOUNDING (surfaces (a) deterministically but is NOT the bug)      |
| Load-bearing fix locus          | SPEC R-03 wording "player pinned to floor near right-edge trigger" is a player-facing game-design contract; even with A=off, a player can be airborne at trigger (run-off-platform, future knockback) | **(a) IS the fix locus**                                             |

## Verdict

**H3 root cause = CODEGEN defect.**

**Fix locus = `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt` lines 1204-1218 (the `// --- 8. Level-end trigger` CIf in `buildTilemapPhysicsUpdateFunction`).**

**Required emit shape:** the CIf condition MUST be extended with a `&& _grounded != 0`
conjunction (or equivalent — see Plan 12.7-28 for the exact CBinaryExpr shape). The
generated C MUST emit:
```c
    if (player_real_x > _current_level_width - 32u && _grounded != 0u) {
        ++_next_level;
    }
```
or equivalent precedence-safe form.

**Why NOT a harness fix:**
  1. The SPEC R-03 contract is player-facing game semantics, not a test-evidence
     convention. A runtime game with a grounded-blind trigger is incorrect regardless
     of how the test drives it.
  2. The harness's periodic A press is required for water-gap traversal earlier in
     the level; removing it would break the test's reachability of the trigger zone.
  3. A future hazard system that knocks the player airborne could re-surface the same
     class of bug. The fix MUST be in the game-design contract, not in any single test.

**Why codegen-fix IS load-bearing (the counterfactual argument):**
  Even if the UAT harness were changed to never press A near the trigger zone, a human
  player running at speed on a platform that ends near the right-edge threshold could
  run off the edge and be airborne (coyote-time window exhausted) at the moment their
  real_x crosses the threshold. The grounded-blind trigger would fire, advancing the
  level mid-air. This is a game-design correctness defect independent of the harness.

**Subordinate informational finding (NOT in scope for Phase 12.7 -- surface for future
test-quality improvement only):** the anchor5LevelSwitch loop could be defensively
tightened to release A and wait N grounded-true frames before reaching the trigger
zone. This would make the test more deterministic against future regressions of this
bug class. DO NOT plan this under Phase 12.7's terminal cluster; consider for a
future test-harness-hardening phase if the team ever pursues one.

## Terminal-Round Contract

Per CLAUDE.md project memory `feedback_many_small_plans_terminal_subphase.md`:
  - Round 6 (Plans 12.7-26..12.7-32) IS the terminal round for Phase 12.7.
  - NO Round 7 inside 12.7.
  - If Plan 12.7-31 BINDING gate fails, the resume signal MUST route to a sibling
    phase under parent 12 (e.g., `/gsd-phase --insert 12 <slug>` per
    `feedback_gsd_phase_insert_after_decimal` -- pass integer parent 12, not decimal
    12.7).

## Successor Plan References

  - **Plan 12.7-27** (W21) -- RED test: `LevelEndTriggerGroundedGuardEmissionTest` --
    asserts the trigger CIf condition contains `_grounded` (or its semantic equivalent).
  - **Plan 12.7-28** (W22) -- GREEN fix: extend the CIf condition with
    `CBinaryExpr(... outer condition ..., "&&", CBinaryExpr(CVar(groundedSym), "!=", CIntLiteral(0)))`.
    Plumb `groundedSym` from the outer function scope (already resolved at line 579-580).
  - **Plan 12.7-29** (W23) -- UAT re-capture (anchor-5 + anchor-2 regression-guard).
  - **Plan 12.7-30** (W24) -- R-04 regression sweep post-H3.
  - **Plan 12.7-31** (W25) -- BINDING human-verify gate (supersedes Plan 12.7-24).
  - **Plan 12.7-32** (W26) -- Ledger close + retro-supersession of Plans 12.7-22/24/25 (supersedes Plan 12.7-25).
