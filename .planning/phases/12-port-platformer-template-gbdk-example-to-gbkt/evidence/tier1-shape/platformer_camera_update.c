void platformer_camera_update(void) {
    // Apply background scroll to GPU (horizontal-only tilemap camera)
    move_bkg(_camera_x, 0u);

    // Compute current tile column (camera_x / 8 → tilemap column index)
    _map_pos_x = (UINT8)(_camera_x >> 3u);

    // Column-scroll trigger: only redraw a column when the tile column changes
    if (_map_pos_x != _old_map_pos_x) {
        // Scrolling left: redraw the new LEFT-edge column
        if (_camera_x < _old_camera_x) {
            _bkg_set_level_submap_banked(_map_pos_x + 1u, 0u, 1u, DEVICE_SCREEN_HEIGHT);
        } else {
            // Scrolling right: redraw the new RIGHT-edge column (bounded by level width)
            if (_current_level_width_in_tiles - DEVICE_SCREEN_WIDTH > _map_pos_x) {
                _bkg_set_level_submap_banked(_map_pos_x + DEVICE_SCREEN_WIDTH, 0u, 1u, DEVICE_SCREEN_HEIGHT);
            }
        }

        // Latch new column index for next frame's delta check
        _old_map_pos_x = _map_pos_x;
    }

    // Latch current camera_x for next frame's direction-of-scroll check
    _old_camera_x = _camera_x;
}
