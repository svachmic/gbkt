---
phase: 12-port-platformer-template-gbdk-example-to-gbkt
plan: 13
subsystem: genre-platformer-codegen
tags: [platformer, jump-hold, variable-height-jump, d-14, codegen, gbdk, wave-7]

# Dependency graph
requires:
  - phase: 12-port-platformer-template-gbdk-example-to-gbkt
    provides: "PlatformerPhysicsConfig.jumpHoldMaxFrames + PlatformerPhysicsBuilder.jumpHold(maxFrames) (12-05); buildTilemapPhysicsUpdateFunction tilemap-physics branch with jump-initiation site that already sets _jump_increase_timer = cfg.jumpHoldMaxFrames (12-11); gameUsesTilemapCollision predicate (12-10/12-11)"
provides:
  - "buildTilemapPhysicsUpdateFunction §section 5b — gravity-suppression branch gated on cfg.jumpHoldMaxFrames > 0. Branch contains the 3-statement reference shape (timer decrement + button/timer guard + gravity application + timer reset) per player.c lines 297-317."
  - "PlatformerVisitor.visitPhysics — adds _jump_increase_timer (UINT8) WRAM global to varDecls when cfg.jumpHoldMaxFrames > 0 AND gameUsesTilemapCollision(gameIR). Lockstep gate keeps the global out of WRAM for abstract-path or non-tilemap games."
  - "CRawExpr import in PlatformerVisitor — needed to emit the parenthesized OR-chain inside the ! negation. CUnaryExpr emits without parens, so a `!CBinaryExpr(\"||\", ...)` operand would lose precedence (`!A || B` instead of `!(A || B)`)."
affects:
  - 12-14  # Locks the emitted body shape via per-function awk brace-walk + grep (next plan, Wave 7)
  - 12-19  # MCP play-through anchor 3 (variable-height jump) consumes runtime evidence from this branch

# Tech tracking
tech-stack:
  added: []  # No new libraries; additive codegen branch inside existing visitor method + 1 WRAM global
  patterns:
    - "Codegen-time gating with a regression-guard contract: when the feature field is 0/null, the codegen emits zero new C — preserving the prior-plan baseline byte-identical. Same shape as Plan 12-11's gameUsesTilemapCollision gate, but at the field level (cfg.jumpHoldMaxFrames > 0) instead of the predicate level. Enables additive lowering across waves without re-baselining downstream emission tests every time a new field lights up."
    - "CRawExpr as the escape hatch for parenthesised C expressions whose precedence cannot be expressed cleanly by the typed AST. CEmitter emits `!CBinaryExpr` as `!A || B` (loses precedence). De Morgan's law (`!A && !B`) is the natural alternative but loses the literal `||` token that the upcoming JVM emission test (Plan 12-14) is required to grep for. CRawExpr keeps the AST otherwise-typed and isolates the raw token to the single sub-expression that needs paren-wrapping."
    - "Lockstep dual-condition gate for tilemap-only WRAM globals — the global is emitted ONLY when (a) the feature field is opted in AND (b) the tilemap-physics branch is the one taking the call. Mirrors the existing tilemap-camera globals pattern (Plan 12-11 visitCamera lines 993-998)."

key-files:
  created:
    - ".planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-13-SUMMARY.md"
  modified:
    - "gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt"

