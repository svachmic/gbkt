void nextLevelScene_frame(void) BANKED {
    if (button_pressed(J_START)) {
        setup_current_level();
        navigate_to_scene(SCENE_GAMEPLAY);
    }
}
