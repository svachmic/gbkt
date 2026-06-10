void main(void) {
    NR52_REG = 128u;
    NR50_REG = 119u;
    NR51_REG = 255u;
    DISPLAY_ON;
    SHOW_SPRITES;
    set_sprite_data(0u, 2u, sprites_car_tiles);
    set_sprite_data(2u, 2u, sprites_car_tiles);
    set_sprite_tile(0u, 0u);
    move_sprite(0u, _car_x + 8u, _car_y + 16u);
    set_sprite_tile(1u, 1u);
    move_sprite(1u, _car_x + 8u, _car_y + 24u);
    pool_carAi_init();
    title_enter_trampoline();
    while (1) {
        update_joypad();
        switch (current_scene) {
        case SCENE_RESULTS:
            results_frame_trampoline();
            break;
        case SCENE_RACE:
            race_frame_trampoline();
            break;
        case SCENE_TITLE:
            title_frame_trampoline();
            break;
        }
        update_sprites();
        sound_driver_update();
        wait_vbl_done();
    }
    return;
}