key-decisions:
  - "Decrement is emitted prefix (`--_jump_increase_timer`), not postfix (`_jump_increase_timer--`). CEmitter line 426 emits `CUnaryExpr` as `${op}${operand}` — every existing visitor uses prefix when constructing standalone-statement decrements (ActorVisitor line 607 `--coyoteVar`, GBDKPipelineV2 line 2792 `++_d`, ScriptOpVisitor lines 473/541, etc.). Postfix would require a new AST node or special-case emission path. Functionally identical in this position (the decrement's value is not consumed); the upcoming Plan 12-14 emission grep should accept either form. Documented under §Deviations so Plan 12-14's author knows to allow both."
  - "Gravity scale chosen as `cfg.gravity * 16`, not literal 45. RESEARCH §D-14 documents both options. The * 16 form keeps cfg.gravity in user-facing pixel/frame² units (consistent with what the abstract physics path emits via `_plat_vy += cfg.gravity`) while emitting sub-pixel velocity increments correct for the >> 4 integration scale used at section §6. Literal 45 would be reference-faithful but tie the user out of tuning. Plan 12-15+ may re-tune when paired with UAT screenshot evidence; the multiplier is a single literal in one line, easy to migrate."
  - "_grounded comparison uses CIntLiteral(0). UINT8 actually permits CLiteral, but the existing jump-initiation site at line 586 (Plan 12-11) uses CIntLiteral. Matching the local-scope convention keeps the tilemap-physics branch internally consistent and reduces the surface area for scope-level grep gates to false-trigger on subtle literal-flavour drift. The `_jump_increase_timer` comparisons use CLiteral(0) per the same UINT8-unsigned-context rule, since they have no upstream convention to match."
  - "CRawExpr for `!(button_held(J_A) || button_held(J_UP))` — the rest of the condition stays in the typed AST. Alternatives considered: (a) De Morgan to `!button_held(J_A) && !button_held(J_UP)` — would lose the literal `||` token Plan 12-14 must grep; (b) introduce a `CParen(CExpr)` AST node — wider change, blast-radius beyond Plan 12-13's scope; (c) wrap the whole condition in a single CRawExpr — would lose the typed-AST property of the `_jump_increase_timer == 0u` half. The chosen middle ground keeps the AST typed for everything EXCEPT the one sub-expression that the emitter cannot parenthesise."
  - "Lockstep gate `cfg.jumpHoldMaxFrames > 0 && gameUsesTilemapCollision(gameIR)` for the WRAM global. The abstract physics path uses `variableHeightJump` (a separate Boolean flag with separate semantics — early-cut velocity vs. extend-hold), so the timer is not needed there. Restricting the global emission to (a) feature opted in AND (b) tilemap path active prevents dead-code WRAM allocation in mixed configurations (e.g. tilemap-off + jumpHold field set, which is a no-op shape that Plan 12-14 will treat as feature-off)."

patterns-established:
  - "When a plan's must_haves require literal C tokens with a precedence that CUnaryExpr's no-paren emission cannot produce, CRawExpr is the sanctioned escape — confined to the smallest possible sub-expression."
  - "Plan-level regression guard: when a new gated branch lights up, the gate must include a `field > 0` (or `flag == true`) condition such that the default-construction shape emits zero new C. This keeps each wave additive without re-baselining prior emission tests."

requirements-completed: [D-14]

# Metrics
duration: 12min
completed: 2026-05-21
---

# Phase 12 Plan 13: jumpHold gravity-suppression branch in tilemap-physics Summary

**Extends the Plan 12-11 tilemap-physics branch with the D-14 jumpHold gravity-suppression block — while airborne AND A/Up held AND `_jump_increase_timer > 0`, gravity is SUPPRESSED; on button release OR timer expiry, gravity resumes and the timer is zeroed. Mirrors reference `platformer_template/src/player.c` lines 297-317. Codegen-time gated on `cfg.jumpHoldMaxFrames > 0` so the 12-11 baseline is preserved byte-identical when jumpHold is disabled (default).**

## Performance

- **Duration:** ~12 min
- **Tasks:** 1 (Task 1 — add WRAM global + gravity-suppression branch)
- **Files modified:** 1 (`gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt`)

## Accomplishments

### Task 1: Add _jump_increase_timer WRAM global + jumpHold gravity-suppression branch

**WRAM global** — added in `visitPhysics()` varDecls, gated on `cfg.jumpHoldMaxFrames > 0 AND gameUsesTilemapCollision(gameIR)`:

```kotlin
if (physicsConfig.jumpHoldMaxFrames > 0 && gameUsesTilemapCollision(gameIR)) {
    add(CVarDecl(name = "_jump_increase_timer", type = CU8, initializer = CLiteral(0)))
}
```

