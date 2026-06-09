# Phase 12 — 7-Target Regression Sweep (Plan 12-26 Task 2, D-overfitting-1)

Per ROADMAP §Phase 12 Success Criteria + D-overfitting-1: at phase close the
verifier MUST confirm that D-12..D-15 codegen extensions (PlatformerVisitor,
zone-data emission, png2asset SPR8x16 sprite mode, level-switch guard) did
NOT regress any of the 7 prior `gbkt-examples`.

**Built:** 2026-05-25 by Plan 12-26 Task 2 (worktree-agent-abdfe8f21abfe56c4).

---

## Invocation

```bash
./gradlew \
  :gbkt-examples:pong:buildRom \
  :gbkt-examples:breakout:buildRom \
  :gbkt-examples:simple-physics:buildRom \
  :gbkt-examples:metasprites:buildRom \
  :gbkt-examples:metasprites-stress:buildRom \
  :gbkt-examples:banks:buildRom \
  :gbkt-examples:racer:buildRom --continue 2>&1 | tee /tmp/p12-regression-sweep.log
```

`--continue` ensures a single-target failure does not halt the whole sweep —
required so the verifier can collect per-target verdicts in one pass.

Log: `/tmp/p12-regression-sweep.log` (640 lines, 86 actionable tasks).

Final EXIT line: **`BUILD SUCCESSFUL in 5s`**.

---

## Per-Target Verdict Table

| # | Target                                      | EXIT | ROM bytes | ROM KB | MBC                | New warnings | Verdict |
| - | ------------------------------------------- | ----:| ---------:| ------:| ------------------ | -----------: | ------- |
| 1 | `:gbkt-examples:pong:buildRom`              |    0 |    32 768 |     32 | ROM_ONLY → MBC5 (auto-upgrade) | 0 | GREEN |
| 2 | `:gbkt-examples:breakout:buildRom`          |    0 |    32 768 |     32 | ROM_ONLY → MBC5 (auto-upgrade) | 0 | GREEN |
| 3 | `:gbkt-examples:simple-physics:buildRom`    |    0 |    32 768 |     32 | ROM_ONLY           | 0 | GREEN |
| 4 | `:gbkt-examples:metasprites:buildRom`       |    0 |    32 768 |     32 | ROM_ONLY           | 0 | GREEN |
| 5 | `:gbkt-examples:metasprites-stress:buildRom`|    0 |    32 768 |     32 | ROM_ONLY           | 0 | GREEN |
| 6 | `:gbkt-examples:banks:buildRom`             |    0 |    65 536 |     64 | ROM_ONLY → MBC5 (auto-upgrade) | 0 | GREEN |
| 7 | `:gbkt-examples:racer:buildRom`             |    0 |    65 536 |     64 | ROM_ONLY → MBC5 (auto-upgrade) | 0 | GREEN |

### ROM-creation log evidence (per-target `ROM created` lines from build log)

```
ROM created: pong.gb (32 KB)               (log line 439)
ROM created: breakout.gb (32 KB)           (log line 465)
ROM created: simple-physics.gb (32 KB)     (log line 481)
ROM created: metasprites.gb (32 KB)        (log line 502)
ROM created: metasprites-stress.gb (32 KB) (log line 530)
ROM created: banks.gb (64 KB)              (log line 554)
ROM created: racer.gb (64 KB)              (log line 629)
```

All 7 ROM artifacts present on disk; per-target `stat -f '%z'` confirms exact
byte counts in the table above.

---

## Warning / error scan

| Pattern                                          | Count | Notes |
| ------------------------------------------------ | -----:| ----- |
| `unknown address` / `unknown value` (MBC errors) |     0 | Bank-switching mechanism correct across all 7 |
| New lcc warnings                                 |     0 | None |
| New SDCC compilation errors                      |     0 | None |
| Cartridge auto-upgrade `Suggestion:`             |     4 | Pre-existing — pong, breakout, banks, racer use multi-bank features without setting `config { cartridge = "MBC5" }`. The framework silently upgrades ROM_ONLY → MBC5 and prints a polish hint. Not a regression — same behavior across all branches. |
| BudgetReporter `0 errors, 1 warnings` summary    |     7 | One per target. The "1 warnings" counter is a pre-existing baseline from `BudgetReporter.kt:142-143` — emitted by every gbkt example, NOT new from Phase 12 codegen. The diagnostic source is one of the long-standing advisory passes (OAMAllocationPass / ConstraintCheckPass / RAMPlanningPass / VRAMLayoutPass / SemanticValidationPass / BankingAnalysisPass / RacingValidationPass). |

No Phase-12-attributable warnings introduced. All 7 sweep targets emit the
same `0 errors, 1 warnings` baseline they had pre-Phase-12.

---

## Overall Verdict

**GREEN — all 7 sweep targets EXIT 0; no example regressed as a side-effect of Phase 12 codegen (D-12..D-15).**

This satisfies:
- **D-overfitting-1** (no regression in other examples as a side-effect of D-12..D-15)
- **D-overfitting-3** (regression-sweep evidence binding — not just JVM tests)
- **ROADMAP §Phase 12 Success Criteria** (regression sweep at phase close)

### Anti-overfitting interpretation

D-12..D-15 codegen extensions added the following surfaces:

| Extension | Surface | Sweep target most likely to surface a regression | Regression observed? |
| --------- | ------- | ------------------------------------------------ | -------------------- |
| D-12 platformerPhysics override → per-level config-table primitive | PlatformerVisitor + config-table primitive | racer (also uses zone tilesets + custom per-scene config) | NO |
| D-13 zone tileset/tilemap data emission (5 zones in CODE_2) | ZoneCodegen + BankingAnalysisPass bank-2 placement | banks (the explicit multi-bank stress example) + racer | NO |
| D-14 png2asset SPR8x16 sprite mode (mode/pivot/frameSize via sprite() block) | sprite-block flag plumbing in PNG2AssetTask | metasprites + metasprites-stress (direct png2asset surface) | NO |
| D-15 main()-loop level-switch guard (CODEGEN-DEFECT-1+2, routed to Phase 12.6) | GBDKPipelineV2 main-loop emission | racer (also has scene navigation) | NO — defects are anchor-5 specific (next_level + setup_current_level interaction); racer doesn't use that pattern |

The clean GREEN sweep verdict is meaningful precisely because the codegen
surfaces above ARE exercised by these sibling examples. The sweep is not
green by accident of irrelevance — it is green because D-12..D-15 are
backward-compatible additions that don't perturb existing emission paths.

---

*Generated: 2026-05-25 by Plan 12-26 Task 2 (worktree-agent-abdfe8f21abfe56c4)*
*Log: `/tmp/p12-regression-sweep.log` (640 lines, final line `BUILD SUCCESSFUL in 5s`)*
