Snap to tile-top: precedence-immune via intermediate CVarDecl locals (one binary-op class per line). Pins RENDERED metasprite-bottom to underlying solid tile's top edge. Plan 12.7-11 — Path A intermediate-vars rewrite (CParenExpr AST surgery deferred to seed). Plan 12.7-19 — Round-5 H1 fix adds `pivot_adjust` to align RENDER vs HITBOX foot (under SPRITES_8x16 + pivot + frameSize geometry the rendered metasprite-bottom sits `frameHeight − pivotY − hitbox.height` pixels below the hitbox foot — for the platformer-template `32 − 6 − 24 = 2 px`); see evidence/round-5-diagnostic.md Section 2.
            UINT16 foot_tile_row = player_real_y + 24u >> 3u;
            UINT16 foot_pixel_top = foot_tile_row << 3u;
            UINT16 pivot_adjust = 2u;
            UINT16 foot_pixel_anchor = foot_pixel_top - 24u - pivot_adjust;
            _player_y = foot_pixel_anchor << 4u;