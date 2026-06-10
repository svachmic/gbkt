# Phase 1: IR Foundation and DSL - Context

**Gathered:** 2026-02-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Sealed IR hierarchy and DSL recording context. All example games representable as IR. The DSL is a clean-break redesign (not backward-compatible with existing syntax). Core IR + RPG genre package created. Three example games (Pong, Breakout, Explorer) defined as DSL producing valid IR.

</domain>

<decisions>
## Implementation Decisions

### DSL syntax direction
- **Clean break** from existing DSL syntax. No backward compatibility. Fresh design.
- **Declarative, state-driven** style inspired by Jetpack Compose. Describe what the game IS, not what it DOES.
- **Pure builder functions** (scene {}, entity {}, game {}). No annotation processing or compiler plugins.
- **Explicit composition** of small, testable pieces. No convention-based file discovery or black-box magic. It must be clear from code, DX, or tooling how files are separated and assembled.
- **Declarative navigation** with routes for scene transitions (like Compose Navigation). Framework manages scene stack.
- **High-level abstractions by default** (entities, images, movement) with **escape hatches for direct hardware access** when advanced users need it.

### Example game scope
- **Pong (beginner):** Polished minimum. Two paddles, ball, score, win condition + title screen, game-over screen, sound effects, 2-player. A complete tiny game.
- **Breakout (intermediate):** Multi-entity management + levels. Brick grid (many entities), power-ups, level progression. Tests entity management at scale.
- **Explorer (advanced):** Simple dungeon crawler with grid movement, multiple floors, keys/doors, torch gauge + **simple turn-based combat** (attack/defend/item menu, HP/ATK/DEF stats). No deep RPG (no abilities, equipment, monster AI).

### RPG generalization / Package architecture
- **BOM (Bill of Materials) approach.** Developers select packages: core (DSL+IR), genre packages (rpg, platformer, puzzle), and backend (gameboy).
- **Core IR** handles generic game concepts: entities, scenes, input, sprites, tilemaps, navigation.
- **Genre packages** (e.g., gbkt-rpg) add domain-specific IR nodes and DSL extensions on top of core.
- **Phase 1 creates both core IR and RPG genre package.** Explorer uses the RPG package for simple turn-based combat.
- **Design core IR with RPG, Platformer, and Puzzle genres in mind** to ensure it's truly generic.
- **Each example game is a separate Gradle module** with explicit package dependencies, demonstrating the BOM selection pattern.
- **LabyrinthOfTheDragon kept as reference** (uses old pipeline). Not migrated or deleted in Phase 1.

### Error experience
- **Two-layer error checking:** Obvious errors at DSL recording time (type mismatches, immediate validation), cross-cutting errors at IR validation pass (dangling references, constraint violations).
- **Fail fast:** Stop at first error. Developer fixes one issue at a time.
- **Compiler-style error format:** file:line:col: error: message. Familiar, IDE-clickable, structured.
- **"Did you mean?" suggestions** included from the start. Fuzzy-match against valid targets for typo detection.

### Claude's Discretion
- State management pattern for game variables (Compose-like state() vs property delegates vs other)
- Combat boundary between core collision and genre-specific combat systems
- Whether Pong/Breakout use only core IR or can pull genre packages if it makes code cleaner
- DSL extensibility mechanism for genre packages (full DSL extension vs IR + utilities)
- Sealed type constraint resolution for multi-module IR (extension mechanism design)

</decisions>

<specifics>
## Specific Ideas

- Jetpack Compose is the primary reference for DSL feel and patterns
- "No black magic convention or black-box obscure discovery" — composition must be explicit and visible in code
- BOM pattern like Spring Boot starters or Jetpack library groups — pick what you need
- Explorer should prove the genre package concept works (core + rpg dependency)
- Pong/Breakout should demonstrate that core alone is sufficient for simple games

</specifics>

<deferred>
## Deferred Ideas

- Platformer genre package — design noted, implementation deferred beyond Phase 1
- Puzzle genre package — design noted, implementation deferred beyond Phase 1
- LabyrinthOfTheDragon migration to new pipeline — keep as reference, migrate later
- IDE support for DSL (IntelliJ plugin) — separate tooling concern

</deferred>

---

*Phase: 01-ir-foundation-and-dsl*
*Context gathered: 2026-02-17*
