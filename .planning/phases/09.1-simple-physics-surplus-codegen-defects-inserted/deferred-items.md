# Phase 09.1 — Deferred Items

Items discovered during phase execution that are OUT of scope per the deviation-rules
SCOPE BOUNDARY ("Only auto-fix issues DIRECTLY caused by the current task's changes.
Pre-existing warnings, linting errors, or failures in unrelated files are out of scope.").

## DEFERRED-09.1-01 — dungeon/explorer/rpg-lite buildRom fail with SDCC error 91 (const mismatch)

**Discovered:** Plan 02 (Task 3, smoke regression) and independently re-observed in Plan 03 (Task 5, smoke regression).

**Symptoms:**
```
main.c:60: error 91: extern definition for '_char_adventurer_hp' mismatches with declaration.
game.h:66: error 177: previously defined here
```
(also reproduced for `_char_hero_hp` and other character stat fields: sp, atk, def, matk, mdef, agl)

**Cause:** RPG character stats codegen emits `const UINT8 _char_adventurer_hp = 25u;` in `main.c`
but `game.h` declares `extern UINT8 _char_adventurer_hp;` (without `const`). SDCC treats these as
mismatched declarations (const vs non-const extern). The same pattern repeats across all RPG character
stat fields (hp/sp/atk/def/matk/mdef/agl).

**Verified pre-existing:** Same errors occur at the base commit (57028f50) before any Phase 9.1 changes.
Unrelated to SDCC warnings 84/85/126 (Plan 02) and unrelated to the banking analysis fix (Plan 03).

**Affected examples:** dungeon, explorer, rpg-lite (all examples using RPG character definitions via
`gbkt-genre-rpg`).

**Root cause location:** RPG character stats extern declaration generator in the header builder
(`CharacterVisitor` or `RpgCodegen` in `gbkt-backend-gbdk`). The `game.h` prototype generator drops
`const` from character variable externs when the globals are declared as `const` initializer variables
in `main.c`.

**Fix direction:** Header builder should detect `const` initializer `CVarDecl`s and emit
`extern const UINT8 ...` prototypes to match. Alternatively, remove `const` from the
character stat declarations (they may be mutable during leveling).

**Owner:** Future phase (Phase 10+ or dedicated codegen-hygiene plan).
