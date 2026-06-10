# v2 Codegen Pipeline Investigation Report

## Executive Summary

The v2 codegen pipeline has a **critical structural problem**: it generates a minimal game loop and scene transitions with variable assignments, but **almost all game features (sprite rendering, collision detection, input handling, entity management) are either stubbed or completely missing**. Pong will compile and run but shows a blank screen after pressing Start because the generated C code doesn't actually render sprites or update OAM.

---

## What IS Generated (7 ScriptOp Types)

The `ScriptOpVisitor.kt` class explicitly handles only **7 out of 20+ ScriptOp types**:

### Fully Implemented:
1. **Assign** — Variable assignment (`_ball_x = 80`, `_p1Score += 1`)
2. **IfOp** — Conditional branches (`if (ball.y < 16) { ballDy = 1; }`)
3. **SetPosition** — Teleport actors (`_paddle1_x = 16; _paddle1_y = 64;`)
4. **MoveBy** — Relative movement (`_ball_x += 1;`)
5. **NavigateTo** — Scene transitions (`navigate_to_scene(SCENE_GAME);`)
6. **PrintOp** — Text printing (`gotoxy(6, 4); printf("PONG");`)
7. **FadeOp** — Screen fade stubs (`hide_sprites_range(0, 40);` — **EMPTY STUB**)
8. **RawOp** — Passthrough C code injection

### All Others → `CRawCode("/* TODO: ClassName */")`:
- `ShowDialog` — TODO
- `ShowMenu` — TODO
- `PlaySound` — TODO
- `SpawnActor` — TODO
- `DestroyActor` — TODO
- `AnimateOp` — TODO
- `CameraOp` — TODO
- `WaitFrames` — TODO
- `CallOp` — TODO
- `ReturnOp` — TODO
- `MathOp` — TODO
- `TriggerSystem` — TODO
- `WhileOp` — TODO
- `ForOp` — TODO
- `ArrayAssign` — TODO (partially; basic syntax generated but no OAM management)

---

## The Critical Missing Features

### 1. **Sprite/Entity Rendering (COMPLETELY STUBBED)**

**In ScriptOpVisitor.kt, lines 489-505:**
```kotlin
private fun buildSpriteHelperStubs(): List<CFunction> {
    return listOf(
        CFunction(
            name = "hide_sprites_range",
            returnType = CVoid,
            params = listOf(CParam("from", CU8), CParam("to", CU8)),
            body = listOf(CRawCode("/* TODO: Phase 3 - OAM management */")),  // ← EMPTY!
        ),
        CFunction(
            name = "show_sprites_range",
            returnType = CVoid,
            params = listOf(CParam("from", CU8), CParam("to", CU8)),
            body = listOf(CRawCode("/* TODO: Phase 3 - OAM management */")),  // ← EMPTY!
        ),
    )
}
```

**Result**: When Pong calls `showSprites()` in the game scene's enter handler:
- DSL records: `hideSprites()` → `RawOp("HIDE_SPRITES;")`
- ScriptOpVisitor converts: `RawOp` → `CRawCode("HIDE_SPRITES;")`
- GBDK preprocessor expands `HIDE_SPRITES` macro to actual register write
- But `show_sprites_range(0, 40)` in `FadeOp.visitFadeOp()` → empty function body
- **Result**: Screen stays blank because OAM (Object Attribute Memory) is never populated

**What's Missing**:
- `move_sprite(oam_slot, x, y)` — Position OAM entries
- `set_sprite_tile(oam_slot, tile_id)` — Assign sprite graphics
- `set_sprite_prop(oam_slot, palette)` — Set GBC attributes
- Sprite tile data loading via `set_sprite_data(offset, count, data)`
- Actor-to-OAM slot mapping (currently unimplemented)

**Why**: The comment says "Phase 3 will replace these" — the asset pipeline that maps sprites to VRAM/OAM slots is not yet integrated into v2.

---

### 2. **Entity Management (NOT IN IR)**

**Current state**:
- `ActorIR` is defined with position, sprite, hitbox fields
- `ActorVisitor.visit()` generates **only position variable declarations**:
  ```kotlin
  CVarDecl("_ball_x", CU8, CLiteral(80))
  CVarDecl("_ball_y", CU8, CLiteral(72))
  ```
- No OAM slot assignment
- No sprite tile initialization
- `SpawnActor`, `DestroyActor`, `AnimateOp` are **not handled** by the codegen

**ScriptOp Types for Entity Lifecycle**:
```kotlin
data class SpawnActor(val actorId: String) : ScriptOp  // → TODO comment
data class DestroyActor(val actorId: String) : ScriptOp // → TODO comment  
data class AnimateOp(val actorId: String, val animation: String) : ScriptOp // → TODO comment
data class SetVisible(val actorId: String, val visible: Boolean) : ScriptOp // Partially handled as RawOp
```

All three produce **TODO comments** in the C output.

---

### 3. **Input Handling (PARTIAL)**

