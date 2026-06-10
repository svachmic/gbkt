void update_camera_camera(void) {
    INT16 rawX = (INT16)_car_x - 80u;
    INT16 rawY = (INT16)_car_y - 72u;
    _camera_x = (UINT8)(rawX < 0u) ? 0u : (rawX > 0u) ? 0u : rawX;
    _camera_y = (UINT8)(rawY < 0u) ? 0u : (rawY > 8u) ? 8u : rawY;
    if (_camera_shake_timer > 0u) {
        UINT8 offset = (_camera_shake_timer & 1u != 0u) ? _camera_shake_intensity : 0u;
        SCX_REG = _camera_x + offset;
        SCY_REG = _camera_y + offset;
        --_camera_shake_timer;
    } else {
        SCX_REG = _camera_x;
        SCY_REG = _camera_y;
    }
}
