# gbkt-intellij-plugin — IDE Support

IntelliJ IDEA plugin providing syntax highlighting, code completion, inspections, visual editors, and live C code preview for the gbkt Game Boy Kotlin DSL.

## Sub-packages

| Package | Files | Purpose |
|---------|-------|---------|
| `highlighting/` | 4 | Syntax highlighting (`GbktSyntaxHighlighter`), DSL annotator for call/name coloring, color settings page |
| `completion/` | 5 | Code completion: keyword, builder, property-chain, type-aware providers wired via `GbktCompletionContributor` |
| `navigation/` | 2 | Go-to-declaration for DSL names (`GbktGotoDeclarationHandler`), nested property navigation |
| `inspections/` | 3 | DSL inspection (context checks, GB constraint validation, undefined refs), asset ref inspection, quick fix |
| `editors/` | 6 sub-dirs | Visual editors for sprites, tilemaps, palettes, animations, strings, balance data |
| `toolwindow/` | 3 | C code preview panel with source-map navigation, auto-refresh, Kotlin-to-C caret sync |
| `project/` | 4+templates | New project wizard (`GbktModuleBuilder`), file generator, 3 templates (Minimal, Platformer, RPG) |
| `settings/` | 4 | Application-level and project-level plugin configuration |
| `documentation/` | 1 | Quick doc provider for DSL symbols |
| `gutter/` | 1 | Budget gutter icons showing ROM bank usage per scene/asset (green/yellow/red by %) |
| `codegen/` | 1 | `GbktCodegenService` — async Gradle-backed C generation with source-map parsing and caching |

## Visual Editors

| Editor | Sub-dir | Purpose |
|--------|---------|---------|
| Sprite | `editors/sprite/` | PNG viewer with zoom, grid overlay, GB-palette preview; `SpriteEditorProvider` opens for `.png` in gbkt projects |
| Tilemap | `editors/tilemap/` | Tile painting (brush/fill/eraser/select), object/exit placement, DSL code output, tileset panel |
| Palette | `editors/palette/` | GBC 15-bit color picker (`GbcColorPicker`), palette swatch editor, DSL code output |
| Animation | `editors/animation/` | Frame sequence editor with live playback preview, frame-rate slider, DSL code output |
| Strings | `editors/strings/` | `.po` file editor with GB-font preview (`GbFontRenderer`), char/line validation (18 char/90 total limits) |
| Balance Data | `editors/data/` | Spreadsheet editor for RPG stat curves with `CurveVisualizationPanel` graphs and template presets |

## Key Classes

| Class | Role |
|-------|------|
| `GbktLanguage` | Singleton language definition (`Language("gbkt")`) |
| `GbColors` | DMG/grayscale palettes, RGB-to-GBC conversion, 2bpp validation and quantization |
| `GbktCodegenService` | Runs `generateC` via Gradle, parses `.gbkt.map` source maps, provides bidirectional Kotlin-C line mapping |
| `CCodePreviewPanel` | Read-only C editor with click-to-navigate source mapping and auto-refresh on DSL file changes |
| `BudgetGutterIconProvider` | Reads budget report, shows per-bank usage as gutter icons with tooltip (bytes used / capacity) |
| `GbktDslInspection` | Validates DSL context requirements, GB hardware constraints (sprite count, screen bounds), undefined refs |
| `GameTemplate` | Interface for project templates; implementations: `MinimalTemplate`, `PlatformerTemplate`, `RpgTemplate` |

## Common Tasks

- **Add completion for new DSL keyword:** `completion/GbktKeywordCompletionProvider.kt`
- **Add builder-context completion:** `completion/GbktBuilderCompletionProvider.kt`
- **Add new inspection:** `inspections/GbktDslInspection.kt` (add check method), register in `plugin.xml`
- **Add visual editor:** Create sub-dir under `editors/`, implement `FileEditorProvider`, register in `plugin.xml`
- **Add project template:** Implement `GameTemplate` interface in `project/templates/`, add to `GbktModuleBuilder`
- **Modify syntax colors:** `highlighting/GbktDslAnnotator.kt` (semantic), `highlighting/GbktSyntaxHighlighter.kt` (lexical)
- **Extend C preview navigation:** `toolwindow/CCodePreviewPanel.kt` — `navigateToSource()` and caret listeners
- **Extend budget gutter:** `gutter/BudgetGutterIconProvider.kt` — `parseBudgetReport()` and `resolveBudgetEntry()`