**What Works**:
- Input helpers (`update_joypad`, `button_pressed`, `button_held`, `dpad_held`, `dpad_pressed`) are generated in `buildInputHelperFunctions()` 
- Joypad state variables (`__joypad`, `__joypad_prev`) are declared
- Main loop calls `update_joypad()` every frame

**What's Missing**:
- No DSL binding for input conditionals in v2! 
- Pong uses `buttonPressed("start")`, `dpadHeld("up")` but these are **hand-written helper functions** in `ScriptBuilder.kt`
- No automatic IR generation for button/dpad state checks
- `ShowMenu` produces TODO — menus with input selection are not implemented

---

### 4. **Collision Detection (NOT IMPLEMENTED)**

The v2 DSL has no collision system. Pong manually simulates paddle/ball collisions with conditionals:
```kotlin
whenever(varRef("ball.x") isBelow literal(20)) { assign("ballDx", literal(1)) }
```

This is **hard-coded game logic**, not a reusable collision system.

---

### 5. **Sound/Audio (NOT IMPLEMENTED)**

`PlaySound` ScriptOp produces a TODO comment.

---

### 6. **Camera System (NOT IMPLEMENTED)**

`CameraOp` with actions (FOLLOW, UNFOLLOW, SHAKE, MOVE_TO) produces a TODO comment.

---

## What the Generated C Actually Looks Like

**For Pong, the generated main.c would contain:**

```c
// Global variables (generated by ActorVisitor + global variables)
UINT8 _paddle1_x = 16;
UINT8 _paddle1_y = 64;
UINT8 _paddle2_x = 152;
UINT8 _paddle2_y = 64;
UINT8 _ball_x = 80;
UINT8 _ball_y = 72;
UINT8 _p1Score = 0;
UINT8 _p2Score = 0;
INT8 _ballDx = 1;
INT8 _ballDy = 1;

UINT8 current_scene = SCENE_TITLE;

// Input helpers (real implementations)
void update_joypad(void) {
    __joypad_prev = __joypad;
    __joypad = joypad();
}

UINT8 button_pressed(UINT8 mask) {
    return (__joypad & mask) & ~(__joypad_prev & mask);
}

// ... other button/dpad helpers ...

// Sprite helpers (STUBS!)
void hide_sprites_range(UINT8 from, UINT8 to) {
    /* TODO: Phase 3 - OAM management */
}

void show_sprites_range(UINT8 from, UINT8 to) {
    /* TODO: Phase 3 - OAM management */
}

// Scene navigation
void navigate_to_scene(UINT8 scene) {
    switch (current_scene) {
        case SCENE_TITLE:
            title_exit();
            break;
        case SCENE_GAME:
            game_exit();
            break;
        case SCENE_GAMEOVER:
            gameover_exit();
            break;
    }
    current_scene = scene;
    switch (scene) {
        case SCENE_TITLE:
            title_enter();
            break;
        case SCENE_GAME:
            game_enter();
            break;
        case SCENE_GAMEOVER:
            gameover_enter();
            break;
    }
}

// Main loop
void main(void) {
    title_enter();  // Call start scene enter
    while (1) {
        update_joypad();
        switch (current_scene) {
            case SCENE_TITLE:
                title_frame();
                break;
            case SCENE_GAME:
                game_frame();
                break;
            case SCENE_GAMEOVER:
                gameover_frame();
                break;
        }
        wait_vbl_done();
    }
}
```

**In bank1.c (scene functions):**

```c
void title_enter(void) {
    HIDE_SPRITES;  // Macro: expands to register write
    cls();         // Clear text layer
    gotoxy(6, 4);
    printf("PONG");
    gotoxy(3, 10);
    printf("PRESS START");
}

void title_frame(void) {
    if (button_pressed(J_START)) {
        navigate_to_scene(SCENE_GAME);
    }
}

void game_enter(void) {
    cls();
    SHOW_SPRITES;  // ← This macro expands, but show_sprites_range() body is empty!
    _ball_x = 80;
    _ball_y = 72;
    _ballDx = 1;
    _ballDy = 1;
    _p1Score = 0;
    _p2Score = 0;
}

void game_frame(void) {
    if (dpad_held(J_UP)) {
        _paddle1_y = (UINT8)(_paddle1_y + -2);
    }
    if (dpad_held(J_DOWN)) {
        _paddle1_y = (UINT8)(_paddle1_y + 2);
    }
    if (_paddle2_y > _ball_y) {
        _paddle2_y = (UINT8)(_paddle2_y + -1);
    }
    if (_paddle2_y < _ball_y) {
        _paddle2_y = (UINT8)(_paddle2_y + 1);
    }
    
    // Ball movement
    _ball_x = (UINT8)(_ball_x + _ballDx);
    _ball_y = (UINT8)(_ball_y + _ballDy);
    
    // Bounces and scoring
    if (_ball_y < 16) { _ballDy = 1; }
    if (_ball_y > 148) { _ballDy = -1; }
    
    // ... more conditionals ...
    
    if (_p1Score >= 5) {
        navigate_to_scene(SCENE_GAMEOVER);
    }
}

void gameover_enter(void) {
    HIDE_SPRITES;
    cls();
    gotoxy(3, 7);
    printf("GAME OVER");
    gotoxy(3, 14);
    printf("PRESS START");
}

void gameover_frame(void) {
    if (button_pressed(J_START)) {
        navigate_to_scene(SCENE_TITLE);
    }
}
```