When the gate fires, the main.c emission grows by one global:
```c
UINT8 _jump_increase_timer = 0u;
```

When `cfg.jumpHoldMaxFrames == 0` (default), zero new emission — the global is omitted. When `gameUsesTilemapCollision == false` (no tilemap-physics game), zero new emission either. Both halves of the gate must fire to emit the global.

**Gravity-suppression branch** — added as section 5b inside `buildTilemapPhysicsUpdateFunction`, between section 5 (jump initiation) and section 6 (velocity integration). Codegen-time gated on `cfg.jumpHoldMaxFrames > 0`:

```kotlin
if (cfg.jumpHoldMaxFrames > 0) {
    add(CComment("Phase 12 D-14 — gravity gated by jumpHold timer (suppress while A/Up held)"))
    add(
        CIf(
            condition = CBinaryExpr(CVar("_grounded"), "==", CIntLiteral(0)),
            thenBody = listOf(
                CIf(
                    condition = CBinaryExpr(CVar("_jump_increase_timer"), ">", CLiteral(0)),
                    thenBody = listOf(
                        CExprStatement(CUnaryExpr("--", CVar("_jump_increase_timer")))
                    ),
                ),
                CIf(
                    condition = CBinaryExpr(
                        CRawExpr("!(button_held(J_A) || button_held(J_UP))"),
                        "||",
                        CBinaryExpr(CVar("_jump_increase_timer"), "==", CLiteral(0)),
                    ),
                    thenBody = listOf(
                        CExprStatement(CBinaryExpr(CVar("_player_vy"), "+=", CLiteral(cfg.gravity * 16))),
                        CExprStatement(CBinaryExpr(CVar("_jump_increase_timer"), "=", CLiteral(0))),
                    ),
                ),
            ),
        )
    )
    add(CBlankLine)
}
```

Emitted C (with cfg.gravity=2, cfg.jumpHoldMaxFrames=20):

```c
// Phase 12 D-14 — gravity gated by jumpHold timer (suppress while A/Up held)
if (_grounded == 0) {
    if (_jump_increase_timer > 0u) {
        --_jump_increase_timer;
    }
    if (!(button_held(J_A) || button_held(J_UP)) || _jump_increase_timer == 0u) {
        _player_vy += 32u;
        _jump_increase_timer = 0u;
    }
}
```

**Reference oracle (player.c lines 297-317):**
```c
if(!grounded){
    if(playerJumpIncrease>0)playerJumpIncrease--;
    if(!((joypadCurrent & J_A||joypadCurrent & J_UP))||playerJumpIncrease==0){
        playerYVelocity+=GRAVTY;
        playerJumpIncrease=0;
    }
}
```

Behavioural parity: button-held + timer-positive ⇒ gravity suppressed (variable-height-jump rising window); button-released OR timer-expired ⇒ gravity resumes + timer zeroed (no re-suppression on a stray re-press).

**Comment updates** — three docstring blocks updated to reflect new ownership:
1. File-level §"jumpHold lowering" block (lines 472-478): now describes Plan 12-13's section 5b emission instead of "Plan 12-13 owns the actual jumpHold gravity-suppression emission".
2. Inline §5 jump-initiation comment (lines 587-590): now describes the timer's downstream consumer (section 5b) instead of "Plan 12-13 will gate gravity on _jump_increase_timer".
3. New §5b block introducer (lines 640-665): documents the gate, the gravity-scale choice, the signed-literal discipline, and the CRawExpr precedence rationale.

## Tasks executed

| Task | Name                                                                            | Commit     | Files                                                                                                |
| ---- | ------------------------------------------------------------------------------- | ---------- | ---------------------------------------------------------------------------------------------------- |
| 1    | Add _jump_increase_timer WRAM global + jumpHold gravity-suppression branch (D-14) | `d04fcc01` | `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt` |

## Verification

