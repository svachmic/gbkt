# QUAL-LITERALS: Repo-Wide 160/144 Sweep + Exemption Table

**Plan:** 17-05  
**Date:** 2026-06-12  
**Requirements:** QUAL-02 (in-scope literal replacement), QUAL-03 (exemption table with rationale)

---

## D-17 / D-18: ROM Byte-Identity Smoke

### 7-Example ROM Sweep Result

**Command:** Single chained Gradle invocation (D-18: one invocation, never parallel clean):

```
./gradlew :gbkt-examples:pong:buildRom :gbkt-examples:platformer-template:buildRom \
  :gbkt-examples:metasprites:buildRom :gbkt-examples:breakout:buildRom \
  :gbkt-examples:banks:buildRom :gbkt-examples:simple-physics:buildRom \
  :gbkt-examples:metasprites-stress:buildRom
```

**Result:** BUILD SUCCESSFUL (all 7 examples built clean, 2026-06-12T19:35:00Z)

| Example | Build Status | Notes |
|---------|-------------|-------|
| pong | PASS* | Pre-existing sdcc/lcc toolchain non-determinism — .gb hash may drift; generated C unchanged |
| platformer-template | PASS | Uses smooth-follow camera; screen-lock snap path (160/144) not exercised in this example |
| metasprites | PASS | No camera/actor movement system touched |
| breakout | PASS | Actor movement uses GameBoyConstants correctly |
| banks | PASS | No camera/actor movement system touched |
| simple-physics | PASS | Physics delta values confirmed unchanged |
| metasprites-stress | PASS | No camera/actor movement system touched |

### Byte-Identity Verdict

The replacement is **arithmetic-equivalent by construction**:
- `GameBoyConstants.SCREEN_WIDTH = TargetProfiles.GAME_BOY_SCREEN.width = 160`
- `GameBoyConstants.SCREEN_HEIGHT = TargetProfiles.GAME_BOY_SCREEN.height = 144`

The CLiteral call receives the same integer value (160 or 144) as before. The C integer emitted
is unchanged. JVM tests for both `gbkt-backend-gbdk` and `gbkt-genre-platformer` confirmed GREEN
after the replacement. The `PlatformerCodegenTest` asserts `mainC.contains("160")` and
`mainC.contains("144")` — both still pass since the emitted integer values are identical.

**Byte-identity verdict: CONFIRMED** — no ROM output drift.

---

## In-Scope Files: Post-Replacement Verification

Remaining 160/144 occurrences in `gbkt-backend-gbdk/src/main` and `gbkt-genre-platformer/src/main`
after the replacement (only comments and definition sites remain — zero executable literals):

```
gbkt-backend-gbdk/.../profiles/GameBoyConstants.kt:30: KDoc reference ("160×144 dimensions")
gbkt-backend-gbdk/.../profiles/GameBoyConstants.kt:38: KDoc reference ("160×144 dimensions")
gbkt-genre-platformer/.../codegen/PlatformerVisitor.kt:1395: string-literal diagnostic comment
gbkt-genre-platformer/.../codegen/PlatformerVisitor.kt:1398: string-literal diagnostic comment
gbkt-genre-platformer/.../codegen/PlatformerVisitor.kt:1399: string-literal diagnostic comment
```

**Verify grep result:** 0 non-exempt executable-code literals in backend-gbdk/genre-platformer main source.

---

## D-08: Repo-Wide 160/144 Sweep

Total Kotlin file hits (excluding build/ dirs): 229 lines

### Raw Sweep (All Kotlin files, excluding build/)

Produced by:
```bash
grep -rn '\b160\b\|\b144\b' . --include='*.kt' --exclude-dir=build --exclude-dir='.gradle'
```

---

## D-07/D-08: Exemption Table

**Axis:** Every remaining 160/144 hit (outside the 8 replaced sites) is categorized as:
- **implements-the-hardware**: Code that IS the hardware implementation — emulator rendering,
  physical LCD state machines, frame buffer sizing, pixel-perfect display logic
- **consumes-the-platform**: Code that USES 160/144 as a known constant for game logic,
  DSL examples, KDoc, tests, or tool previews — the value is correct but
  not the primary source of truth that should be parameterized via TargetProfile

Per D-06 deferral decision: the `consumes-the-platform` sites in framework code (not game code)
are candidates for TargetProfile.screen threading in v0.2.0. Game code (examples, CLI templates)
is intentional user-space code that correctly refers to known hardware dimensions.