---

## Why Pong Shows a Blank Screen

1. **Title scene**: Works correctly
   - `cls()` clears text layer
   - `printf()` prints text to screen
   - Button press detected, navigate to game

2. **Game scene enter**: **Fails**
   - `cls()` clears text layer
   - `SHOW_SPRITES;` macro expands to LDH H, $40; OR (HL) — enables OAM rendering
   - **But no sprites are written to OAM** — the position variables (_ball_x, etc.) exist but are never moved to the hardware OAM table
   - `show_sprites_range(0, 40)` calls an empty stub function
   - Screen renders but OAM is full of garbage or zeros

3. **Game scene frame**: Runs but invisible
   - Position variables update correctly (_ball_x += 1, etc.)
   - Input is processed
   - Scene transitions work
   - **But sprites never render** because OAM was never populated

**The Title Screen Works** because it only uses text (gotoxy/printf), which works independently of OAM/sprites.

---

## Architecture Gap: v1 vs v2

### v1 Pipeline (WORKS)
- `MainCodegen` — emits sprite initialization and move_sprite calls
- `StatementCodegen` — handles sprite tile updates, animations
- `VariablesCodegen` — tracks sprite metadata (OAM slots, VRAM offsets)
- v1 DSL records full IR → extensive sprite management code

### v2 Pipeline (STUBBED)
- `GBDKPipelineV2` — orchestrates typed C AST generation
- `SceneVisitor` — converts scene lifecycle to C functions
- `ScriptOpVisitor` — only 7 ScriptOp types → rest are TODO
- `ActorVisitor` — **only generates position variables**, no OAM/VRAM mapping
- No asset pipeline integration
- No sprite/entity manager

**The Gap**: v2 was designed for simplicity (typed C AST) but deferred all complex features to "Phase 3" (asset pipeline) which hasn't been built yet.

---

## What Would Need to Be Implemented for v2 to Work

### Immediate (to make Pong playable):
1. Implement sprite rendering in `ScriptOpVisitor`:
   - `FadeOp.fadeIn` → generate `move_sprite()` calls for all actor OAM slots
   - `SetPosition` → generate `move_sprite()` call, not just variable assignment
   - `MoveBy` → same
   - Integrate actor OAM slot annotation from resource allocation

2. Implement sprite initialization in `buildHomeFile()` or scene enter:
   - Load sprite tile data via `set_sprite_data()`
   - Populate initial OAM positions via `move_sprite()`
   - Set palettes via `set_sprite_prop()`

3. Connect `ActorVisitor` to resource allocation:
   - Query OAM slot from `ActorIR.oamSlot` annotation
   - Generate sprite tile references from `ActorIR.sprite` field
   - Load VRAM tile data at init

### Medium (for feature parity with v1):
4. Implement remaining ScriptOp types in `ScriptOpVisitor`
5. Build a v2-compatible entity pool system
6. Implement collision detection DSL
7. Implement sound/audio system
8. Implement camera system with smooth following

### Long-term:
9. Integrate full asset pipeline (sprite sheet parsing, tile allocation)
10. Implement RPG subsystems (battles, NPCs, dialog)
11. Implement exploration system (dungeon floors, encounters)

---

## Code Locations

| Component | File | Status |
|-----------|------|--------|
| v2 Pipeline Entry | `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` | ✅ Implemented |
| Scene Functions | `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/SceneVisitor.kt` | ✅ Implemented |
| Script Op Visitor | `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ScriptOpVisitor.kt` | ⚠️ 7/20+ ops |
| Expression Visitor | `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ExprVisitor.kt` | ✅ Implemented |
| Actor Visitor | `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ActorVisitor.kt` | ⚠️ Position only |
| Sprite Helpers | Line 489-505 in GBDKPipelineV2 | ❌ Stubs only |
| v2 IR Nodes | `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/v2/` | ✅ Defined |
| v2 DSL | `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/ScriptBuilder.kt` | ✅ Defined |
| Pong Example | `gbkt-examples/pong/src/main/kotlin/io/github/gbkt/examples/pong/PongV2.kt` | ✅ Builds IR |

---

## Validation

The v2 IR validates correctly via `PongIRTest.kt`:
- ✅ 3 scenes (title, game, gameover)
- ✅ 3 actors (paddle1, paddle2, ball)
- ✅ 4 variables (p1Score, p2Score, ballDx, ballDy)
- ✅ Correct scene lifecycle (enter/frame/exit ops)
- ✅ Actor positions initialized
- ✅ Scene transitions recorded

**But the C code generation is incomplete.**

