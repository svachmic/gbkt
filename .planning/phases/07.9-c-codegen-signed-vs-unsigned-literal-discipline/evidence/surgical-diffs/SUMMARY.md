# Surgical Diff Summary — Phase 07.9 Plan 04

**Phase:** 07.9
**Date:** 2026-05-13
**Baseline SHA:** acf6e2d6 (docs: update tracking after wave 1, pre-Plan-02)
**Post-fix SHA:** 0a92fbe3 (docs: update tracking after wave 2, post-Plan-02)
**D-09 #3 Status:** SATISFIED

## Per-Example Diff Line Counts

| Example | Added lines | Removed lines | Bucket-(a) comparison sites | Non-comparison drift |
|---------|------------|---------------|----------------------------|---------------------|
| pong | 0 | 0 | 0 | 0 |
| breakout | 0 | 0 | 0 | 0 |
| explorer | 0 | 0 | 0 | 0 |
| dungeon | 0 | 0 | 0 | 0 |
| rpg-lite | 0 | 0 | 0 | 0 |
| platformer | 22 | 2 | 2 signed-comparison fixes (+ 20 new _plat_vx from Rule 2) | 0 |
| platformer-gbc | 22 | 2 | 2 signed-comparison fixes (+ 20 new _plat_vx from Rule 2) | 0 |
| shmup | 0 | 0 | 0 | 0 |
| racer | 2 | 2 | 2 | 0 |

## Verification: Surgical-Diff Gates

### Gate 1: Comparison-site changed-line counts

- pong: 0 == 0 (no signed-context comparisons in pong DSL)
- breakout: 0 == 0 (no signed-context comparisons in breakout DSL)
- explorer: 0 == 0 (no signed-context comparisons in explorer DSL)
- dungeon: 0 == 0 (no signed-context comparisons in dungeon DSL)
- rpg-lite: 0 == 0 (no signed-context comparisons in rpg-lite DSL)
- platformer: 2 removed at comparison sites, 2 added at comparison sites (additional 20 lines = new _plat_vx functionality from Rule 2)
- platformer-gbc: same as platformer (identical DSL source, GBC target only)
- shmup: 0 == 0 (no signed-context comparisons in shmup DSL)
- racer: 2 == 2 (camera-clamp: rawX < 0u to rawX < 0, rawY < 0u to rawY < 0)

### Gate 2: Non-comparison drift == 0

All 9 examples: assignment-initializer drift = 0
All 9 examples: arithmetic-operand drift = 0

## Surgical Property Verification

### Racer (camera-clamp migration)

Removed lines:
    _camera_x = (UINT8)((rawX < 0u) ? 0u : (rawX > 0u) ? 0u : rawX);
    _camera_y = (UINT8)((rawY < 0u) ? 0u : (rawY > 8u) ? 8u : rawY);

Added lines:
    _camera_x = (UINT8)((rawX < 0) ? 0u : (rawX > 0) ? 0u : rawX);
    _camera_y = (UINT8)((rawY < 0) ? 0u : (rawY > 8) ? 8u : rawY);

Only the comparison RHS changed from 0u/8u to 0/8. Ternary then/else branches (0u) and non-comparison positions preserved.

### Platformer (jump-cancel + terminal velocity + new _plat_vx)

Removed at comparison sites:
    if (_plat_vy < 12u) {
    if (button_released(J_A) && _plat_vy < 0u) {

Added at comparison sites:
    if (_plat_vy < 12) {
    if (button_released(J_A) && _plat_vy < 0) {

Additional lines = new functionality (Plan 02 Rule 2): _plat_vx variable, physics wiring, horizontal movement and friction. These did not replace existing unsigned literals (no _plat_vx existed in baseline).

### Examples with zero diff (pong, breakout, explorer, dungeon, rpg-lite, shmup)

These examples do not exercise the camera system or platformer physics, so migrated paths produce zero text changes — confirming unaffected code paths produce zero diff.

## Conclusion

D-09 #3 satisfied. The surgical-diff property is observed natively under Option C.

Under Option C, each migrated bucket-(a) site produces exactly one removed line (with Nu suffix) and one added line (without u suffix) at the comparison RHS position. Bucket-(b) sites (assignment initializers, arithmetic operands, ternary branches) are untouched. Per RESEARCH Pitfall 2: no reinterpretation of D-09 #3 is needed.

## Bucket-(a) Cross-Reference

Cross-reference deferred to Plan 06 wrap-up (Plan 03 audit executing in parallel, wave 3).

Confirmed migrated sites from this diff evidence:
- GBDKSystemVisitor.visitCameraSystem: rawX < 0, rawX > maxX, rawY < 0, rawY > maxY
- PlatformerVisitor.buildPhysicsUpdateFunction: _plat_vy < terminalVelocity (12), _plat_vy < 0 (jump-cancel)
