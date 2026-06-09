// Plan 07.4-34 Task 4 — POST-FIX camera shape extracted from
// gbkt-examples/racer/build/gbkt/generated/main.c via the same awk brace-walk
// pattern used to capture the baseline (file 03).
//
// Fix surface: gbkt-backend-gbdk/.../codegen/emit/CEmitter.kt — the CCast
// branch now delegates to emitCastInner(), which wraps its argument in
// parens when the inner is CTernary, CBinaryExpr, or CUnaryExpr. The cast
// now applies to the ENTIRE nested ternary, not just the inner comparison.
//
// Post-fix shape locked here (look at line 4-5 of the function body):
//   _camera_x = (UINT8)((rawX < 0u) ? 0u : (rawX > 0u) ? 0u : rawX);
//   _camera_y = (UINT8)((rawY < 0u) ? 0u : (rawY > 8u) ? 8u : rawY);
// — the (UINT8) cast now precedes the OUTER paren of the ternary, fixing
// C operator precedence so the cast applies to the whole expression.
//
// ==========================================================================
// POST-FIX update_camera_camera() — extracted via awk brace-walk (Plan 07.4-34)
// Source: gbkt-examples/racer/build/gbkt/generated/main.c
// Extracted on: 2026-05-12T18:44:38Z
// Plan 07.4-34 commit: 2322a4a6
// ==========================================================================

void update_camera_camera(void) {
    INT16 rawX = (INT16)_car_x - 80u;
    INT16 rawY = (INT16)_car_y - 72u;
    _camera_x = (UINT8)((rawX < 0u) ? 0u : (rawX > 0u) ? 0u : rawX);
    _camera_y = (UINT8)((rawY < 0u) ? 0u : (rawY > 8u) ? 8u : rawY);
    if (_camera_shake_timer > 0u) {
        UINT8 offset = (_camera_shake_timer & 1u != 0u) ? _camera_shake_intensity : 0u;
        SCX_REG = _camera_x + offset;
        SCY_REG = _camera_y + offset;
        --_camera_shake_timer;
    } else {
        SCX_REG = _camera_x;
        SCY_REG = _camera_y;
    }
}

// ==========================================================================
// CROSS-GENRE proof — Explorer (RPG/exploration genre)
// Source: gbkt-examples/explorer/build/gbkt/generated/main.c
// Regenerated post-fix on: 2026-05-12T18:45:33Z
//
// Explorer's CameraSystem has NO boundsWidth / boundsHeight (no follow-clamp
// configured by the explorer game block), so visitCameraSystem emits the
// shake/no-clamp branch only. The CCast precedence fix is inert here because
// no CCast(_, CTernary) is ever constructed — the function body contains no
// ternary cast. This is the NON-REGRESSION proof: the fix doesn't change
// shape for genres that don't use camera bounds.
// ==========================================================================

void update_camera_camera(void) {
    if (_camera_shake_timer > 0u) {
        UINT8 offset = (_camera_shake_timer & 1u != 0u) ? _camera_shake_intensity : 0u;
        SCX_REG = _camera_x + offset;
        SCY_REG = _camera_y + offset;
        --_camera_shake_timer;
    } else {
        SCX_REG = _camera_x;
        SCY_REG = _camera_y;
    }
}

// ==========================================================================
// CROSS-GENRE proof — Dungeon (RPG/exploration genre)
// Source: gbkt-examples/dungeon/build/gbkt/generated/main.c
// Regenerated post-fix on: 2026-05-12T18:45:33Z
//
// Same shape as Explorer — no bounds, so no CCast(_, CTernary) to fix.
// Confirms the fix is invisible to genres that don't exercise the cast site.
// ==========================================================================

void update_camera_camera(void) {
    if (_camera_shake_timer > 0u) {
        UINT8 offset = (_camera_shake_timer & 1u != 0u) ? _camera_shake_intensity : 0u;
        SCX_REG = _camera_x + offset;
        SCY_REG = _camera_y + offset;
        --_camera_shake_timer;
    } else {
        SCX_REG = _camera_x;
        SCY_REG = _camera_y;
    }
}

// ==========================================================================
// CROSS-GENRE reusability — any future bounded CameraSystem inherits the fix
//
// The fix lives in CEmitter.emitCastInner(), which dispatches on the AST
// shape of CCast.expr. Per Plan 07.4-32's CameraBoundsClampPrecedenceTest
// (now GREEN — see evidence file 10), any CameraSystem instance — racing,
// platformer, RPG, puzzle — that calls visitCameraSystem with non-null
// boundsWidth/boundsHeight will produce CCast(CU8, CTernary(...)) AST and
// will get the parenthesised emission. The cross-genre proof is therefore
// the JVM test class (a genre-agnostic CameraSystem fixture), not a
// per-example body grep (because the per-example bodies don't currently
// configure bounds on non-racer games).
// ==========================================================================
