# Detekt Violation Inventory — Plan 17-03

**Generated:** 2026-06-12 after re-enabling MagicNumber / UnusedPrivateMember / UnusedPrivateProperty / ComplexCondition in `detekt.yml`.

**Status:** Detekt FAILED as expected. This file is the worklist for Plan 17-06 (fix violations).

---

## Summary Counts

| Rule | Total violations | Main code | Test code |
|------|-----------------|-----------|-----------|
| MagicNumber | 2025 | 734 (non-intellij: ~400 extractable) | included in totals |
| UnusedPrivateProperty | 26 | 6 (3 visitors + 3 intellij) | 20 (test leftovers) |
| ComplexCondition | 10 | 10 | 0 |
| UnusedPrivateMember | 3 | 1 (intellij singleton) | 2 (test helpers) |
| **TOTAL** | **2064** | — | — |

Note: `gbkt-intellij-plugin` dominates MagicNumber (1291 of 2025). IntelliJ UI panel constants (pixel offsets, component sizes, colour values) are the source.

---

## Per-Module Counts

### MagicNumber (2025 violations)

| Subproject | Count | Notes |
|------------|-------|-------|
| gbkt-intellij-plugin | 1291 | IntelliJ UI: pixel sizes, colour ints, layout constants |
| gbkt-lang | 198 | DSL builder: tile/grid numeric constants |
| gbkt-examples | 178 | Example game sources |
| gbkt-core | 110 | AssetPipeline PNG byte offsets, PngValidator thresholds |
| gbkt-emulator | 99 | Emulator rendering / UI panel constants |
| gbkt-ir | 55 | IR arithmetic constants |
| gbkt-genre-rpg | 25 | RPG stat/battle constants |
| gbkt-analysis | 25 | BankingAnalysisPass / OAMAllocationPass / BudgetReporter |
| gbkt-genre-sport | 21 | Racing track/physics constants |
| gbkt-genre-platformer | 11 | Physics / collision constants |
| gbkt-genre-puzzle | 6 | Puzzle grid constants |
| gbkt-mcp-server | 5 | MCP tool parameter limits |
| gbkt-cli | 1 | CLI argument handling |
| **TOTAL** | **2025** | |

### UnusedPrivateProperty (26 violations)

| Subproject | Count | In main/test |
|------------|-------|--------------|
| gbkt-backend-gbdk | 17 | 3 main (visitors), 14 test (leftover helpers) |
| gbkt-intellij-plugin | 3 | 3 main (constructor param `project` unused) |
| gbkt-genre-sport | 2 | 2 test (TILE_DRIVABLE, frame) |
| gbkt-emulator | 2 | 2 test (lastHeld, refreshCount) |
| gbkt-ir | 1 | 1 test (sceneJson) |
| gbkt-examples | 1 | 1 test (ir) |
| **TOTAL** | **26** | 6 main, 20 test |

### ComplexCondition (10 violations)

| Subproject | Count |
|------------|-------|
| gbkt-intellij-plugin | 9 |
| gbkt-core | 1 |
| **TOTAL** | **10** |

### UnusedPrivateMember (3 violations)

| Subproject | Count | In main/test |
|------------|-------|--------------|
| gbkt-intellij-plugin | 1 | 1 main |
| gbkt-emulator | 1 | 1 test |
| gbkt-examples | 1 | 1 test |
| **TOTAL** | **3** | 1 main, 2 test |

---

## Violation Tables by Rule

### MagicNumber — non-intellij-plugin (734 violations)

Representative sample of extractable violations in user-facing modules:

