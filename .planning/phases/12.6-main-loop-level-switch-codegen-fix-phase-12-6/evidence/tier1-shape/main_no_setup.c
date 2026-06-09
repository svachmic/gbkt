void main(void) {
    DISPLAY_OFF;
    NR52_REG = 128u;
    NR50_REG = 119u;
    NR51_REG = 255u;
    SHOW_BKG;
    SHOW_SPRITES;
    DISPLAY_ON;
    title_enter();
    while (1) {
        update_joypad();
        switch (current_scene) {
        case SCENE_TITLE:
            title_frame();
            break;
        case SCENE_GAMEPLAY:
            gameplay_frame();
            break;
        case SCENE_GAMEPLAY2:
            gameplay2_frame();
            break;
        case SCENE_NEXTLEVELSCENE:
            nextLevelScene_frame();
            break;
        }
        // Phase 12.6 D-04 — level-switch guard (trimmed; setup_current_level moved to levelCardScene Start-press path)
        // Phase 12.11 Failure A fix — guard also checks current_scene != SCENE_NEXTLEVELSCENE to prevent
        // navigate_to_scene() firing EVERY frame while already on the card scene. Without this,
        // nextLevelScene_enter() runs each frame, consuming the VBlank slot mid-loop and preventing
        // update_joypad() from seeing the START press (frame-boundary collision — DIAGNOSTIC.md Fix Site 2).
        if (_next_level != _current_level && current_scene != SCENE_NEXTLEVELSCENE) {
            navigate_to_scene(SCENE_NEXTLEVELSCENE);
        }
        update_sprites();
        sound_driver_update();
        wait_vbl_done();
    }
}
