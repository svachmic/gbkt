void race_enter(void) BANKED {
    _camera_target = 0u;
    set_bkg_data(0, 3, _racing_track1_tileset);
    SWITCH_ROM(2);
    set_bkg_tiles(0, 0, 19u, 19u, _zone_track1_tiles);
    _current_tileset_id = 1u;
    pool_carAi_spawn(80u, 96u);
    {
        HIDE_SPRITES;
        _win_clear_region(0u, 0u, 20u, 18u);
    }
    SHOW_SPRITES;
    _raceTime = 0u;
    _position = 1u;
    _win_print_at(1u, 1u, "LAP:", 4u);

