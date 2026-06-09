void platformer_camera_update(void) {
    // Smooth-follow camera with dead zone (8x16)

    // Horizontal dead-zone check
    if (abs(_cam_target_x - _cam_x) > 8u) {
        if (_cam_target_x > _cam_x) {
            _cam_x = _cam_target_x - 8u;
        } else {
            _cam_x = _cam_target_x + 8u;
        }
    }

    // Vertical dead-zone check
    if (abs(_cam_target_y - _cam_y) > 16u) {
        if (_cam_target_y > _cam_y) {
            _cam_y = _cam_target_y - 16u;
        } else {
            _cam_y = _cam_target_y + 16u;
        }
    }

    // Apply camera scroll
    move_bkg(_cam_x, _cam_y);
}
