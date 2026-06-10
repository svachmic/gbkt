void platformer_physics_update(void) {
    // Phase 12.3 — Input → playerVx (D-04 friction on release)
    if (button_held(J_RIGHT)) {
        _player_vx = 128;
    } else {
        if (button_held(J_LEFT)) {
            _player_vx = -128;
        } else {
            UINT8 f = _grounded ? 8u : 0u;
            if (_player_vx > 0) {
                _player_vx = (_player_vx > f) ? (_player_vx - f) : 0;
            } else {
                if (_player_vx < 0) {
                    _player_vx = (_player_vx < -((INT16)f)) ? (_player_vx + f) : 0;
                }
            }
        }
    }

    // Sub-pixel position read (>> 4 = 1/16 pixel granularity)
    UINT16 player_real_x = _player_x >> 4u;
    UINT16 player_real_y = _player_y >> 4u;

    // Horizontal AABB probes — 3-point right wall + 3-point left wall
    if (_player_vx > 0) {
        if (is_tile_solid(player_real_x + 5u, player_real_y + 2u) || is_tile_solid(player_real_x + 5u, player_real_y + 12u) || is_tile_solid(player_real_x + 5u, player_real_y + 22u)) {
            _player_vx = 0u;
        }
    }
    if (_player_vx < 0) {
        if (is_tile_solid(player_real_x - 5u, player_real_y + 2u) || is_tile_solid(player_real_x - 5u, player_real_y + 12u) || is_tile_solid(player_real_x - 5u, player_real_y + 22u)) {
            _player_vx = 0u;
        }
    }

    // Vertical AABB probes — feet (falling) + head (rising)
    if (_player_vy > 0) {
        if (is_tile_solid(player_real_x + 3u, player_real_y + 24u) || is_tile_solid(player_real_x - 3u, player_real_y + 24u)) {
            _player_vy = 0u;
            _grounded = 1u;
            // Snap to tile-top: precedence-immune via intermediate CVarDecl locals (one binary-op class per line). Pins RENDERED metasprite-bottom to underlying solid tile's top edge. Plan 12.7-11 — Path A intermediate-vars rewrite (CParenExpr AST surgery deferred to seed). Plan 12.7-19 — Round-5 H1 fix adds `pivot_adjust` to align RENDER vs HITBOX foot (under SPRITES_8x16 + pivot + frameSize geometry the rendered metasprite-bottom sits `frameHeight − pivotY − hitbox.height` pixels below the hitbox foot — for the platformer-template `32 − 6 − 24 = 2 px`); see evidence/round-5-diagnostic.md Section 2.
            UINT16 foot_tile_row = player_real_y + 24u >> 3u;
            UINT16 foot_pixel_top = foot_tile_row << 3u;
            UINT16 pivot_adjust = 2u;
            UINT16 foot_pixel_anchor = foot_pixel_top - 24u - pivot_adjust;
            _player_y = foot_pixel_anchor << 4u;
            // Each line has at most one binary-op class (the `foot_pixel_anchor` line nests two SAME-class `-` ops — same-class chains are left-associative under C and therefore precedence-immune). Round-trip verified for platformer-template (frameSize 24×32, pivot 12,6, hitbox 8×24, pivot_adjust=2): spawn_y=120, height=24 → foot_tile_row=18, foot_pixel_top=144, pivot_adjust=2, foot_pixel_anchor=118, posYSym=1888 → player_real_y next frame = 118; hitbox foot at 118+24=142 (2 px ABOVE tile-row-18 top at 144); rendered metasprite-bottom at 118-6+32=144 (lands on tile-row-18 top — zero pixel gap). Grounded equilibrium: player_real_y=102, hitbox foot=126, rendered metasprite-bottom=128 (top of tile-row 16). When pivot_adjust=0 (no metasprite bound, or render geometry matches hitbox), the algebra reduces to the Plan 12.7-11 hitbox-foot-snap shape — back-compat for non-platformer-template callers.
        }
    }
    if (_player_vy < 0) {
        if (is_tile_solid(player_real_x + 3u, player_real_y) || is_tile_solid(player_real_x - 3u, player_real_y)) {
            _player_vy = 0u;
        }
    }

    // Stuck-in-ground resolve: pop up until feet clear of solid
    if (_grounded == 0) {
        while (is_tile_solid(player_real_x, player_real_y + 23u)) {
            _player_y -= 16u;
            player_real_y = _player_y >> 4u;
        }
    }

    // Jump initiation: A or UP pressed AND grounded
    if (button_pressed(J_A) || button_pressed(J_UP)) {
        if (_grounded != 0) {
            _player_vy = -800;
            _jump_increase_timer = 0u;
            _grounded = 0u;
        }
    }

    // Sub-pixel velocity integration (>> 4 = scale fixed-point velocity)
    _player_x += _player_vx >> 4u;
    _player_y += _player_vy >> 4u;

    // Camera half-screen trigger: follow player past screen midpoint
    if (player_real_x >= 80u) {
        _camera_x = player_real_x - 80u;
    }

    // Level-end trigger: increment _next_level when past the right margin (grounded-only — Round-6 H3 fix per Plan 12.7-26 verdict; player must be on the floor at trigger fire per SPEC R-03 wording)
    if (player_real_x > _current_level_width - 32u && _grounded != 0) {
        ++_next_level;
    }
}
