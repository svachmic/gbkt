void gameplay_enter(void) BANKED {
    set_bkg_data(0u, _zone_gameplayZone1_tileset_count, _zone_gameplayZone1_tileset);
    _bkg_tiles_load_banked(2u, 0u, 0u, _zone_gameplayZone1_tilemap_WIDTH, _zone_gameplayZone1_tilemap_HEIGHT, _zone_gameplayZone1_tilemap);
    DISPLAY_ON;
    setup_current_level();
}
