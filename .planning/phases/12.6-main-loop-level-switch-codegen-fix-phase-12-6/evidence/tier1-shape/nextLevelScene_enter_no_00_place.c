void nextLevelScene_enter(void) BANKED {
    set_bkg_data(0u, _zone__screen_nextLevelScene_tileset_count, _zone__screen_nextLevelScene_tileset);
    hide_sprites_range(0u, MAX_HARDWARE_SPRITES);
    move_bkg(0u, 0u);
    fill_bkg_rect(0u, 0u, 32u, 32u, 0u);
    _bkg_tiles_load_banked(2u, (DEVICE_SCREEN_WIDTH - _zone__screen_nextLevelScene_tilemap_WIDTH) / 2u, (DEVICE_SCREEN_HEIGHT - _zone__screen_nextLevelScene_tilemap_HEIGHT) / 2u, _zone__screen_nextLevelScene_tilemap_WIDTH, _zone__screen_nextLevelScene_tilemap_HEIGHT, _zone__screen_nextLevelScene_tilemap);
    DISPLAY_ON;
}
