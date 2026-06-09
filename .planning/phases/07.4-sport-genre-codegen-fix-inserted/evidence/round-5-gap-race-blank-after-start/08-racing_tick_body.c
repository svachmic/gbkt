void racing_tick_track1(void) {
    // C.1 — throttle/steer ramp using player stats (D-07)
    if (button_held(J_UP)) {
        _vehicle_carPlayer_speed_cur = (_vehicle_carPlayer_speed_cur + 11u < 200u) ? _vehicle_carPlayer_speed_cur + 11u : 200u;
    } else {
        if (button_held(J_DOWN)) {
            _vehicle_carPlayer_speed_cur = (_vehicle_carPlayer_speed_cur > 255u) ? _vehicle_carPlayer_speed_cur - 255u : 0u;
        } else {
            _vehicle_carPlayer_speed_cur = (_vehicle_carPlayer_speed_cur > 1u) ? _vehicle_carPlayer_speed_cur - 1u : 0u;
        }
    }
    // C.2 — steer (cardinal: 0=N, 1=E, 2=S, 3=W; rising-edge dpad)
    if (button_pressed(J_LEFT)) {
        _vehicle_carPlayer_heading = _vehicle_carPlayer_heading + 3u & 3u;
    }
    if (button_pressed(J_RIGHT)) {
        _vehicle_carPlayer_heading = _vehicle_carPlayer_heading + 1u & 3u;
    }
    // C.3 — delta = speed_cur >> 5; switch on heading; write back to actor
    UINT8 delta = _vehicle_carPlayer_speed_cur >> 5u;
    INT8 vx = 0u;
    INT8 vy = 0u;
    switch (_vehicle_carPlayer_heading) {
    case 0u:
        vy = -(INT8)delta;
        break;
    case 1u:
        vx = (INT8)delta;
        break;
    case 2u:
        vy = (INT8)delta;
        break;
    case 3u:
        vx = -(INT8)delta;
        break;
    }
    {
    INT16 propXs = (INT16)_car_x + (INT16)vx;
    INT16 propYs = (INT16)_car_y + (INT16)vy;
    if (propXs >= 0 && propXs < 144 && propYs >= 0 && propYs < 136) {
        UINT8 propX = (UINT8)propXs;
        UINT8 propY = (UINT8)propYs;
        if (((((propX) >> 3) < 19u && ((propY) >> 3) < 19u && _zone_track1_tiles[((propY) >> 3) * 19u + ((propX) >> 3)] != 0u) || (((propX + 7u) >> 3) < 19u && ((propY) >> 3) < 19u && _zone_track1_tiles[((propY) >> 3) * 19u + ((propX + 7u) >> 3)] != 0u) || (((propX) >> 3) < 19u && ((propY + 15u) >> 3) < 19u && _zone_track1_tiles[((propY + 15u) >> 3) * 19u + ((propX) >> 3)] != 0u) || (((propX + 7u) >> 3) < 19u && ((propY + 15u) >> 3) < 19u && _zone_track1_tiles[((propY + 15u) >> 3) * 19u + ((propX + 7u) >> 3)] != 0u))) {
            _car_x = propX;
            _car_y = propY;
        }
    }
}
    // C.4 — camera follow (D-06)
    update_camera_camera();
    // C.5 — checkpoint state machine via mask_below (D-15, D-17)
    if (_racing_checkpoint_idx_track1 < 2u) {
        UINT8 cp_x = _racing_cp_x_track1[_racing_checkpoint_idx_track1];
        UINT8 cp_y = _racing_cp_y_track1[_racing_checkpoint_idx_track1];
        UINT8 dx = (_car_x > cp_x) ? _car_x - cp_x : cp_x - _car_x;
        UINT8 dy = (_car_y > cp_y) ? _car_y - cp_y : cp_y - _car_y;
        if (dx < 8u && dy < 8u) {
            UINT8 mask_below = (1u << _racing_checkpoint_idx_track1) - 1u;
            if (_racing_visited_track1 & mask_below == mask_below) {
                _racing_visited_track1 |= (1u << _racing_checkpoint_idx_track1);
                _racing_checkpoint_idx_track1 += 1u;
            }
        }
    }
    UINT8 cp0_x = _racing_cp_x_track1[0u];
    UINT8 cp0_y = _racing_cp_y_track1[0u];
    UINT8 dx0 = (_car_x > cp0_x) ? _car_x - cp0_x : cp0_x - _car_x;
    UINT8 dy0 = (_car_y > cp0_y) ? _car_y - cp0_y : cp0_y - _car_y;
    if (dx0 < 8u && dy0 < 8u && _racing_visited_track1 == 3u) {
        _racing_lap_count_track1 += 1u;
        _racing_visited_track1 = 1u;
        _racing_checkpoint_idx_track1 = 1u;
    }
    // C.6 — AI loop for pool 'carAi' (D-09)
    for (UINT8 i_carAi = 0u; i_carAi < 1u; i_carAi++) {
        if (_pool_carAi_active[i_carAi] == 0u) {
            continue;
        }
        UINT8 tgt_x_carAi = _racing_wp_x_track1[_pool_carAi_wp_idx[i_carAi]];
        UINT8 tgt_y_carAi = _racing_wp_y_track1[_pool_carAi_wp_idx[i_carAi]];
        UINT8 dx_carAi = (_pool_carAi_x[i_carAi] > tgt_x_carAi) ? _pool_carAi_x[i_carAi] - tgt_x_carAi : tgt_x_carAi - _pool_carAi_x[i_carAi];
        UINT8 dy_carAi = (_pool_carAi_y[i_carAi] > tgt_y_carAi) ? _pool_carAi_y[i_carAi] - tgt_y_carAi : tgt_y_carAi - _pool_carAi_y[i_carAi];
        {
    UINT8 ai_primary;
    UINT8 ai_fallback;
    if (dx_carAi >= dy_carAi) {
        ai_primary = (_pool_carAi_x[i_carAi] < tgt_x_carAi) ? 1u : 3u;
        ai_fallback = (_pool_carAi_y[i_carAi] < tgt_y_carAi) ? 2u : 0u;
    } else {
        ai_primary = (_pool_carAi_y[i_carAi] < tgt_y_carAi) ? 2u : 0u;
        ai_fallback = (_pool_carAi_x[i_carAi] < tgt_x_carAi) ? 1u : 3u;
    }
    UINT8 ai_delta_probe = _pool_carAi_speed_cur[i_carAi] >> 5;
    if (ai_delta_probe == 0u) ai_delta_probe = 1u;
    UINT8 cd;
    UINT8 blocked[4];
    for (cd = 0u; cd < 4u; cd++) {
        INT8 pvx = 0;
        INT8 pvy = 0;
        switch (cd) {
            case 0u: pvy = -(INT8)ai_delta_probe; break;
            case 1u: pvx = (INT8)ai_delta_probe; break;
            case 2u: pvy = (INT8)ai_delta_probe; break;
            case 3u: pvx = -(INT8)ai_delta_probe; break;
        }
        INT16 pXs = (INT16)_pool_carAi_x[i_carAi] + (INT16)pvx;
        INT16 pYs = (INT16)_pool_carAi_y[i_carAi] + (INT16)pvy;
        UINT8 b = 1u;
        if (pXs >= 0 && pXs < 144 && pYs >= 0 && pYs < 136) {
            UINT8 sX = (UINT8)pXs + 4u;
            UINT8 sY = (UINT8)pYs + 8u;
            UINT8 tCol = sX >> 3;
            UINT8 tRow = sY >> 3;
            if (tCol < 19u && tRow < 19u) {
                UINT8 t = _zone_track1_tiles[tRow * 19u + tCol];
                if (t != 0u) b = 0u;
            }
        }
        blocked[cd] = b;
    }
    UINT8 primary_blocked = blocked[ai_primary];
    UINT8 fallback_blocked = blocked[ai_fallback];
    UINT8 both_blocked = (primary_blocked && fallback_blocked) ? 1u : 0u;
    UINT8 ai_prev_heading = _pool_carAi_heading[i_carAi];
    /* prev-perpendicular-commit: when primary is blocked, prefer the
     * previous heading IF it is perpendicular to primary AND still
     * unblocked. This breaks the degenerate oscillation where the AI
     * alternates between fallback (E) and anti-fallback (W) without
     * making progress along the corridor — committing to the perp
     * escape direction lets the AI traverse narrow corridors that
     * require multiple lateral steps before primary unblocks. The
     * axis-bit is bit-0: N=0/S=2 are vertical (bit-0 == 0); E=1/W=3
     * are horizontal (bit-0 == 1). Two headings are perpendicular iff
     * their bit-0 differs. */
    UINT8 ai_prev_is_perp = ((ai_prev_heading & 1u) != (ai_primary & 1u)) ? 1u : 0u;
    if (!primary_blocked) {
        _pool_carAi_heading[i_carAi] = ai_primary;
    } else if (ai_prev_is_perp && !blocked[ai_prev_heading]) {
        _pool_carAi_heading[i_carAi] = ai_prev_heading;
    } else if (!fallback_blocked) {
        _pool_carAi_heading[i_carAi] = ai_fallback;
    } else {
        UINT8 ai_tertiary = 0xFFu;
        for (cd = 0u; cd < 4u; cd++) {
            if (cd == ai_primary || cd == ai_fallback) continue;
            if (!blocked[cd]) { ai_tertiary = cd; break; }
        }
        if (ai_tertiary != 0xFFu) {
            _pool_carAi_heading[i_carAi] = ai_tertiary;
        }
        (void)both_blocked;
    }
}
        _pool_carAi_speed_cur[i_carAi] = (_pool_carAi_speed_cur[i_carAi] + 10u < 252u) ? _pool_carAi_speed_cur[i_carAi] + 10u : 252u;
        UINT8 ai_delta_carAi = _pool_carAi_speed_cur[i_carAi] >> 5u;
        INT8 ai_vx_carAi = 0u;
        INT8 ai_vy_carAi = 0u;
        switch (_pool_carAi_heading[i_carAi]) {
        case 0u:
            ai_vy_carAi = -(INT8)ai_delta_carAi;
            break;
        case 1u:
            ai_vx_carAi = (INT8)ai_delta_carAi;
            break;
        case 2u:
            ai_vy_carAi = (INT8)ai_delta_carAi;
            break;
        case 3u:
            ai_vx_carAi = -(INT8)ai_delta_carAi;
            break;
        }
        {
    INT16 propXs = (INT16)_pool_carAi_x[i_carAi] + (INT16)ai_vx_carAi;
    INT16 propYs = (INT16)_pool_carAi_y[i_carAi] + (INT16)ai_vy_carAi;
    if (propXs >= 0 && propXs < 144 && propYs >= 0 && propYs < 136) {
        UINT8 propX = (UINT8)propXs;
        UINT8 propY = (UINT8)propYs;
        if (((((propX) >> 3) < 19u && ((propY) >> 3) < 19u && _zone_track1_tiles[((propY) >> 3) * 19u + ((propX) >> 3)] != 0u) || (((propX + 7u) >> 3) < 19u && ((propY) >> 3) < 19u && _zone_track1_tiles[((propY) >> 3) * 19u + ((propX + 7u) >> 3)] != 0u) || (((propX) >> 3) < 19u && ((propY + 15u) >> 3) < 19u && _zone_track1_tiles[((propY + 15u) >> 3) * 19u + ((propX) >> 3)] != 0u) || (((propX + 7u) >> 3) < 19u && ((propY + 15u) >> 3) < 19u && _zone_track1_tiles[((propY + 15u) >> 3) * 19u + ((propX + 7u) >> 3)] != 0u))) {
            _pool_carAi_x[i_carAi] = propX;
            _pool_carAi_y[i_carAi] = propY;
        }
    }
}
        if (dx_carAi < 8u && dy_carAi < 8u) {
            _pool_carAi_wp_idx[i_carAi] = (_pool_carAi_wp_idx[i_carAi] + 1u >= 4u) ? 0u : _pool_carAi_wp_idx[i_carAi] + 1u;
        }
    }
}
