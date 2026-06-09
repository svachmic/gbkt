// Plan 07.4-32 Task 3 — baseline broken-precedence camera shape extracted from
// gbkt-examples/racer/build/gbkt/generated/main.c via awk brace-walk.
//
// Method: start capture at the line matching `void update_camera_camera`, track
// '{' and '}' counts, stop capture when brace count returns to zero. This is the
// scope-level grep pattern from CLAUDE.md "Scope-level grep gates (corollary)".
//
// The broken-precedence shape locked here:
//   _camera_x = (UINT8)(rawX < 0u) ? 0u : (rawX > 0u) ? 0u : rawX;
//   _camera_y = (UINT8)(rawY < 0u) ? 0u : (rawY > 8u) ? 8u : rawY;
//
// C operator precedence binds the (UINT8) cast to the inner `(rawX < 0u)`
// comparison (yielding 0 or 1), then the outer ternary fires — so _camera_x is
// permanently 0 and _camera_y is permanently 0 or 8 (the zone upper-bound clamp.
// 152 px zone − 144 px screen = maxY 8). GAP-CAMERA-NO-FOLLOW.
//
// Plan 07.4-34 GREEN fix must re-parenthesise the emitter (or wrap the inner
// CTernary at AST level) so the cast applies to the entire ternary:
//   _camera_x = (UINT8)((rawX < 0u) ? 0u : (rawX > 0u) ? 0u : rawX);

// ==========================================================================
// BASELINE update_camera_camera() — extracted via awk brace-walk (Plan 07.4-32)
// Source: gbkt-examples/racer/build/gbkt/generated/main.c
// Extracted on: 2026-05-12T18:18:15Z
// ==========================================================================

void update_camera_camera(void) {
    INT16 rawX = (INT16)_car_x - 80u;
    INT16 rawY = (INT16)_car_y - 72u;
    _camera_x = (UINT8)(rawX < 0u) ? 0u : (rawX > 0u) ? 0u : rawX;
    _camera_y = (UINT8)(rawY < 0u) ? 0u : (rawY > 8u) ? 8u : rawY;
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
