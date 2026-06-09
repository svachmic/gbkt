void setup_current_level(void) NONBANKED {
    _current_level = _next_level;
    DISPLAY_OFF;
    switch (_current_level % 2u) {
case 0:  // zone: gameplayZone1
    // PLAN-12-18: populate _current_area_bank / _current_level_map / width / height
    // from `_zone_gameplayZone1_tilemap` symbol + per-zone metadata. The Gradle
    // task `ConvertZoneTilesetsTask` emits the symbol at buildRom time (Plan 11.1-17
    // Phase C path), so the assignments below currently reference symbols that only
    // resolve when ConvertZoneTilesetsTask runs (Plan 12-18 first buildRom).
    _current_area_bank = 2u;  // Defect-6 fix: literal bank from bankAllocation (option c-prime)
    _current_level_map = _zone_gameplayZone1_tilemap;
    _current_level_width_in_tiles = _zone_gameplayZone1_tilemap_WIDTH;
    _current_level_height = _zone_gameplayZone1_tilemap_HEIGHT * 8u;
    _current_level_width = _zone_gameplayZone1_tilemap_WIDTH * 8u;
    _current_level_non_solid_tile_count = 17u;
    // Plan 12-19 deviation [Rule 1 - Bug]: load this zone's tileset graphics into BG
    // VRAM tile 0+ and write its tilemap to the BG tilemap. Without these calls, the
    // gameplay scene renders whatever tileset+tilemap the prior scene (title) loaded —
    // the gameplay tilemap pointer is set in `_current_level_map` but never pushed
    // to VRAM. Matches the Banks Phase-11 contract (bank1.c title_enter:13-14) and
    // the reference's `set_native_tile_data(0, ..._TILE_COUNT, ..._tiles)` call inside
    // SetupCurrentLevel (level.c:93). The tileset symbol lives in HOME bank
    // (verified via .noi: `___bank__zone_<id>_tileset = 0x0`), so `set_bkg_data` reads
    // it without SWITCH_ROM. The tilemap lives in bank 2u (per `#pragma bank` in
    // the tilemap .c file), so `_bkg_tiles_load_banked` switches to that bank, calls
    // `set_bkg_tiles`, and restores bank 1 — exactly the same helper used by
    // title_enter and nextLevel_enter, so the contract is consistent across all
    // scenes (title, nextLevel card, gameplay).
    set_bkg_data(0u, _zone_gameplayZone1_tileset_count, _zone_gameplayZone1_tileset);
    // Phase 12.9 RC-1 fix (Plan 12.9-08b) — upload THIS zone's authored palette right after
    // its tile data. Without it, gameplay zones inherit the title scene's BG palette RAM
    // (RC-1 palette inversion: sky renders WHITE, ground near-black). W5's SceneVisitor
    // palette fix is blind to this path because the gameplay scene has empty scene.zoneRefs
    // (cEmit("setup_current_level();"), not zone(...)) — RESEARCH Pitfall 7. The
    // _zone_<id>_tileset_PALETTE_COUNT macro + _zone_<id>_tileset_palettes extern + <gb/cgb.h>
    // include are all provided by W4's ConvertZoneTilesetsTask, so no header work is needed.
    // Emitted unconditionally to mirror the set_bkg_data line above and the main() startup
    // _gbkt_default_bg_pal upload — cgb_compatibility() makes set_bkg_palette a no-op on DMG
    // (RESEARCH RC-1d). Mirrors the reference level.c setBKGPalettes-after-tileset-load order.
    set_bkg_palette(0u, _zone_gameplayZone1_tileset_PALETTE_COUNT, _zone_gameplayZone1_tileset_palettes);
    // Phase 12.6 D-08 (debug 12-6-07 CYCLE 3) — windowed submap write replaces full-tilemap write.
    // The previous `_bkg_tiles_load_banked(bank, 0, 0, WIDTH, HEIGHT, tilemap)` wrote the ENTIRE
    // ZONE_WIDTH x ZONE_HEIGHT tilemap (e.g. 60x18 for level-1/level-2) starting at BG (0,0).
    // The GB BG map is 32x32 cells, and `set_bkg_tiles` WRAPS at the 32-cell boundary — so
    // columns 32..59 of the tilemap overwrote columns 0..27 of the BG map. At camera_x=0 the
    // visible window (cols 0..19) showed tilemap[32..51, r] instead of tilemap[0..19, r]:
    // chimera of right-side tiles wrapped to the left, plus untouched leftover cells from the
    // previous level's BG map content (rows the level-N write never reached). Empirically
    // verified via live MCP capture of frame 1208 — bgText row 16 showed level-1's floor
    // pattern (`$!"#$!"#%67676767676`) at the level-2 spawn position.
    //
    // Reference (gbdk/examples/.../platformer_template/src/main.c:43-50): SetupCurrentLevel
    // loads tileset only, then `SetCurrentLevelSubmap(0, 0, DEVICE_SCREEN_WIDTH+1, DEVICE_SCREEN_HEIGHT)`
    // writes exactly DEVICE_SCREEN_WIDTH+1 (21) x DEVICE_SCREEN_HEIGHT (18) = 378 cells using
    // `set_bkg_submap` which TAKES A STRIDE parameter (no wrap, even though source tilemap
    // is wider than 32). gbkt mirrors this by calling the existing _bkg_set_level_submap_banked
    // helper (declared at GBDKPipeline buildSetLevelSubmapHelperIfNeeded; same shape as the
    // reference's SetCurrentLevelSubmap). The helper reads _current_level_map +
    // _current_level_width_in_tiles + _current_area_bank — all 3 are set on lines 2465-2467
    // ABOVE this call, so the data dependency is satisfied at runtime.
    //
    // The bank-allocation literal `<bank>u` is no longer needed at this site because
    // _bkg_set_level_submap_banked sources _current_area_bank from the just-set global.
    _bkg_set_level_submap_banked(0u, 0u, 21u, 18u);
    // Phase 12.6 D-06 — per-level spawn position (closes DEFECT-2).
    // Spawn coords are pixels in the DSL; <<4 shift converts to subpixel form (mirrors
    // reference SetupPlayer() at platformer_template/src/player.c:101-103). The write
    // happens AFTER `_bkg_tiles_load_banked` so the order mirrors the reference's
    // SetupCurrentLevel() → SetupPlayer() sequence (see Pitfall 1 in 12.6-RESEARCH.md).
    // Velocity reset is part of the contract (Pitfall 3): without it, the player would
    // carry level-N momentum into level-N+1, causing same-frame level-end-trigger re-fire.
    _playerX = ((INT16)_level_spawn_x[0u]) << 4;
    _playerY = ((INT16)_level_spawn_y[0u]) << 4;
    _playerVx = 0;
    _playerVy = 0;
    // Phase 12.9 D4 fix: reset grounded to 0 on every level switch.
    // Without this, grounded carries 1 from the prior level's level-end trigger
    // (which requires grounded == 1). With grounded == 1, gravity is suppressed and
    // the vertical collision snap never fires → player frozen at raw spawn y ("sunk").
    // Resolved from tilemap_collision config "groundedVar" (default "_grounded").
    _grounded = 0;
    // Phase 12.6 D-07 camera reset — mirrors reference main.c:63 .
    // platformer_physics_update() only updates _camera_x when player_real_x >= 80;
    // with spawn at x=40 the update never fires, leaving _camera_x at the old level's
    // scroll position and putting the player off-screen on level-2 entry.
    _camera_x = 0;
    _old_camera_x = 0;
    break;
case 1:  // zone: gameplayZone2
    // PLAN-12-18: populate _current_area_bank / _current_level_map / width / height
    // from `_zone_gameplayZone2_tilemap` symbol + per-zone metadata. The Gradle
    // task `ConvertZoneTilesetsTask` emits the symbol at buildRom time (Plan 11.1-17
    // Phase C path), so the assignments below currently reference symbols that only
    // resolve when ConvertZoneTilesetsTask runs (Plan 12-18 first buildRom).
    _current_area_bank = 2u;  // Defect-6 fix: literal bank from bankAllocation (option c-prime)
    _current_level_map = _zone_gameplayZone2_tilemap;
    _current_level_width_in_tiles = _zone_gameplayZone2_tilemap_WIDTH;
    _current_level_height = _zone_gameplayZone2_tilemap_HEIGHT * 8u;
    _current_level_width = _zone_gameplayZone2_tilemap_WIDTH * 8u;
    _current_level_non_solid_tile_count = 17u;
    // Plan 12-19 deviation [Rule 1 - Bug]: load this zone's tileset graphics into BG
    // VRAM tile 0+ and write its tilemap to the BG tilemap. Without these calls, the
    // gameplay scene renders whatever tileset+tilemap the prior scene (title) loaded —
    // the gameplay tilemap pointer is set in `_current_level_map` but never pushed
    // to VRAM. Matches the Banks Phase-11 contract (bank1.c title_enter:13-14) and
    // the reference's `set_native_tile_data(0, ..._TILE_COUNT, ..._tiles)` call inside
    // SetupCurrentLevel (level.c:93). The tileset symbol lives in HOME bank
    // (verified via .noi: `___bank__zone_<id>_tileset = 0x0`), so `set_bkg_data` reads
    // it without SWITCH_ROM. The tilemap lives in bank 2u (per `#pragma bank` in
    // the tilemap .c file), so `_bkg_tiles_load_banked` switches to that bank, calls
    // `set_bkg_tiles`, and restores bank 1 — exactly the same helper used by
    // title_enter and nextLevel_enter, so the contract is consistent across all
    // scenes (title, nextLevel card, gameplay).
    set_bkg_data(0u, _zone_gameplayZone2_tileset_count, _zone_gameplayZone2_tileset);
    // Phase 12.9 RC-1 fix (Plan 12.9-08b) — upload THIS zone's authored palette right after
    // its tile data. Without it, gameplay zones inherit the title scene's BG palette RAM
    // (RC-1 palette inversion: sky renders WHITE, ground near-black). W5's SceneVisitor
    // palette fix is blind to this path because the gameplay scene has empty scene.zoneRefs
    // (cEmit("setup_current_level();"), not zone(...)) — RESEARCH Pitfall 7. The
    // _zone_<id>_tileset_PALETTE_COUNT macro + _zone_<id>_tileset_palettes extern + <gb/cgb.h>
    // include are all provided by W4's ConvertZoneTilesetsTask, so no header work is needed.
    // Emitted unconditionally to mirror the set_bkg_data line above and the main() startup
    // _gbkt_default_bg_pal upload — cgb_compatibility() makes set_bkg_palette a no-op on DMG
    // (RESEARCH RC-1d). Mirrors the reference level.c setBKGPalettes-after-tileset-load order.
    set_bkg_palette(0u, _zone_gameplayZone2_tileset_PALETTE_COUNT, _zone_gameplayZone2_tileset_palettes);
    // Phase 12.6 D-08 (debug 12-6-07 CYCLE 3) — windowed submap write replaces full-tilemap write.
    // The previous `_bkg_tiles_load_banked(bank, 0, 0, WIDTH, HEIGHT, tilemap)` wrote the ENTIRE
    // ZONE_WIDTH x ZONE_HEIGHT tilemap (e.g. 60x18 for level-1/level-2) starting at BG (0,0).
    // The GB BG map is 32x32 cells, and `set_bkg_tiles` WRAPS at the 32-cell boundary — so
    // columns 32..59 of the tilemap overwrote columns 0..27 of the BG map. At camera_x=0 the
    // visible window (cols 0..19) showed tilemap[32..51, r] instead of tilemap[0..19, r]:
    // chimera of right-side tiles wrapped to the left, plus untouched leftover cells from the
    // previous level's BG map content (rows the level-N write never reached). Empirically
    // verified via live MCP capture of frame 1208 — bgText row 16 showed level-1's floor
    // pattern (`$!"#$!"#%67676767676`) at the level-2 spawn position.
    //
    // Reference (gbdk/examples/.../platformer_template/src/main.c:43-50): SetupCurrentLevel
    // loads tileset only, then `SetCurrentLevelSubmap(0, 0, DEVICE_SCREEN_WIDTH+1, DEVICE_SCREEN_HEIGHT)`
    // writes exactly DEVICE_SCREEN_WIDTH+1 (21) x DEVICE_SCREEN_HEIGHT (18) = 378 cells using
    // `set_bkg_submap` which TAKES A STRIDE parameter (no wrap, even though source tilemap
    // is wider than 32). gbkt mirrors this by calling the existing _bkg_set_level_submap_banked
    // helper (declared at GBDKPipeline buildSetLevelSubmapHelperIfNeeded; same shape as the
    // reference's SetCurrentLevelSubmap). The helper reads _current_level_map +
    // _current_level_width_in_tiles + _current_area_bank — all 3 are set on lines 2465-2467
    // ABOVE this call, so the data dependency is satisfied at runtime.
    //
    // The bank-allocation literal `<bank>u` is no longer needed at this site because
    // _bkg_set_level_submap_banked sources _current_area_bank from the just-set global.
    _bkg_set_level_submap_banked(0u, 0u, 21u, 18u);
    // Phase 12.6 D-06 — per-level spawn position (closes DEFECT-2).
    // Spawn coords are pixels in the DSL; <<4 shift converts to subpixel form (mirrors
    // reference SetupPlayer() at platformer_template/src/player.c:101-103). The write
    // happens AFTER `_bkg_tiles_load_banked` so the order mirrors the reference's
    // SetupCurrentLevel() → SetupPlayer() sequence (see Pitfall 1 in 12.6-RESEARCH.md).
    // Velocity reset is part of the contract (Pitfall 3): without it, the player would
    // carry level-N momentum into level-N+1, causing same-frame level-end-trigger re-fire.
    _playerX = ((INT16)_level_spawn_x[1u]) << 4;
    _playerY = ((INT16)_level_spawn_y[1u]) << 4;
    _playerVx = 0;
    _playerVy = 0;
    // Phase 12.9 D4 fix: reset grounded to 0 on every level switch.
    // Without this, grounded carries 1 from the prior level's level-end trigger
    // (which requires grounded == 1). With grounded == 1, gravity is suppressed and
    // the vertical collision snap never fires → player frozen at raw spawn y ("sunk").
    // Resolved from tilemap_collision config "groundedVar" (default "_grounded").
    _grounded = 0;
    // Phase 12.6 D-07 camera reset — mirrors reference main.c:63 .
    // platformer_physics_update() only updates _camera_x when player_real_x >= 80;
    // with spawn at x=40 the update never fires, leaving _camera_x at the old level's
    // scroll position and putting the player off-screen on level-2 entry.
    _camera_x = 0;
    _old_camera_x = 0;
    break;
        default:
            break;
    }
    DISPLAY_ON;
}
