UINT8 is_tile_solid(UINT16 world_x, UINT16 world_y) NONBANKED {
    UINT8 _previous_bank = CURRENT_BANK;
    SWITCH_ROM(_current_area_bank);
    UINT16 column = world_x >> 3u;
    UINT16 row = world_y >> 3u;
    if (row > (_current_level_height >> 3u) || column >= _current_level_width_in_tiles) {
        SWITCH_ROM(_previous_bank);
        return TRUE;
    }
    UINT8 tile = _current_level_map[column + row * _current_level_width_in_tiles];
    SWITCH_ROM(_previous_bank);
    return tile < _current_level_non_solid_tile_count;
}
