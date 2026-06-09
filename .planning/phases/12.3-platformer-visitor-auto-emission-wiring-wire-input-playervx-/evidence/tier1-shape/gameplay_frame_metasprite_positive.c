void gameplay_frame(void) BANKED {
    platformer_physics_update();
    uint8_t hiwater = 0u;
    {
    uint8_t subpal = _playerRot >> 2;
    _player_subPalette = subpal;
    _player_flipX = (_playerRot & 0x3u) >> 0u;
    _player_flipY = (_playerRot & 0x3u) >> 1u;
    switch (_playerRot & 0x3u) {
        case 1:
            hiwater += move_metasprite_flipy(sprite_player_frames[_walkFrameIdx], 0, subpal, hiwater,
                                              DEVICE_SPRITE_PX_OFFSET_X + (UINT8)(((INT16)(_playerX >> 4)) - (INT16)_camera_x),
                                              DEVICE_SPRITE_PX_OFFSET_Y + (_playerY >> 4));
            break;
        case 2:
            hiwater += move_metasprite_flipxy(sprite_player_frames[_walkFrameIdx], 0, subpal, hiwater,
                                              DEVICE_SPRITE_PX_OFFSET_X + (UINT8)(((INT16)(_playerX >> 4)) - (INT16)_camera_x),
                                              DEVICE_SPRITE_PX_OFFSET_Y + (_playerY >> 4));
            break;
        case 3:
            hiwater += move_metasprite_flipx(sprite_player_frames[_walkFrameIdx], 0, subpal, hiwater,
                                              DEVICE_SPRITE_PX_OFFSET_X + (UINT8)(((INT16)(_playerX >> 4)) - (INT16)_camera_x),
                                              DEVICE_SPRITE_PX_OFFSET_Y + (_playerY >> 4));
            break;
        default:
            hiwater += move_metasprite_ex(sprite_player_frames[_walkFrameIdx], 0, subpal, hiwater,
                                          DEVICE_SPRITE_PX_OFFSET_X + (UINT8)(((INT16)(_playerX >> 4)) - (INT16)_camera_x),
                                          DEVICE_SPRITE_PX_OFFSET_Y + (_playerY >> 4));
            break;
    }
}

    hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES);
}