| File | Line | Value | Fix strategy |
|------|------|-------|--------------|
| gbkt-analysis/src/main/kotlin/.../BankingAnalysisPass.kt | 370:65 | unknown | Extract named constant |
| gbkt-analysis/src/main/kotlin/.../BankingAnalysisPass.kt | 441:48 | unknown | Extract named constant |
| gbkt-analysis/src/main/kotlin/.../OAMAllocationPass.kt | 57:50 | unknown | Extract named constant |
| gbkt-analysis/src/main/kotlin/.../OAMAllocationPass.kt | 58:51 | unknown | Extract named constant |
| gbkt-analysis/src/main/kotlin/.../ResourceInventoryPass.kt | 102:48 | unknown | Extract named constant |
| gbkt-analysis/src/main/kotlin/.../BudgetReporter.kt | 91:68 | unknown | Extract named constant |
| gbkt-analysis/src/main/kotlin/.../BudgetReporter.kt | 116:58 | unknown | Extract named constant |
| gbkt-analysis/src/main/kotlin/.../BudgetReporter.kt | 122:51 | unknown | Extract named constant |
| gbkt-analysis/src/main/kotlin/.../BudgetReporter.kt | 127:58 | unknown | Extract named constant |
| gbkt-analysis/src/main/kotlin/.../BudgetReporter.kt | 177:39 | unknown | Extract named constant |
| gbkt-analysis/src/main/kotlin/.../BudgetReporter.kt | 177:58 | unknown | Extract named constant |
| gbkt-analysis/src/main/kotlin/.../BudgetReporter.kt | 195:84 | unknown | Extract named constant |
| gbkt-analysis/src/main/kotlin/.../BudgetReporter.kt | 232:81 | unknown | Extract named constant |
| gbkt-analysis/src/main/kotlin/.../BudgetReporter.kt | 234:36 | unknown | Extract named constant |
| gbkt-analysis/src/main/kotlin/.../BudgetReporter.kt | 234:61 | unknown | Extract named constant |
| gbkt-analysis/src/main/kotlin/.../BudgetReporter.kt | 236:56 | unknown | Extract named constant |
| gbkt-analysis/src/main/kotlin/.../BudgetReporter.kt | 237:61 | unknown | Extract named constant |
| gbkt-analysis/src/main/kotlin/.../BudgetReporter.kt | 263:31 | unknown | Extract named constant |
| gbkt-analysis/src/main/kotlin/.../BudgetReporter.kt | 286:69 | unknown | Extract named constant |
| gbkt-analysis/src/main/kotlin/.../BudgetReporter.kt | 287:62 | unknown | Extract named constant |
| gbkt-analysis/src/main/kotlin/.../BudgetReporter.kt | 289:68 | unknown | Extract named constant |
| gbkt-analysis/src/main/kotlin/.../BudgetReporter.kt | 289:93 | unknown | Extract named constant |
| gbkt-analysis/src/main/kotlin/.../BudgetReporter.kt | 323:48 | unknown | Extract named constant |
| gbkt-analysis/src/main/kotlin/.../BudgetReporter.kt | 361:31 | unknown | Extract named constant |
| gbkt-analysis/src/main/kotlin/.../BudgetReporter.kt | 390:34 | unknown | Extract named constant |
| gbkt-cli/src/main/kotlin/.../Main.kt | 101:62 | unknown | Extract named constant |
| gbkt-core/src/main/kotlin/.../AssetPipeline.kt | 121:27 | unknown | PNG byte-offset constant |
| gbkt-core/src/main/kotlin/.../AssetPipeline.kt | 135:26 | unknown | PNG byte-offset constant |
| gbkt-core/src/main/kotlin/.../AssetPipeline.kt | 135:34 | unknown | PNG byte-offset constant |
| gbkt-core/src/main/kotlin/.../AssetPipeline.kt | 136:34 | unknown | PNG byte-offset constant |
| gbkt-core/src/main/kotlin/.../AssetPipeline.kt | 137:33 | unknown | PNG byte-offset constant |
| gbkt-core/src/main/kotlin/.../AssetPipeline.kt | 138:25 | unknown | PNG byte-offset constant |
| gbkt-core/src/main/kotlin/.../AssetPipeline.kt | 141:17 | unknown | PNG byte-offset constant |
| gbkt-core/src/main/kotlin/.../AssetPipeline.kt | 144:26–50 | unknown | PNG byte-offset constants |
| gbkt-core/src/main/kotlin/.../AssetPipeline.kt | 172:42 | unknown | PNG byte-offset constant |
| gbkt-core/src/main/kotlin/.../AssetPipeline.kt | 204:38–48 | unknown | PNG byte-offset constants |
| gbkt-core/src/main/kotlin/.../AssetPipeline.kt | 207:44–54 | unknown | PNG byte-offset constants |
| gbkt-core/src/main/kotlin/.../AssetPipeline.kt | 210:39–48 | unknown | PNG byte-offset constants |
| gbkt-core/src/main/kotlin/.../AssetPipeline.kt | 241:38–46 | unknown | PNG byte-offset constants |
| gbkt-core/src/main/kotlin/.../AssetPipeline.kt | 242:30 | unknown | PNG byte-offset constant |
| gbkt-core/src/main/kotlin/.../AssetPipeline.kt | 243:41 | unknown | PNG byte-offset constant |
| gbkt-core/src/main/kotlin/.../PngValidator.kt | ~various | unknown | PNG validation thresholds |
| gbkt-lang/src/main/kotlin/ | ~198 violations | various | Tile/grid/DSL numeric constants |
| gbkt-emulator/src/main/kotlin/ | ~90 (main) | various | Emulator rendering constants |
| gbkt-ir/src/main/kotlin/ | ~55 | various | IR arithmetic constants |
| gbkt-genre-rpg/src/main/kotlin/ | 25 | various | RPG stat/battle constants |
| gbkt-genre-sport/src/main/kotlin/ | ~15 | various | Racing physics constants |
| gbkt-genre-platformer/src/main/kotlin/ | ~10 | various | Physics/collision constants |
| gbkt-genre-puzzle/src/main/kotlin/ | 6 | various | Grid constants |
| gbkt-mcp-server/src/main/kotlin/ | 5 | various | MCP parameter limits |

