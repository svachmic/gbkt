void gameplay_frame(void) BANKED {
    uint8_t hiwater = 0u;
    {
    uint8_t subpal = _rot >> 2;
    _elephant_subPalette = subpal;
    _elephant_flipX = (_rot & 0x3u) >> 0u;
    _elephant_flipY = (_rot & 0x3u) >> 1u;
    switch (_rot & 0x3u) {
        case 1:
            hiwater += move_metasprite_flipy(sprite_elephant_frames[_idx], 0, subpal, hiwater,
                                              DEVICE_SPRITE_PX_OFFSET_X + (_posX >> 4),
                                              DEVICE_SPRITE_PX_OFFSET_Y + (_posY >> 4));
            break;
        case 2:
            hiwater += move_metasprite_flipxy(sprite_elephant_frames[_idx], 0, subpal, hiwater,
                                              DEVICE_SPRITE_PX_OFFSET_X + (_posX >> 4),
                                              DEVICE_SPRITE_PX_OFFSET_Y + (_posY >> 4));
            break;
        case 3:
            hiwater += move_metasprite_flipx(sprite_elephant_frames[_idx], 0, subpal, hiwater,
                                              DEVICE_SPRITE_PX_OFFSET_X + (_posX >> 4),
                                              DEVICE_SPRITE_PX_OFFSET_Y + (_posY >> 4));
            break;
        default:
            hiwater += move_metasprite_ex(sprite_elephant_frames[_idx], 0, subpal, hiwater,
                                          DEVICE_SPRITE_PX_OFFSET_X + (_posX >> 4),
                                          DEVICE_SPRITE_PX_OFFSET_Y + (_posY >> 4));
            break;
    }
}

    hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES);
}
