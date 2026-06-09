void title_enter(void) BANKED {
    set_bkg_data(0u, _zone_titleZone_tileset_count, _zone_titleZone_tileset);
    _bkg_tiles_load_banked(2u, 0u, 0u, _zone_titleZone_tilemap_WIDTH, _zone_titleZone_tilemap_HEIGHT, _zone_titleZone_tilemap);
    DISPLAY_ON;
    fill_bkg_rect(0u, 0u, 20u, 18u, 0u);
}