**MagicNumber — intellij-plugin (1291 violations):** IntelliJ UI code with Swing pixel sizes, colour integer constants, layout values. These require manual `@Suppress` or constant extraction in the IntelliJ module. Lower priority than user-facing framework modules.

### UnusedPrivateProperty — all 26 violations

| File | Line | Property | Location | Fix strategy |
|------|------|----------|----------|--------------|
| gbkt-backend-gbdk/.../CombatVisitor.kt | 383:26 | `_child` | main | Real dead code — remove property |
| gbkt-backend-gbdk/.../GBDKSystemVisitor.kt | 5440:27 | `reqObj` | main | Real dead code — remove property |
| gbkt-backend-gbdk/.../RpgVisitor.kt | 93:30 | `gameIR` | main | Real dead code — remove property |
| gbkt-backend-gbdk/.../ExplorationCodegenTest.kt | 401:13 | `northOrdinal` | test | Test leftover — remove |
| gbkt-backend-gbdk/.../ExplorationCodegenTest.kt | 402:13 | `southOrdinal` | test | Test leftover — remove |
| gbkt-backend-gbdk/.../ExplorationCodegenTest.kt | 404:13 | `westOrdinal` | test | Test leftover — remove |
| gbkt-backend-gbdk/.../SimpleBattleAndTilesetTest.kt | 285:13 | `tileset1Count` | test | Test leftover — remove |
| gbkt-backend-gbdk/.../SimpleBattleAndTilesetTest.kt | 286:13 | `tileset2Count` | test | Test leftover — remove |
| gbkt-backend-gbdk/.../SpritePaletteSlotEmissionTest.kt | 185:13 | `mainC` | test | Test leftover — remove |
| gbkt-backend-gbdk/.../TilemapCollisionPathCEmissionTest.kt | 65:25 | `z1` | test | Test leftover — remove |
| gbkt-backend-gbdk/.../TilemapCollisionPathCEmissionTest.kt | 101:25 | `z1` | test | Test leftover — remove |
| gbkt-backend-gbdk/.../TilemapCollisionPathCEmissionTest.kt | 137:25 | `z1` | test | Test leftover — remove |
| gbkt-backend-gbdk/.../TitleSceneEmissionTest.kt | 175:25 | `nextLevelZone` | test | Test leftover — remove |
| gbkt-backend-gbdk/.../ZoneTilemapBankingTest.kt | 36:19 | `EIGHT_KB_TILE_COUNT` | test | Test leftover — remove |
| gbkt-backend-gbdk/.../DV3VisualV3DiagnosticTest.kt | 148:17 | `dummySprite` | test | Test leftover — remove |
| gbkt-backend-gbdk/.../MenuCodegenTest.kt | 319:13 | `hasHideCall` | test | Test leftover — remove |
| gbkt-backend-gbdk/.../ScriptOpVisitorTest.kt | 516:17 | `emittedCode` | test | Test leftover — remove |
| gbkt-emulator/.../StepAgentTest.kt | 289:13 | `lastHeld` | test | Test leftover — remove |
| gbkt-emulator/.../MemoryInspectorPanelTest.kt | 277:13 | `refreshCount` | test | Test leftover — remove |
| gbkt-examples/.../PlatformerTemplateIRTest.kt | 18:17 | `ir` | test | Test leftover — remove |
| gbkt-genre-sport/.../RacingTrackNavigabilityTest.kt | 42:17 | `TILE_DRIVABLE` | test | Test leftover — remove |
| gbkt-genre-sport/.../RacingTrackNavigabilityTest.kt | 426:14 | `frame` | test | Test leftover — remove |
| gbkt-intellij-plugin/.../EntityPreviewPanel.kt | 50:38 | `project` | main | Constructor param forwarded but unused — remove from constructor or use |
| gbkt-intellij-plugin/.../PoEditorPanel.kt | 46:33 | `project` | main | Constructor param forwarded but unused — remove from constructor or use |
| gbkt-intellij-plugin/.../GbktToolWindowPanel.kt | 32:39 | `project` | main | Constructor param forwarded but unused — remove from constructor or use |
| gbkt-ir/.../GameIRSerializerTest.kt | 378:13 | `sceneJson` | test | Test leftover — remove |