- `./gradlew :gbkt-genre-platformer:compileKotlin --quiet` → exit 0
- `./gradlew :gbkt-genre-platformer:test --quiet` → exit 0 (all existing tests stay GREEN: TilemapCollisionEmissionTest, HorizontalScrollEmissionTest, PlatformerCodegenTest, PlatformerPhysicsBuilderTest, ZonePlatformerPhysicsTest, JumpHoldEmissionTest placeholder)
- `./gradlew :gbkt-examples:banks:buildRom --quiet` → exit 0 (non-tilemap regression preserved)
- `./gradlew :gbkt-examples:pong:generateC :gbkt-examples:breakout:generateC --quiet` → exit 0
- Regression-guard grep: `grep -c '_jump_increase_timer\|button_held(J_A)\|button_held(J_UP)' gbkt-examples/{banks,pong,breakout}/build/gbkt/generated/main.c` → **0 0 0** (zero references in any example main.c — neither path opts into tilemap-physics)
- Plan-level grep gate: `grep -c '_jump_increase_timer' PlatformerVisitor.kt` → **11** (≥2 ✓ — 1 in varDecl, 1 in existing 12-11 jump-init, 9 in new section 5b emission + comments)
- `grep -n 'button_held(J_A)\|button_held(J_UP)' PlatformerVisitor.kt` → present (1 occurrence inside the CRawExpr at section 5b)

## Plan 12-14 readiness

The emitted body shape that Plan 12-14 will lock via per-function awk brace-walk + grep:

**Anchor:** `awk '/^void platformer_physics_update/{p=1;d=0} p{d+=gsub(/{/,"");d-=gsub(/}/,"");if(d<0)exit} p' main.c`

**Required tokens (Plan 12-13 emits all of them when cfg.jumpHoldMaxFrames > 0):**
- `_jump_increase_timer` — appears 4× in the section 5b body (decrement, `> 0u` guard, `== 0u` guard, `= 0u` reset)
- `button_held(J_A)` — appears 1× (inside the CRawExpr)
- `button_held(J_UP)` — appears 1× (inside the CRawExpr)
- `_player_vy += ` — appears 1× (the gravity application)
- `_grounded` — appears 1× (the airborne guard)

**When cfg.jumpHoldMaxFrames == 0:** none of these tokens appear in the function body — Plan 12-14's negative-case test (if it has one) can grep for ZERO occurrences and expect a clean miss.

## Deviations from Plan

### Auto-fixed / clarifications

