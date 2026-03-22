# Deferred Items — Phase 06.11 Labyrinth of the Dragon Port

Pre-existing issues discovered during plan execution. Out of scope for the current plan(s). Do NOT fix during current execution.

---

## Detekt Issues in Pre-existing Files

**Discovered during:** Plan 05 (Task 1 verification via `./gradlew :LabyrinthOfTheDragon-port:build`)

**Files with issues (not modified by Plan 05):**

1. `src/main/kotlin/io/github/gbkt/examples/labyrinth/rpg/StatusEffects.kt:115`
   - Rule: `LongMethod` — `defineStatusEffects` function is 97 lines (max 80)
   - Rule: `MatchingDeclarationName` — file named `StatusEffects` but top-level class is `LabyrinthStatusEffects`

2. `src/main/kotlin/io/github/gbkt/examples/labyrinth/rpg/Characters.kt:64`
   - Rule: `MatchingDeclarationName` — file named `Characters` but top-level class is `LabyrinthCharacters`

**Impact:** `./gradlew :LabyrinthOfTheDragon-port:build` fails on detekt. `compileKotlin` and `test` tasks both pass.

**Plan 05 verification uses:** `compileKotlin` and `test` (both passing). The detekt failures are pre-existing.

**Suggested fix (future plan):** Either rename the files to match the top-level declaration names, or rename the classes to match the file names. Extract `defineStatusEffects` into sub-functions to reduce its length.

---

## Auto-fixed in Plan 05

**Items.kt — `return@items LabyrinthItems(...)` type mismatch (Rule 1 - Bug)**
- **File:** `src/main/kotlin/io/github/gbkt/examples/labyrinth/rpg/Items.kt:321`
- **Error:** `return@items LabyrinthItems(...)` inside `items { }` lambda that returns `Unit` — type mismatch
- **Fix:** Declared all `ItemRef` variables as `lateinit var` outside the `items {}` block, assigned them inside, returned `LabyrinthItems(...)` after the block
- **Committed in:** Deviation fix commit