### ComplexCondition — all 10 violations

| File | Line | Complexity | Fix strategy |
|------|------|-----------|--------------|
| gbkt-core/src/main/kotlin/.../PngValidator.kt | 141:13 | 4 (threshold 4) | Extract sub-conditions into named booleans or private functions |
| gbkt-intellij-plugin/.../SpriteEditorProvider.kt | 53:13 | 4 | IntelliJ UI check — extract named boolean |
| gbkt-intellij-plugin/.../SpriteSheetPanel.kt | 158:13 | 4 | IntelliJ UI check — extract named boolean |
| gbkt-intellij-plugin/.../TilemapModel.kt | 54:13 | 4 | Tilemap bounds check — extract named boolean |
| gbkt-intellij-plugin/.../TilemapModel.kt | 60:13 | 4 | Tilemap bounds check — extract named boolean |
| gbkt-intellij-plugin/.../TilemapModel.kt | 66:13 | 4 | Tilemap bounds check — extract named boolean |
| gbkt-intellij-plugin/.../TilemapModel.kt | 72:13 | 4 | Tilemap bounds check — extract named boolean |
| gbkt-intellij-plugin/.../TilemapModel.kt | 196:13 | 4 | Tilemap bounds check — extract named boolean |
| gbkt-intellij-plugin/.../TilemapPanel.kt | 183:13 | 4 | IntelliJ panel check — extract named boolean |
| gbkt-intellij-plugin/.../TilesetPanel.kt | 129:13 | 4 | IntelliJ panel check — extract named boolean |

### UnusedPrivateMember — all 3 violations

