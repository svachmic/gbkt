# Constraints Module

Platform-agnostic hardware capability descriptors used by backends for
validation and code generation.

## Files

| File | Purpose |
|------|---------|
| `TargetProfile.kt` | Top-level interface aggregating all specs: `name`, `id`, `screen`, `sprites`, `memory`, `audio`, banking limits |
| `ScreenSpec.kt` | Display specs: resolution, bpp, tile size, background layers, palette support. Computed: `widthInTiles`, `heightInTiles`, `totalTiles` |
| `SpriteSpec.kt` | Sprite hardware: max OAM entries, per-scanline limit, supported `SpriteSize` list, flip/priority flags. Helper: `supportsSize(w, h)` |
| `MemorySpec.kt` | Memory layout: work RAM, VRAM, OAM, HRAM, ROM/RAM bank sizes. Computed: `workRamKB`, `videoRamKB` |
| `AudioSpec.kt` | Audio channels with `AudioChannelType` enum (PULSE, WAVE, NOISE, PCM, FM), sample rate, PCM/wavetable flags. Helper: `channelsOfType(type)` |

## Usage

Backends implement `TargetProfile` for each platform (e.g. `GBProfile`, `GBCProfile`).
The profile is queried during validation to enforce hardware limits and during
codegen to select platform-appropriate code paths.
