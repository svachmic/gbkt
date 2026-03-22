# Sprite Specifications

Sprite specifications for games without GBDK example equivalents.
Games with GBDK equivalents (Platformer, Shmup) can source sprites from
`gbdk-2020/examples/` — platformer/ and space/ respectively.

## Racer

| Sprite | File | Size | Description |
|--------|------|------|-------------|
| Car | car.png | 8x16 | Top-down race car, 4-color GB palette |
| Track | track.png | 8x8 tileset | Road tiles (straight, curve, grass edge) |

## RPG-Lite

| Sprite | File | Size | Description |
|--------|------|------|-------------|
| Hero | hero.png | 8x16 | Front-facing RPG character |
| Slime | slime.png | 8x8 | Blob monster (asset referenced in code but no monster sprite API) |

## Dungeon

| Sprite | File | Size | Description |
|--------|------|------|-------------|
| Player | player.png | 8x16 | Dungeon crawler character |
| Bat | bat.png | 8x8 | Bat enemy (asset referenced in code but no monster sprite API) |

Note: slime.png and bat.png exist as asset references but the framework does not
yet have a monster sprite rendering API. They serve as documentation of intended
visual design for future implementation.