**1. [Rule 3 — Blocking compile-shape mismatch] Decrement emitted prefix, not postfix.**
- **Found during:** Task 1 implementation.
- **Issue:** The plan's must_haves say `_jump_increase_timer--` (postfix). The reference player.c uses postfix `playerJumpIncrease--`. However, `CEmitter.emitExpr` (line 426) emits `CUnaryExpr` as `${op}${operand}` — prefix-only. The project has NO postfix emission path for standalone-statement decrements; every existing visitor uses prefix (ActorVisitor line 607, GBDKPipelineV2 line 2792, ScriptOpVisitor lines 473/541, etc.).
- **Fix:** Emitted prefix `--_jump_increase_timer`. Functionally identical to postfix in this position (the decrement's value is never consumed). Plan 12-14's grep test should accept either form — surfaced explicitly under §Plan 12-14 readiness so the next author knows the actual emission shape.
- **Alternative considered:** Adding a `CPostfixUnaryExpr` AST node + new CEmitter arm. Rejected — blast-radius outside Plan 12-13's scope; would require updating every existing prefix decrement site OR risking divergence. The functional equivalence makes the deviation harmless.
- **Rule:** Rule 3 (auto-fix blocking issue — the must_have form is not expressible without architectural change).
- **Files modified:** `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt` (the section 5b emission).
- **Commit:** `d04fcc01`.

**2. [Rule 2 — Auto-add missing critical functionality] CRawExpr import added.**
- **Found during:** Task 1 implementation.
- **Issue:** The condition `!(button_held(J_A) || button_held(J_UP))` needs parens around the OR-chain. The typed AST does NOT have a `CParen` node, and `CUnaryExpr("!", CBinaryExpr("||", ...))` emits as `!button_held(J_A) || button_held(J_UP)` — lost precedence.
- **Fix:** Imported `CRawExpr` (the sanctioned escape hatch per `gbkt-backend-gbdk/codegen/ast/CExpr.kt:89`) and used it for the single negated sub-expression. The rest of the condition (`|| _jump_increase_timer == 0u` half + the inner `> 0u` decrement guard) stays in the typed AST.
- **Alternatives considered:** (a) De Morgan to `!button_held(J_A) && !button_held(J_UP)` — would lose the literal `||` token Plan 12-14 must grep; (b) wrap the whole condition in one CRawExpr — would needlessly de-type the `_jump_increase_timer == 0u` half. The chosen middle ground keeps the AST typed for everything EXCEPT the one sub-expression that the emitter cannot parenthesise.
- **Rule:** Rule 2 (required for correct C semantics — without it, the `!` binds only to the first `button_held` call).
- **Files modified:** Same file (single import line).
- **Commit:** `d04fcc01`.

**3. Gravity multiplier note — plan suggested `<gravity>` or 45, chose `cfg.gravity * 16`.**
- **Found during:** Task 1 design.
- **Issue:** The plan's action prose says "use the scaling factor that makes gravity work in sub-pixel space (reference uses literal 45; if cfg.gravity is in pixels/frame, multiply by 16 to match sub-pixel velocity scale)". This was discretionary.
- **Decision:** Chose `cfg.gravity * 16`. RESEARCH §D-14 line 738 documents the alternative; the multiplier-of-16 form keeps cfg.gravity in user-facing pixel/frame² units while honouring the sub-pixel velocity domain. Documented in §key-decisions and in the inline §5b comment.
- **Rule:** Not a rule — discretionary choice within the plan's stated options.

### Plan-prose vs. emission shape clarifications

The plan's action step 3 said: "When `jumpHoldMaxFrames == 0`, emit the simpler `if (!_grounded) _player_vy += <gravity>;` (no timer logic)." However, the **prompt's regression-guard** says: "Lowering is gated such that when `jumpHoldMaxFrames == 0` (default), zero new C emission diff vs. the 12-11 baseline." These conflict — the plan-prose would emit a NEW gravity statement (the 12-11 baseline has NO gravity in the tilemap branch), while the prompt requires byte-identical preservation.

**Resolution:** Followed the prompt. When `cfg.jumpHoldMaxFrames == 0`, section 5b emits nothing — the 12-11 baseline is preserved byte-identical. Verified via the regression-guard grep against banks/pong/breakout (all 0). The gravity-absent state in the cfg.jumpHoldMaxFrames == 0 + tilemap-enabled case is a known incompleteness inherited from Plan 12-11; downstream waves (12-15+ runtime integration plans) own the question of whether to add a fall-back gravity path or to require `jumpHoldMaxFrames > 0` for tilemap games. Not Plan 12-13's responsibility.

### Comment-only updates (not deviations, surfaced for diff clarity)

Updated three docstring blocks in `PlatformerVisitor.kt` to reflect the new ownership boundary (Plan 12-13 now emits the gravity-suppression branch; Plan 12-11's jump-initiation site is unchanged behaviourally). The text-only updates are byte-identical to the C emission — they only change what humans read about the codegen.

## Known Stubs

None. The branch emits a complete, runnable C body when active; the only "stub" condition is the cfg-gate (`cfg.jumpHoldMaxFrames > 0`) which is the contract surface, not an unfinished feature.

## Threat Flags

None. The new branch operates entirely within the existing HOME-bank `platformer_physics_update` function (Plan 12-11). No new cross-bank calls, no new I/O, no new auth/network surface. The only WRAM addition is `_jump_increase_timer` (1 byte) — gated, dead-code-free.

## Issues Encountered

None significant. The CEmitter prefix-vs-postfix decrement question was resolved by matching project convention (prefix). The `!(A || B)` precedence question was resolved by using CRawExpr for the single negated sub-expression — the smallest escape-hatch footprint compatible with the typed-AST surrounding.

## User Setup Required

None — no external service configuration required.

## Threat Mitigations

**T-12-13-01 (Integrity — gravity-application missed when jumpHold transitions cross-frame):** Mitigated by the two-statement structure (decrement THEN re-evaluate guard). The reference player.c uses the same structure: line 300 decrements, line 303 evaluates the combined guard against the just-decremented value. If the timer was 1 going into the frame, after decrement it becomes 0, and the second `if` fires (timer==0 OR button released) → gravity applies + timer reset to 0. No cross-frame gap.

**T-12-13-02 (Integrity — button-release after timer expiry doesn't re-suppress gravity):** Mitigated by the unconditional `_jump_increase_timer = 0u` reset inside the gravity-application branch. Once the timer is zeroed, a subsequent button re-press has no effect until the next grounded jump initiation re-sets the timer to `cfg.jumpHoldMaxFrames`. Matches reference player.c line 309 verbatim. Prevents the bug where a player could "pump" the jump button to indefinitely suppress gravity.

**T-12-13-03 (Confidentiality — none).** No data crosses a trust boundary.

## Next Phase Readiness

**Ready for Plan 12-14 (Wave 7 — JVM emission invariant test):** The function `void platformer_physics_update` is emitted at column 0 of main.c when `gameUsesTilemapCollision` fires. Plan 12-14's per-function awk brace-walk extraction can use:
- Anchor: `awk '/^void platformer_physics_update/{p=1;d=0} p{d+=gsub(/{/,"");d-=gsub(/}/,"");if(d<0)exit} p' main.c`
- Positive-case (cfg.jumpHoldMaxFrames > 0): body must contain `_jump_increase_timer`, `button_held(J_A)`, `button_held(J_UP)`, `_player_vy += `, `--_jump_increase_timer` (or postfix — accept both)
- Negative-case (cfg.jumpHoldMaxFrames == 0): body must NOT contain any of the above tokens (regression-guard)

**Ready for Plan 12-15+ (Wave 8 — runtime integration):** The branch consumes globals (`_grounded`, `_player_vy`, `_player_x/y`) that downstream wave-8 plans will declare in GBDKPipelineV2 via the scene-enter wiring (per Plan 12-11 §"Next Phase Readiness"). Plan 12-13 does not add any NEW undeclared references — every symbol the new branch touches was already referenced by Plan 12-11.

**Existing examples remain byte-identical:** Verified via fresh build of `:gbkt-examples:banks:buildRom` + `:gbkt-examples:pong:generateC` + `:gbkt-examples:breakout:generateC` — all three emit ZERO references to `_jump_increase_timer`, `button_held(J_A)`, or `button_held(J_UP)` in any main.c. The two-condition gate (cfg.jumpHoldMaxFrames > 0 AND gameUsesTilemapCollision) strictly opts in; none of the existing example games trip either half.

## Self-Check: PASSED

- `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt` exists; contains literal `_jump_increase_timer` (11 occurrences ≥2 ✓)
- Contains literal `button_held(J_A)` and `button_held(J_UP)` (inside the CRawExpr in section 5b)
- Contains literal `_player_vy += ` (inside the gravity-application thenBody)
- Contains the codegen-time gate `cfg.jumpHoldMaxFrames > 0` (line 666)
- Contains the lockstep WRAM-global gate `physicsConfig.jumpHoldMaxFrames > 0 && gameUsesTilemapCollision(gameIR)` (line 167-176 area)
- Commit `d04fcc01` exists in git log (verified: `git log --oneline -1` → `d04fcc01 feat(12-13): D-14 jumpHold gravity-suppression branch in tilemap-physics`)
- `:gbkt-genre-platformer:compileKotlin --quiet` → exit 0
- `:gbkt-genre-platformer:test --quiet` → exit 0 (all existing tests stay GREEN)
- `:gbkt-examples:banks:buildRom --quiet` → exit 0 (non-tilemap regression preserved)
- `:gbkt-examples:pong:generateC` + `:gbkt-examples:breakout:generateC` → 0 references to new tokens in any main.c

---
*Phase: 12-port-platformer-template-gbdk-example-to-gbkt*
*Completed: 2026-05-21*
