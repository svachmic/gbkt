# gbkt Project Overview

## Purpose
gbkt (Game Boy Kotlin) is a DSL framework that compiles Kotlin code to GBDK-compatible C for Game Boy / Game Boy Color development.

**Pipeline:** Kotlin DSL → IR (Intermediate Representation) → C Code Generation

## Tech Stack
- **Language:** Kotlin 2.3.20
- **Build:** Gradle 9.5.1
- **JVM Target:** 21
- **Target Platform:** Game Boy / GBC via GBDK-2020

## Module Structure
```
gbkt/
├── gbkt-core/            # DSL, IR, all game constructs (platform-agnostic)
├── gbkt-backend-api/     # Backend contract (CodegenBackend interface)
├── gbkt-backend-gbdk/    # Game Boy/GBC backend (C code generation)
├── gbkt-gradle-plugin/   # Build integration (composite build)
├── gbkt-cli/             # Command-line tool
├── gbkt-intellij-plugin/ # IDE support
├── gbkt-examples/        # Example games (pong, breakout, explorer)
├── gbkt-bom/             # Bill of materials
├── LabyrinthOfTheDragon-port/ # Complex RPG example game
└── LabyrinthOfTheDragon/      # Original reference
```

## Key Source Locations
- IR nodes: `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/`
- DSL builders: `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/`
- GBDK codegen: `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/`
- RPG system: `gbkt-core/src/main/kotlin/io/github/gbkt/core/rpg/`
- World/dungeon: `gbkt-core/src/main/kotlin/io/github/gbkt/core/world/`

## Architecture Notes
- gbkt-core is monolithic due to Kotlin sealed interface constraint (all IR node implementations must be in same module)
- Backends implement `CodegenBackend` interface (validate + generate)
- Banking: `setBank(N)` switches output to bankN.c, `returnToHome()` switches back to main.c
- `currentBank` state is persistent — forgetting `returnToHome()` leaks bank assignment
