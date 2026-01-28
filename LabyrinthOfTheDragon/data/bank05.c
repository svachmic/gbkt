/**
 * [Data] Bank 5 - Stats & Common Battle Routines
 */
#pragma bank 5

#include <gb/gb.h>
#include <gb/cgb.h>
#include <gbdk/incbin.h>
#include <stdint.h>

const uint8_t attack_roll_monster[65] = {
  50, 54, 58, 63, 67, 72, 76, 81,
  85, 89, 94, 98, 103, 107, 112, 116,
  121, 125, 129, 134, 138, 143, 147, 152,
  156, 160, 165, 169, 174, 178, 183, 187,
  192, 193, 195, 196, 198, 199, 201, 203,
  204, 206, 207, 209, 211, 212, 214, 215,
  217, 219, 220, 222, 223, 225, 227, 228,
  230, 231, 233, 235, 236, 238, 239, 241,
  243,
};

const uint8_t attack_roll_player[65] = {
  154, 156, 158, 161, 163, 165, 168, 170, 173, 175,
  177, 180, 182, 184, 187, 189, 192, 194, 196, 199, 201,
  203, 206, 208, 211, 213, 215, 218, 220, 222, 225, 227,
  230, 230, 231, 231, 232, 233, 233, 234, 235, 235, 236,
  236, 237, 238, 238, 239, 240, 240, 241, 241, 242, 243,
  243, 244, 245, 245, 246, 246, 247, 248, 248, 249, 250,
};


const uint8_t damage_roll_modifier[16] = {
  12, 13, 13, 14, 14, 15, 15, 16, 16, 17, 17, 18, 18, 19, 19, 20
};

const uint8_t xp_mod[16] = {
  0, 2, 4, 6, 8, 10, 12, 14, 16, 17, 18, 19, 20, 21, 22, 23,
};

INCBIN(tilemap_battle_menus, "res/tilemaps/battle_menus.tilemap")
INCBIN(tilemap_battle_monster_layouts, "res/tilemaps/battle_monster_layouts.tilemap")

