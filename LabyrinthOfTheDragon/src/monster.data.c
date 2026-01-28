#pragma bank 0

#include "core.h"
#include "data.h"
#include "monster.h"

//------------------------------------------------------------------------------
// Tilesets
//------------------------------------------------------------------------------

const Tileset test_dummy_tileset = { MONSTER_TILES, 14, tile_dummy };
const Tileset kobold_tileset = { MONSTER_TILES, 14, tile_kobold };
const Tileset goblin_tileset = { MONSTER_TILES, 14, tile_goblin };
const Tileset zombie_tileset = { MONSTER_TILES, 14, tile_zombie };
const Tileset bugbear_tileset = { MONSTER_TILES, 14, tile_bugbear };
const Tileset owlbear_tileset = { MONSTER_TILES, 14, tile_owlbrear };
const Tileset gelatinous_cube_tileset = { MONSTER_TILES, 14, tile_gelatinous_cube };
const Tileset displacer_beast_tileset = { MONSTER_TILES, 14, tile_displacer_beast };
const Tileset will_o_wisp_tileset = { MONSTER_TILES, 14, tile_will_o_wisp };
const Tileset deathknight_tileset = { MONSTER_TILES, 14, tile_deathknight };
const Tileset mindflayer_tileset = { MONSTER_TILES, 15, tile_mindflayer };
const Tileset beholder_tileset = { MONSTER_TILES, 15, tile_beholder };
const Tileset dragon_tileset = { MONSTER_TILES, 15, tile_dragon };

//------------------------------------------------------------------------------
// Palettes
//------------------------------------------------------------------------------

const palette_color_t dummy_palette[4] = {
  RGB_WHITE,
  RGB8(210, 0, 150),
  RGB_DARKBLUE,
  RGB8(24, 0, 24),
};

const palette_color_t kobold_palettes[16] = {
  // C-Tier
  RGB_WHITE,
  RGB8(177, 113, 51),
  RGB8(77, 22, 11),
  RGB8(37, 3, 40),
  // B-Tier
  RGB_WHITE,
  RGB8(113, 51, 177),
  RGB8(77, 22, 11),
  RGB8(37, 3, 40),
  // A-Tier
  RGB_WHITE,
  RGB8(104, 196, 121),
  RGB8(6, 58, 99),
  RGB8(26, 28, 79),
  // S-Tier
  RGB_WHITE,
  RGB8(197, 78, 36),
  RGB8(107, 25, 11),
  RGB8(50, 19, 32),
};

const palette_color_t goblin_palettes[16] = {
  // C-Tier
  RGB_WHITE,
  RGB8(155, 181, 63),
  RGB8(100, 69, 43),
  RGB8(14, 20, 55),
  // B-Tier
  RGB_WHITE,
  RGB8(188, 157, 64),
  RGB8(91, 25, 90),
  RGB_BLACK,
  // A-Tier
  RGB_WHITE,
  RGB8(88, 173, 188),
  RGB8(103, 45, 88),
  RGB8(33, 17, 17),
  // S-Tier
  RGB_WHITE,
  RGB8(172, 61, 58),
  RGB8(88, 21, 37),
  RGB8(13, 10, 46),
};

const palette_color_t zombie_palettes[16] = {
  // C-Tier
  RGB_WHITE,
  RGB8(156, 173, 63),
  RGB8(106, 104, 38),
  RGB8(43, 61, 51),
  // B-Tier
  RGB_WHITE,
  RGB8(81, 142, 204),
  RGB8(53, 47, 169),
  RGB8(38, 69, 48),
  // A-Tier
  RGB_WHITE,
  RGB8(183, 131, 40),
  RGB8(107, 34, 11),
  RGB8(58, 35, 65),
  // S-Tier
  RGB_WHITE,
  RGB8(136, 118, 229),
  RGB8(65, 110, 106),
  RGB8(81, 26, 26),
};

const palette_color_t bugbear_palettes[16] = {
  // C-Tier
  RGB_WHITE,
  RGB8(243, 199, 114),
  RGB8(168, 84, 34),
  RGB8(35, 41, 79),
  // B-Tier
  RGB_WHITE,
  RGB8(113, 183, 186),
  RGB8(93, 120, 39),
  RGB8(52, 31, 57),
  // A-Tier
  RGB_WHITE,
  RGB8(207, 113, 133),
  RGB8(34, 65, 136),
  RGB8(31, 32, 8),
  // S-Tier
  RGB_WHITE,
  RGB8(201, 79, 76),
  RGB8(95, 47, 5),
  RGB8(23, 28, 44),
};

const palette_color_t owlbear_palettes[16] = {
  // C-Tier
  RGB_WHITE,
  RGB8(208, 168, 98),
  RGB8(131, 65, 33),
  RGB8(43, 21, 2),
  // B-Tier
  RGB_WHITE,
  RGB8(140, 175, 135),
  RGB8(98, 87, 65),
  RGB8(10, 17, 40),
  // A-Tier
  RGB_WHITE,
  RGB8(150, 149, 29),
  RGB8(35, 64, 103),
  RGB8(33, 27, 49),
  // S-Tier
  RGB_WHITE,
  RGB8(201, 181, 45),
  RGB8(130, 50, 34),
  RGB8(34, 59, 48),
};

