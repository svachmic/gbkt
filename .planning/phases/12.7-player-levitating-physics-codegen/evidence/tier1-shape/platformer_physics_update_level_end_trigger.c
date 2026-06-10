    // Level-end trigger: increment _next_level when past the right margin (grounded-only — Round-6 H3 fix per Plan 12.7-26 verdict; player must be on the floor at trigger fire per SPEC R-03 wording)
    if (player_real_x > _current_level_width - 32u && _grounded != 0) {
        ++_next_level;
    }
}
