void gameplay_enter(void) BANKED {
    SHOW_SPRITES;
    cls();
    {
        _player_x = 80u;
        _player_y = 72u;
    }
    show_hud_game_hud();
}