const palette_color_t gelatinous_cube_palettes[16] = {
  // C-Tier
  RGB_WHITE,
  RGB8(130, 190, 34),
  RGB8(34, 116, 45),
  RGB8(29, 53, 62),
  // B-Tier
  RGB_WHITE,
  RGB8(230, 134, 63),
  RGB8(128, 113, 31),
  RGB8(50, 43, 91),
  // A-Tier
  RGB_WHITE,
  RGB8(186, 66, 221),
  RGB8(119, 35, 63),
  RGB8(51, 49, 24),
  // S-Tier
  RGB_WHITE,
  RGB8(231, 185, 27),
  RGB8(129, 70, 13),
  RGB8(79, 8, 8),
};

const palette_color_t displacer_beast_palettes[16] = {
  // C-Tier
  RGB_WHITE,
  RGB8(116, 105, 227),
  RGB8(55, 66, 159),
  RGB8(45, 17, 32),
  // B-Tier
  RGB_WHITE,
  RGB8(149, 106, 185),
  RGB8(123, 45, 87),
  RGB8(33, 19, 83),
  // A-Tier
  RGB_WHITE,
  RGB8(180, 167, 78),
  RGB8(92, 118, 48),
  RGB8(54, 10, 5),
  // S-Tier
  RGB_WHITE,
  RGB8(200, 96, 45),
  RGB8(125, 24, 24),
  RGB8(7, 26, 29),
};

const palette_color_t will_o_wisp_palettes[16] = {
  // C-Tier
  RGB_WHITE,
  RGB8(79, 172, 216),
  RGB8(170, 33, 221),
  RGB_BLACK,
  // B-Tier
  RGB_WHITE,
  RGB8(116, 197, 51),
  RGB8(157, 123, 41),
  RGB_BLACK,
  // A-Tier
  RGB_WHITE,
  RGB8(194, 165, 50),
  RGB8(85, 20, 7),
  RGB_BLACK,
  // S-Tier
  RGB_WHITE,
  RGB8(54, 207, 101),
  RGB8(95, 18, 210),
  RGB_BLACK,
};

const palette_color_t deathknight_palettes[16] = {
  // C-Tier
  RGB_WHITE,
  RGB8(156, 179, 138),
  RGB8(27, 82, 149),
  RGB8(2, 2, 45),
  // B-Tier
  RGB_WHITE,
  RGB8(200, 184, 128),
  RGB8(106, 55, 9),
  RGB8(35, 5, 0),
  // A-Tier
  RGB_WHITE,
  RGB8(186, 146, 130),
  RGB8(109, 54, 107),
  RGB8(12, 30, 10),
  // S-Tier
  RGB_WHITE,
  RGB8(232, 179, 95),
  RGB8(135, 18, 18),
  RGB8(24, 11, 45),
};

const palette_color_t mindflayer_palettes[16] = {
  // C-Tier
  RGB_WHITE,
  RGB8(196, 145, 222),
  RGB8(83, 63, 121),
  RGB8(9, 19, 63),
  // B-Tier
  RGB_WHITE,
  RGB8(200, 164, 117),
  RGB8(69, 87, 72),
  RGB8(51, 0, 15),
  // A-Tier
  RGB_WHITE,
  RGB8(119, 179, 188),
  RGB8(95, 105, 60),
  RGB8(3, 32, 21),
  // S-Tier
  RGB_WHITE,
  RGB8(165, 174, 192),
  RGB8(54, 42, 73),
  RGB8(86, 12, 48),
};

const palette_color_t beholder_palettes[16] = {
  // C-Tier
  RGB_WHITE,
  RGB8(188, 191, 96),
  RGB8(59, 92, 52),
  RGB8(5, 31, 18),
  // B-Tier
  RGB_WHITE,
  RGB8(94, 206, 216),
  RGB8(24, 70, 132),
  RGB8(51, 0, 66),
  // A-Tier
  RGB_WHITE,
  RGB8(195, 89, 221),
  RGB8(17, 81, 126),
  RGB8(14, 47, 40),
  // S-Tier
  RGB_WHITE,
  RGB8(194, 91, 63),
  RGB8(115, 72, 27),
  RGB8(65, 0, 0),
};

const palette_color_t dragon_palettes[16] = {
  // C-Tier
  RGB_WHITE,
  RGB8(94, 171, 93),
  RGB8(9, 92, 70),
  RGB8(0, 41, 56),
  // B-Tier
  RGB_WHITE,
  RGB8(201, 176, 76),
  RGB8(84, 109, 16),
  RGB8(25, 39, 0),
  // A-Tier
  RGB_WHITE,
  RGB8(116, 193, 218),
  RGB8(46, 73, 152),
  RGB8(16, 32, 53),
  // S-Tier
  RGB_WHITE,
  RGB8(214, 80, 44),
  RGB8(102, 43, 11),
  RGB8(37, 9, 22),
};