| File | Line | Member | Location | Fix strategy |
|------|------|--------|----------|--------------|
| gbkt-emulator/.../StepAgentTest.kt | 453:17 | `sprite` (private fun) | test | Test helper leftover — remove or use |
| gbkt-examples/.../PlatformerTemplateEmissionTest.kt | 56:17 | `extractFunctionBody` (private fun) | test | Test helper leftover — remove or use |
| gbkt-intellij-plugin/.../GbktLanguage.kt | 28:17 | `readResolve` (private fun) | main | Serialization hook for Kotlin singleton — add `@Suppress` with rationale or keep as intentional |

---

## Fix Strategy for Plan 17-06

### Priority 1 — High-value, low-risk (fix in source)

These are real dead code violations in main production modules:

| Rule | File | Action |
|------|------|--------|
| UnusedPrivateProperty | CombatVisitor.kt:383 `_child` | Remove unused property |
| UnusedPrivateProperty | GBDKSystemVisitor.kt:5440 `reqObj` | Remove unused property |
| UnusedPrivateProperty | RpgVisitor.kt:93 `gameIR` | Remove unused property |
| UnusedPrivateMember | GbktLanguage.kt:28 `readResolve` | Add `@Suppress("UnusedPrivateMember")` with serialization-hook rationale comment |
| ComplexCondition | PngValidator.kt:141 | Extract sub-conditions into named booleans |

### Priority 2 — Test file cleanup (remove leftover properties)

20 `UnusedPrivateProperty` + 2 `UnusedPrivateMember` violations in test files are leftover properties from refactors. These are real dead code:
- All 14 unused test properties in `gbkt-backend-gbdk` tests
- `lastHeld`, `refreshCount` in emulator tests
- `ir` in platformer template tests
- `TILE_DRIVABLE`, `frame` in sport genre tests
- `sceneJson` in IR serializer tests
- `sprite`, `extractFunctionBody` test helper functions

### Priority 3 — IntelliJ plugin (medium priority)

- 9 ComplexCondition in IntelliJ UI code: extract named booleans in `TilemapModel.kt`, `TilemapPanel.kt`, `TilesetPanel.kt`, `SpriteEditorProvider.kt`, `SpriteSheetPanel.kt`
- 3 UnusedPrivateProperty `project` constructor params in IntelliJ panels: either use or remove
- 1291 MagicNumber violations: selectively add `@Suppress("MagicNumber")` or extract constants in the most egregious cases; bulk Swing pixel constants are acceptable with file-level suppression

### Priority 4 — MagicNumber in framework modules (iterative)

734 non-intellij-plugin MagicNumber violations. Fix strategy:
- **Analysis passes (`gbkt-analysis`):** Extract hardware constants (`BANK_SIZE`, `OAM_SPRITE_COUNT`, `MAX_TILES`) into companion objects — these are meaningful constants that SHOULD be named
- **Asset pipeline (`gbkt-core` AssetPipeline.kt):** Extract PNG byte-offset magic numbers (IHDR chunk offsets, color type byte positions) — these are the most confusing magic numbers
- **DSL modules (`gbkt-lang`, `gbkt-ir`):** Mix of tile/grid defaults that belong as named constants and genuinely idiomatic literals (`0`, `1`) already in `ignoreNumbers`
- **Example games (`gbkt-examples`):** Low priority — sample code; selective suppression where literals are self-documenting
- **Genre modules:** Evaluate per-case; physics constants (gravity, friction, tile-size) are high candidates for named extraction

---

## Verification

```bash
# Confirm no detekt-baseline.xml exists
test ! -f detekt-baseline.xml && echo "OK: no baseline file"
# Confirm this file exists
test -f .planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/QUAL-DETEKT.md && echo "OK: inventory exists"
```

Both checks pass.

---

*Inventory captured: 2026-06-12 | Plan 17-03 Task 2 | No source files modified | No baseline file created*
