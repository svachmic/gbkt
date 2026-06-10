# Assets Module

Type-safe asset references for compile-time validation.

## Files

| File | Purpose |
|------|---------|
| `AssetRef.kt` | Sealed class hierarchy for typed asset paths: `AssetRef` (base) with subclasses `SpriteAsset`, `TileAsset`, `SoundAsset`, `MusicAsset`, `FontAsset`. Includes `AssetType` enum. |
| `AssetRegistry.kt` | `AssetRegistry` interface providing named maps (`sprites`, `tiles`, `sounds`, `music`, `fonts`), a `get(name)` operator, and `allAssets()`. Ships `EmptyAssetRegistry` default. |

## Key Types

- `AssetRef(path, type)` -- base data class with equality/toString
- `SpriteAsset`, `TileAsset`, `SoundAsset`, `MusicAsset`, `FontAsset` -- typed subclasses that fix `AssetType`
- `AssetRegistry` -- organizer interface; games implement it or use Gradle plugin auto-generation

## Related

- `AssetPipeline.kt` (parent package) -- loads and converts assets
- `optimization/` -- analyzes asset efficiency