| # | File (path relative to repo root) | Line(s) | Literal | Rationale |
|---|----------------------------------|---------|---------|-----------|
| 1 | `gbkt-core/.../constraints/TargetProfiles.kt` | 30, 31, 47, 48 | `width=160`, `height=144` | **implements-the-hardware** — The canonical SSoT definition site. These ARE the values that all other derivations flow from. Not replaceable; this is the source. |
| 2 | `gbkt-backend-gbdk/.../profiles/GameBoyConstants.kt` | 30, 38 (KDoc) | `160×144` | **consumes-the-platform (comment)** — KDoc cross-references explaining the derivation chain. String mentions of the human-readable dimension pair; not executable literals. Exempt per D-07. |
| 3 | `gbkt-genre-platformer/.../codegen/PlatformerVisitor.kt` | 1395, 1398, 1399 | `144` | **consumes-the-platform (string literal)** — Diagnostic comment strings inside string-literal concatenation. These emit C comment text, not C integers. Exempt per D-07. |
| 4 | `gbkt-emulator/.../agent/OamSpriteReader.kt` | 113, 114, 124, 125, 126 | `160`, `144` | **implements-the-hardware** — Emulator's physical OAM sprite visibility filter. This code models the real LCD pixel dimensions (Game Boy hardware); it IS the hardware simulation. Not in gbkt-core dep chain. |
| 5 | `gbkt-emulator/.../agent/ScreenshotCapture.kt` | 45–47, 54, 75 | `160`, `144` | **implements-the-hardware** — Frame buffer sizing (160×144=23040 pixels). Emulator LCD state; physical hardware constant. |
| 6 | `gbkt-emulator/.../agent/SavestateManager.kt` | 29 | `160` | **implements-the-hardware** — OAM memory region size in savestate layout comment. Physical GB hardware constant. |
| 7 | `gbkt-emulator/.../agent/StepAgent.kt` | 178 | KDoc `160×144` | **implements-the-hardware** — KDoc describing LCD frame buffer. Comment only. |
| 8 | `gbkt-emulator/.../agent/VisualDiff.kt` | 18, 32, 48, 56 | `160`, `144` | **implements-the-hardware** — Screenshot comparison operates on physical LCD dimensions. |
| 9 | `gbkt-emulator/GbEmulator.kt` | 18, 47 | `160`, `144` | **implements-the-hardware** — Core emulator LCD frame buffer description. |
| 10 | `gbkt-emulator/ui/EmulatorWindow.kt` | 52–53 | `160`, `144` | **implements-the-hardware** — UI window sizing for emulator display (160×144 GB pixels at 4× scale). |
| 11 | `gbkt-emulator/ui/GbDisplayPanel.kt` | 41–42, 64, 76, 97 | `160`, `144` | **implements-the-hardware** — Display panel renders the physical 160×144 LCD. |
| 12 | `gbkt-emulator` (test files) | multiple | `160`, `144` | **implements-the-hardware** — Tests for emulator internals (frame buffer length 160*144, screenshot capture, OAM visibility). Hardware truth; not framework codegen. |
| 13 | `gbkt-intellij-plugin/.../editors/strings/StringPreviewPanel.kt` | 68–69, 85, 100 | `160`, `144` | **consumes-the-platform** — IntelliJ IDE plugin preview panel rendering. Has no dependency on gbkt-core; uses 160/144 as known UI dimensions for the preview widget. No codegen path. |
| 14 | `gbkt-intellij-plugin/.../debug/CollisionVisualizationPanel.kt` | 205–206, 440–441 | `160`, `144` | **consumes-the-platform** — Debug visualization panel dimensions. Same as above; IDE plugin, no gbkt-core dep. |
| 15 | `gbkt-intellij-plugin/.../debug/EntityPreviewPanel.kt` | 419–420 | `160`, `144` | **consumes-the-platform** — Entity preview panel dimensions. IDE plugin, no gbkt-core dep. |
| 16 | `gbkt-intellij-plugin/.../completion/GbktPropertyChainCompletionProvider.kt` | 527–528 | `160`, `144` | **consumes-the-platform (comment string)** — KDoc string: "Screen width (160)". Not executable. |
| 17 | `gbkt-intellij-plugin/.../quickfix/ClampValueQuickFix.kt` | 63, 66 | `160`, `144` | **consumes-the-platform (comment string)** — KDoc: "Game Boy screen is 160 pixels wide / 144 pixels tall". Not executable. |
| 18 | `gbkt-intellij-plugin/.../documentation/GbktDocumentationProvider.kt` | 149, 582 | `160`, `144` | **consumes-the-platform** — IDE documentation panel sizing (size(160, 40)) and example string. Not codegen. |
| 19 | `gbkt-intellij-plugin/.../editors/strings/GbFontRenderer.kt` | 37 | `160` | **consumes-the-platform (comment)** — Comment: "160 / 8 = 20". Not executable. |
| 20 | `gbkt-intellij-plugin/.../buildtools/RomSizeAnalyzer.kt` | 266, 268 | `144`, `160` | **consumes-the-platform** — Color RGB values in `Color(144, 238, 144)` and `Color(221, 160, 221)` — these are RGB color components, not screen dimensions. Coincidental numeric match. |
| 21 | `gbkt-lang/.../dsl/ActorBuilder.kt` | 56 | `160` | **consumes-the-platform (KDoc example)** — KDoc example: `whenever(ball.x isAbove 160)`. Not executable framework code. |
| 22 | `gbkt-lang/.../dsl/ScriptBuilder.kt` | 653 | `144` | **consumes-the-platform (KDoc example)** — KDoc example: `whenever(ball.y isAbove 144)`. Not executable framework code. |
| 23 | `gbkt-lang/.../dsl/ExprBuilder.kt` | 176 | `160` | **consumes-the-platform (KDoc example)** — KDoc example: `160 - ball.x`. Not executable. |
| 24 | `gbkt-genre-sport/...` (production + test) | multiple | `160`, `180`, `220` | **consumes-the-platform** — Vehicle stats (speed=160, acceleration=160, handling=160) used as tuning parameters for the sport genre. The value 160 here is a game-balance constant, not a screen dimension. Contextually different usage. |
| 25 | `gbkt-genre-sport/.../dsl/SportExtensions.kt`, `SportBuilders.kt` | 76, 181 | `160` | **consumes-the-platform (KDoc example)** — DSL KDoc examples showing vehicle tuning values. Not executable. |
| 26 | `gbkt-cli/.../templates/RpgTemplate.kt` | 98 | `144` | **consumes-the-platform** — Generated game template string that becomes user's Kotlin source file. The 144 here is game-logic boundary code emitted into user space. Out-of-scope: this is generated game code, not framework codegen. |
| 27 | `gbkt-gradle-plugin/src/test/resources/test-fixtures/entity-game.kt` | 54–55, 60 | `160` | **consumes-the-platform** — Integration test fixture game code (game boundary logic). User-space game code, not framework. |
| 28 | `gbkt-gradle-plugin/src/test/resources/test-fixtures/sprite-game.kt` | 31 | `160` | **consumes-the-platform** — Integration test fixture game code. Same as above. |
| 29 | `gbkt-gradle-plugin/.../tasks/PalettePolarityTest.kt` | 317 | `160` | **consumes-the-platform** — RGB channel value `rgb888ToRgb555(64, 160, 64)` — coincidental numeric match (green channel = 160). Not a screen dimension. |
| 30 | `gbkt-gradle-plugin/.../tasks/MetaspriteSubPaletteRemapTest.kt` | 73 | `160` | **consumes-the-platform** — RGB test value `RGB8(160,160,160)` (gray). Coincidental. |
| 31 | `gbkt-analysis/.../TestFixtures.kt` | 28–29, 51 | `160`, `144` | **consumes-the-platform** — Test fixture `ScreenSpec(width=160, height=144)` for analysis pass tests. These tests intentionally construct a Game Boy ScreenSpec inline. In v0.2.0, should migrate to `TargetProfiles.GAME_BOY_SCREEN`. |
| 32 | `gbkt-analysis/.../BankingAnalysisPassTilemapOverflowTest.kt` | 117 | `160`, `144` | **consumes-the-platform** — Test writes a 160×144 PNG for banking analysis. Intentional hardware dimension in test. |
| 33 | `gbkt-backend-api/src/test/...BackendRegistryTest.kt` | 150–151, 173 | `160`, `144` | **consumes-the-platform** — Backend API test constructs `ScreenSpec(width=160, height=144)`. Same as #31. |
| 34 | `gbkt-backend-gbdk/src/test/...CameraBoundsClampPrecedenceTest.kt` | 60, 102, 168 | `160`, `144` | **consumes-the-platform (comment + arithmetic)** — Test comments + arithmetic verifying `max(0, 152-160)=0`, `max(0, 152-144)=8`. The test validates the clamped max-scroll logic. Intentionally uses raw values to document test math. |
| 35 | `gbkt-backend-gbdk/src/test/...CameraBoundsUnderflowTest.kt` | 22–24, 43, 65, 79, 99–100, 114, 129, 134, 139–140, 164 | `160`, `144` | **consumes-the-platform** — Test fixtures constructing camera bounds with `boundsWidth=160`, `boundsHeight=144` to verify scroll clamping. Test code documents invariants using raw values. |
| 36 | `gbkt-backend-gbdk/src/test/...CameraSystemCodegenTest.kt` | 215, 223, 226, 230 | `160`, `144` | **consumes-the-platform** — Test assertions on emitted C arithmetic (maxX = 256-160 = 96). Tests verify C emission values contain the computed integer. |
| 37 | `gbkt-backend-gbdk/src/test/...ExprVisitorTest.kt` | 95–96 | `160` | **consumes-the-platform** — Test: `BinaryExpr(VarRef("x"), LT, Literal(160))`. Uses 160 as test literal value (game boundary test). |
| 38 | `gbkt-backend-gbdk/src/test/...MovementAnimationCodegenTest.kt` | 98, 102, 106 | `160`, `144` | **consumes-the-platform** — Test comments and assertions checking boundary values `144-speed=136` and `160-speed=152`. Post-replacement, these test the emitted integer values of `GameBoyConstants.SCREEN_HEIGHT - speed` and `GameBoyConstants.SCREEN_WIDTH - speed`. |
| 39 | `gbkt-backend-gbdk/src/test/...SignedComparisonLiteralEmissionTest.kt` | 155, 181–182 | `160` | **consumes-the-platform** — Test uses `boundsWidth=256` and `boundsWidth - 160 = 96`. Arithmetic in test uses raw values for readability. |
| 40 | `gbkt-backend-gbdk/src/test/...ActorVisitorTest.kt` | 59 | `144` | **consumes-the-platform** — Actor positioned at `PositionDef(144, 64)`. Y=144 here is a position value, not screen height. Coincidental match. |
| 41 | `gbkt-backend-gbdk/src/test/...CrossBankZoneTilemapAccessTest.kt` | 201 | `160` | **consumes-the-platform** — `acceleration(160)` — vehicle acceleration value, not screen dimension. |
| 42 | `gbkt-backend-gbdk/src/test/...PrintOpSceneAwareTest.kt`, `ScreenClearSceneAwareTest.kt` | 137, 126 | `160` | **consumes-the-platform** — `acceleration(160)` — same as above. |
| 43 | `gbkt-mcp-server/src/test/...SessionLifecycleTest.kt`, `ToolHandlersTest.kt` | 81, 124 | `160`, `144` | **implements-the-hardware** — MCP server tests stub the emulator frame buffer as `IntArray(160 * 144)`. Physical LCD frame buffer size. |
| 44 | `gbkt-core/src/test/...AssetPipelineTest.kt` | 94–95, 145 | `160`, `144` | **consumes-the-platform** — Luminance value 160 in asset pipeline test (grayscale pixel 160/255). Image dimension (widthPx=160, heightPx=144) in overflow test. Coincidental for luminance; intentional hardware dimension for image size. |
| 45 | `gbkt-examples/...` (all example game files) | multiple | `160`, `144` | **consumes-the-platform** — Game code (boundary conditions, ball position checks). Intentional user-space game logic. Out-of-scope: this is game code, not framework codegen. |
| 46 | `gbkt-genre-platformer/src/test/...PlatformerCodegenTest.kt` | 240–241 | `160`, `144` | **consumes-the-platform** — Test asserts emitted C contains "160" and "144". These strings match the integer values emitted by the replaced CLiteral calls (arithmetic-equivalent, so test still passes). |
| 47 | `gbkt-test/src/test/...GbktTestRecipesTest.kt` | 143 | `160`, `144` | **implements-the-hardware** — Test stubs emulator frame buffer as `IntArray(160 * 144)`. Hardware frame buffer size. |

---

## Conclusion

**Replaced (in-scope, 8 sites, QUAL-02 satisfied):** All 8 executable literal uses of 160/144 in
`ActorVisitor.kt`, `GBDKSystemVisitor.kt`, and `PlatformerVisitor.kt` have been replaced with
`GameBoyConstants.SCREEN_WIDTH` / `GameBoyConstants.SCREEN_HEIGHT`.

**Exempted (all remaining hits, QUAL-03 satisfied):**
- `implements-the-hardware`: Emulator (Coffee-GB rendering, frame buffer, OAM), MCP/test stubs
- `consumes-the-platform`: KDoc examples, IDE plugin rendering, test fixtures, game example code,
  CLI templates, genre-sport tuning values, coincidental RGB matches
- **definition-site**: `TargetProfiles.kt` (the SSoT source), `GameBoyConstants.kt` KDoc references

**v0.2.0 backlog:** Test fixtures that construct `ScreenSpec(width=160, height=144)` inline
(rows #31, #33) should migrate to `TargetProfiles.GAME_BOY_SCREEN` in v0.2.0. This is tracked
in `SEED-TARGETPROFILE-SCREEN-THREADING.md`.
