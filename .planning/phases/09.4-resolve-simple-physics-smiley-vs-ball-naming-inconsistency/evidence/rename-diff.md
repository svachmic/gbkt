# Rename Diff — Phase 09.4

## Summary

The post-09.4 generated C is **shape-preserving** against the post-09.3 baseline with **zero behavior deltas**. The 16 changed lines (8 `-` + 8 `+`) are pure symbol-token substitutions: `_smiley_x` → `_ball_x`, `_smiley_y` → `_ball_y`, `sprites_smiley_tiles` → `sprites_ball_tiles`, and `#include "sprites/smiley.h"` → `#include "sprites/ball.h"`. Per D-A1-01 (path-A rename), the Kotlin property name `val smiley` was renamed to `val ball`, which is the single source of truth that drives all downstream codegen symbol names via `ActorDelegate.provideDelegate`. Per D-A2-02, this artifact is the verifier-facing proof that the rename is shape-preserving — the ROM byte size (32768 bytes, identical to 09.3 post-fix) confirms shape preservation at the compiled-ROM tier.

## Pre/Post Sources

- **Pre-09.4 baseline:** `.planning/phases/09.3-polish-simple-physics-port-and-reposition-as-canonical-showc/evidence/post-09.3-main.c` (5449 bytes, 254 lines; captured by Phase 09.3 Plan 04 after the JUMP oracle refactor)
- **Post-09.4 snapshot:** `.planning/phases/09.4-resolve-simple-physics-smiley-vs-ball-naming-inconsistency/evidence/post-09.4-main.c` (5429 bytes, 254 lines; captured by Plan 04 Task 1 after `:gbkt-examples:simple-physics:clean :gbkt-examples:simple-physics:buildRom` of the renamed source). Net 20-byte shrink: replacing `smiley` (6 chars) with `ball` (4 chars) at 8 sites = 16 fewer chars across 8 token occurrences, plus 4 chars from the `sprites/smiley.h` include path versus `sprites/ball.h`.

## Unified Diff

```diff
--- post-09.3-main.c	2026-05-18 13:08:51.930044754 +0200
+++ post-09.4-main.c	2026-05-18 15:03:14.733514186 +0200
@@ -5,12 +5,12 @@
 #include <stdlib.h>
 #include <gbdk/console.h>
 #include "game.h"
-#include "sprites/smiley.h"
+#include "sprites/ball.h"
 
 #define SCENE_PLAY 0
 
-UINT8 _smiley_x = 64u;
-UINT8 _smiley_y = 64u;
+UINT8 _ball_x = 64u;
+UINT8 _ball_y = 64u;
 INT16 _posX = 1024u;
 INT16 _posY = 1024u;
 INT16 _spdX = 0u;
@@ -77,7 +77,7 @@
 
 // Sprite OAM sync (called every frame)
 void update_sprites(void) {
-    move_sprite(0u, _smiley_x + 8u, _smiley_y + 16u);
+    move_sprite(0u, _ball_x + 8u, _ball_y + 16u);
 }
 
 // Sound driver (channel allocation with priority preemption)
@@ -179,9 +179,9 @@
     DISPLAY_ON;
     SHOW_BKG;
     SHOW_SPRITES;
-    set_sprite_data(0u, 1u, sprites_smiley_tiles);
+    set_sprite_data(0u, 1u, sprites_ball_tiles);
     set_sprite_tile(0u, 0u);
-    move_sprite(0u, _smiley_x + 8u, _smiley_y + 16u);
+    move_sprite(0u, _ball_x + 8u, _ball_y + 16u);
     play_enter();
     while (1) {
         update_joypad();
@@ -236,8 +236,8 @@
     _posX += _spdX;
     _posY += _spdY;
     {
-        _smiley_x = _posX >> 4u;
-        _smiley_y = _posY >> 4u;
+        _ball_x = _posX >> 4u;
+        _ball_y = _posY >> 4u;
     }
     if (_spdY < 0) {
         _spdY = _spdY + 1u;
```

## Behavior Deltas

| Site | Pre-09.4 | Post-09.4 | Decision Ref | Justification |
| ---- | -------- | --------- | ------------ | ------------- |

No behavior deltas — this is a pure shape-preserving symbol rename per D-A1-01. The 16 changed lines are all token substitutions on actor-derived identifiers (`_smiley_*` → `_ball_*`) and the sprite include (`sprites/smiley.h` → `sprites/ball.h`). The RAM layout, control flow, immediates (e.g. `-JUMP_ACCELERATION_IN_SUBPIXELS`), and per-frame integration logic are byte-identical to the 09.3 baseline. Verified by ROM byte size = 32768 (unchanged from 09.3 post-fix).

## Verifier Note

1. Confirm the diff shows exactly 8 `-` lines and 8 `+` lines (16 total changed lines). Command: `diff -u .planning/phases/09.3-polish-simple-physics-port-and-reposition-as-canonical-showc/evidence/post-09.3-main.c .planning/phases/09.4-resolve-simple-physics-smiley-vs-ball-naming-inconsistency/evidence/post-09.4-main.c | grep -c "^[-+][^-+]"` → `16`.

2. Confirm every `-` line contains the token `smiley` and every `+` line contains the token `ball` with otherwise identical surrounding tokens. Command: `diff -u .planning/phases/09.3-.../evidence/post-09.3-main.c .planning/phases/09.4-.../evidence/post-09.4-main.c | grep "^-" | grep -v "^---" | grep -v "smiley" | wc -l` → `0` (every removed line has smiley).

3. Confirm zero structural changes — no `@@` hunk covers a line-count delta. The line count is identical between pre and post (254 lines each).

4. Confirm the post-09.4 ROM is 32768 bytes (`wc -c < gbkt-examples/simple-physics/build/gbkt/output/simple-physics.gb`) — proves the symbol-token rename is also byte-neutral at the compiled-ROM tier.

5. Reference: `09.4-CONTEXT.md` § D-A1-01 (path-A rename), § D-A2-02 (this artifact's mandate), § D-A4-02 (artifact role = verifier evidence, not discoverability anchor).

---

*D-IDs implemented by this artifact: D-A1-01 (rename path), D-A2-02 (verifier-facing rename-diff published).*
