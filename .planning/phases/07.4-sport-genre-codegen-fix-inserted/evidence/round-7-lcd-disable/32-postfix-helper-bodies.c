void _win_print_at(UINT8 x, UINT8 y, const UINT8* str, UINT8 len) {
void _win_print_at(UINT8 x, UINT8 y, const UINT8* str, UINT8 len) {
    UINT8 i = 0u;
    for (; i < len; i++) {
        set_win_tiles(x + i, y, 1u, 1u, (unsigned char*)&str[i]);
    }
}
void _win_clear_region(UINT8 x, UINT8 y, UINT8 w, UINT8 h) {
void _win_clear_region(UINT8 x, UINT8 y, UINT8 w, UINT8 h) {
    UINT8 ry = 0u;
    UINT8 rx = 0u;
    UINT8 blank = 0u;
    for (; ry < h; ry++) {
        for (; rx < w; rx++) {
            set_win_tiles(x + rx, y + ry, 1u, 1u, &blank);
        }
    }
}
void _win_fill_screen(UINT8 tile) {
void _win_fill_screen(UINT8 tile) {
    UINT8 fy = 0u;
    UINT8 fx = 0u;
    for (; fy < 18u; fy++) {
        for (; fx < 20u; fx++) {
            set_win_tiles(fx, fy, 1u, 1u, &tile);
        }
    }
}
