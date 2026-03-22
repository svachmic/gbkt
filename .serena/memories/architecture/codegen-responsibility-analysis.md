# Codegen Responsibility Architectural Analysis - IN PROGRESS

## Initial Findings (2026-02-25)

### GBDKPipelineV2 Symbols (generate() orchestrates top-level)
**Key Methods Found:**
- `generate()` - Main entry point
- `buildCollisionArrayDecl()` - Collision array declarations
- `buildCollisionFunction()` - Collision function generation
- `buildCollisionDispatchFunction()` - Dispatch logic
- `buildCollisionCodegen()` - Overall collision codegen
- `buildNpcCollisionFunctions()` - NPC-specific collision functions
- `buildCFiles()` - Orchestrates C file building
- `buildZoneData()`, `buildZoneDefines()` - Zone-related
- `buildStructTypedefs()`, `buildHeaderFile()` - Type system
- `buildHomeFile()`, `buildSceneFile()` - File organization
- `buildForwardDeclarations()`, `buildTrampolinesForScene()` - Function management
- `buildInputHelperFunctions()`, `buildSpriteHelperFunctions()` - Per-actor features
- `buildSoundFunctions()`, `buildDialogFunctions()`, `buildMenuFunctions()` - System features
- `buildRpgCharStatVars()`, `buildInventoryFunctions()` - RPG features
- `buildMainFunction()` - Entry point

### GBDKSystemVisitor Symbols (visitor pattern for IR)
**Key Methods Found:**
- `visitCameraSystem()` - Camera generation
- `visitSaveSystem()` - Save system
- `visitSoundSystem()` - Sound system
- `visitExplorationSystem()` - Exploration/dungeon
- `buildEntityCollisionFunctions()` - Entity collision
- `buildEncounterCheckFunction()` - Encounters
- `buildZoneLoadFunction()` - Zone loading
- `buildZoneTransitionFunction()` - Zone transitions
- `visitDialogSystem()` - Dialogs
- `visitGenericSystem()` - Generic system handling
- `buildAudioMixerFunctions()` - Audio mixing
- `visitPathfindingSystem()` - Pathfinding
- `visitCombatEngineSystem()` - Combat engine
- `buildPuzzleObjectFunctions()` - Puzzle objects
- `buildPuzzleRevealFunction()`, `buildPuzzleHideFunction()` - Puzzle control

## Key Architectural Questions - NEED TO ANSWER:

1. **Does GBDKPipelineV2 call GBDKSystemVisitor, or duplicate logic?**
2. **Are there instance methods or static companion object methods?**
3. **What triggers buildNpcCollisionFunctions() in Pipeline?**
4. **What is the relationship between buildCollisionCodegen() and NPC collision?**
5. **Does GBDKSystemVisitor.buildEntityCollisionFunctions() also handle NPCs?**

## NEXT STEPS:
- Read GBDKPipelineV2.kt to see buildNpcCollisionFunctions() implementation
- Read GBDKSystemVisitor.kt to see buildEntityCollisionFunctions() implementation
- Check NpcCollisionCodegen.kt usage
- Read ActorVisitor.kt to see per-actor movement codegen
- Check which features are Pipeline vs SystemVisitor
- Find any architecture docs in codegen/ subdirs
