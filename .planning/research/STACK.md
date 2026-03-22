# Stack Research

**Domain:** Kotlin DSL-to-C compiler pipeline for Game Boy / retro console development
**Researched:** 2026-02-17
**Confidence:** MEDIUM-HIGH (core Kotlin/JVM stack HIGH; niche domain tooling MEDIUM)

---

## Context: What Already Exists vs What's Needed

The codebase already uses Kotlin 2.3.0 / Gradle 9.0 / JVM 21. This research focuses on
what needs to change or be added for the rebuild — structured C AST, compiler passes,
bin-packing, and a production-grade asset pipeline.

---

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Kotlin JVM | 2.3.0 | Language for DSL, IR, all compiler passes | Already in use. Sealed interfaces enable exhaustive pattern matching across IR nodes — this is the architectural foundation. K2 compiler delivers 1.94x faster builds. |
| Gradle | 9.0 | Build system, plugin, task orchestration | Already in use. Gradle 9 Kotlin DSL upgrades to Kotlin 2.2.x runtime; task incremental inputs/outputs API is the right model for the asset pipeline. |
| JVM target | 21 | Runtime for all compiler passes and tests | Already in use. JVM 21 provides virtual threads (if needed for parallel asset processing) and stable pattern matching support. |

### Structured C Code Generation (replaces StringBuilder)

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Custom C AST (internal) | N/A | Structured, typed C output model | No general-purpose C AST builder library exists for the JVM. KotlinPoet generates Kotlin/Java — not C. The correct approach is a lightweight custom C IR (`CFile`, `CFunction`, `CBlock`, `CStatement`, `CExpression`) with a dedicated `CWriter` visitor that prints to string. This is ~300-500 lines and gives you full control over GBDK-specific constructs (`#pragma bank`, `BANKED`, `static inline`). |

**Rationale for custom C AST over strings:** The current `GBDKCodeGenerator` uses `StringBuilder.appendLine()` throughout. Every `PRAGMA_BANK` split, `BANKED` keyword injection, and prototype extraction is a post-processing regex hack. A typed C AST eliminates the `splitByBank()` method entirely — bank assignment becomes a property on `CFunction`, not a pragma injected into text. Extraction of extern declarations and function prototypes becomes a tree walk, not regex.

**What NOT to use:**
- `KotlinPoet` — generates Kotlin/Java syntax only, not C
- `JavaPoet` — deprecated since 2020, Java-only
- String templates / heredocs — what the codebase already has; causes the `splitByBank()` complexity

### Compiler Analysis Passes

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Kotlin sealed `when` (built-in) | 2.3.0 | Exhaustive IR visitor pattern | The codebase already uses `sealed interface IRStatement`. Passes should be implemented as `IRWalker`-style functions or simple recursive `when` dispatches. Kotlin's exhaustive `when` guarantees every new IR node forces a compiler error in every existing pass — zero chance of silent omissions. |
| Custom `CompilerPass<T>` interface | N/A | Pipeline stage abstraction | Define a `CompilerPass<Input, Output>` interface and a `CompilerPipeline` that chains passes. Each pass gets typed input (e.g. `Game`), produces typed output. Passes: `ValidationPass`, `BankAllocationPass`, `OAMPlannerPass`, `VRAMPlannerPass`, `ConstantFoldingPass`, `DeadCodePass`. |

### Bank / Resource Allocation

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Custom FFD bin-packing (internal) | N/A | ROM bank allocation for code sections | The codebase already has `BankAllocator` for tile data using first-fit. The rebuild should extend this with a full First-Fit-Decreasing (FFD) bin-packing algorithm that handles both code sections (functions) and data (tiles, maps, strings) in a unified pass. No external library exists for this on JVM that handles GBDK-specific constraints (16 KB banks, HOME bank must hold ISR + main, BANKED calling convention). |
| GBDK `bankpack` CLI tool | 4.x | Auto-bank packing during ROM compilation | GBDK 2020's `bankpack` tool (invoked by `lcc -autobank`) assigns banks automatically via `#pragma bank 255` markers. For code-only auto-assignment in simple games, this is sufficient. The custom `BankAllocationPass` is for compile-time analysis that feeds into multi-file split and pre-validation. Both should coexist: gbkt plans banks, bankpack confirms at link time. |

### Asset Pipeline

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Java `ImageIO` (JDK built-in) | JDK 21 | PNG loading for tile conversion | Zero dependency. `BufferedImage` + `ImageIO.read()` is sufficient for reading PNG files and accessing ARGB pixel data for 2bpp conversion. Verified: the JDK 21 `javax.imageio` supports PNG natively. |
| scrimage-core | 4.3.6 (Feb 2025) | Image manipulation before tile conversion | Use for: resizing sprite sheets to 8px multiples, palette reduction, pixel format normalization before 2bpp encoding. JVM-native, Kotlin-friendly API, actively maintained (last release Feb 5 2025). Only pull in if you need image manipulation beyond raw pixel access — for pure GB 2bpp conversion, `ImageIO` alone is sufficient. |
| RGBDS `rgbgfx` CLI | 4.x | Reference implementation for 2bpp tile encoding | The canonical Game Boy image converter. Invoke as an external process for high-fidelity conversion OR port its 2bpp encoding logic into Kotlin (~100 lines). Porting is preferred for hermetic builds (no RGBDS install required). The 2bpp format: each 8x8 tile = 16 bytes; each row = 2 bytes (bit plane 0, bit plane 1). |
| Kotlin `org.json` | 20251224 | Tiled TMX/JSON map file parsing | Already a dependency in `gbkt-core`. Sufficient for parsing Tiled `.tmx` and `.json` export formats. |

### Testing

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Kotest | 6.1.3 (Feb 2025) | Test framework with property-based testing | Already used (`kotest-property` in `libs.versions.toml`). Kotest 6.x is a major release with JUnit 6 runner support. Property-based testing (`Arb`) is the right tool for verifying bank-packing correctness across hundreds of random game configurations. |
| `kotlin-test` | 2.3.0 | Unit assertions for IR/codegen tests | Already used. Lightweight alternative to Kotest for simple assertion-based tests. |
| `kotlinx-coroutines-test` | 1.10.2 | Async test support for parallel asset pipeline | Already used. Version 1.10.2 is current stable. |
| JVM simulation context | N/A | Game logic testing without ROM compilation | The codebase has a `SimulationContext` approach. For the rebuild: implement a `GameSimulator` that executes the IR statements directly on JVM (no C, no ROM) to test RPG formulas, encounter tables, and game logic in sub-millisecond unit tests. |

### Code Quality

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| detekt | 1.23.7 | Static analysis | Already configured with `detekt.yml`. Keep existing exclusions (codegen LongMethod, IR TooManyFunctions) — they represent legitimate architectural decisions documented in CLAUDE.md. |
| Spotless + ktfmt | current | Code formatting | Already configured. `ktfmt().kotlinlangStyle()` matches JetBrains style. |
| Kover | current | Coverage reporting | Already configured in `gbkt-core/build.gradle.kts`. |
| SonarCloud | current | Quality gate for CI | Already configured. |

---

## Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `org.json` | 20251224 | Tiled map JSON parsing | Always — already a dependency, handles `.tmx` JSON export |
| `kotest-property` | 6.1.3 | Property-based testing for bank packing and IR transformations | In tests for `BankAllocationPass`, `ConstantFoldingPass`, any algorithm that needs coverage across large input spaces |
| `kotlinx-coroutines-test` | 1.10.2 | Coroutine testing utilities | Only if async processing is added to asset pipeline (parallel tile encoding across CPU cores) |
| `scrimage-core` | 4.3.6 | Image manipulation pre-processing | Only if you need sprite sheet resizing or palette normalization beyond what ImageIO provides |

---

## Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| GBDK 2020 lcc | C→ROM compilation via Gradle `CompileRomTask` | External dependency; detect via `GBDK_HOME` env var or common paths. Already implemented. |
| GBDK `bankpack` | Auto-bank assignment at link time | Used by `lcc -autobank`; gbkt's `BankAllocationPass` pre-computes bank assignments to validate before compilation |
| GBDK `romusage` | Report bank utilization in compiled ROM | Invoke after `buildRom` to verify bank assignments match predictions |
| `rgbgfx` (optional) | Reference PNG→2bpp converter | Use to validate gbkt's own tile encoder output during testing |
| mGBA emulator | ROM testing | Already detected by `RunEmulatorTask` |

---

## Installation

```kotlin
// gradle/libs.versions.toml additions for the rebuild
[versions]
kotest = "6.1.3"          # Upgrade from 5.9.1
scrimage = "4.3.6"         # New addition (optional)
coroutines = "1.10.2"      # Upgrade from 1.9.0

[libraries]
kotest-property  = { module = "io.kotest:kotest-property",            version.ref = "kotest" }
kotest-runner    = { module = "io.kotest:kotest-runner-junit5",        version.ref = "kotest" }
scrimage-core    = { module = "com.sksamuel.scrimage:scrimage-core",   version.ref = "scrimage" }
coroutines-test  = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
```

```kotlin
// gbkt-core/build.gradle.kts
dependencies {
    implementation(libs.json)
    // scrimage only if you need image manipulation in the JVM pipeline
    // implementation(libs.scrimage.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.runner)   // Add for full Kotest runner
    testImplementation(libs.coroutines.test)
    testImplementation(project(":gbkt-backend-gbdk"))
}
```

---

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Custom C AST | String-based `StringBuilder` (current) | Never for the rebuild — the `splitByBank()` regex complexity is proof this doesn't scale |
| Custom C AST | KotlinPoet | If you were generating Kotlin/JVM code instead of C |
| Custom FFD bin-packing | GBDK `bankpack` auto-assignment only | For simple games under 4 banks where bank overflow is unlikely — but you lose pre-compile validation |
| `ImageIO` (JDK) + custom 2bpp | `rgbgfx` process call | If hermetic builds are not a requirement and you want pixel-perfect RGBDS compatibility |
| `ImageIO` (JDK) + custom 2bpp | scrimage | If you need image manipulation (resizing, format conversion) before pixel encoding |
| Kotlin `when` exhaustive dispatch | Visitor pattern with `accept()` | If you add user-extensible IR nodes (not currently planned); sealed interfaces are strictly better when all implementations are known |
| Kotest 6.1.3 | kotlin-test only | For trivial tests — but property testing is essential for validating packing algorithms |

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `KotlinPoet` for C generation | Generates Kotlin/Java syntax. Has no C constructs. | Custom typed C AST (`CFile`, `CFunction`, `CStatement`, `CExpression`) |
| `JavaPoet` | Deprecated since October 2020, unmaintained. | KotlinPoet (for Kotlin/Java), custom AST (for C) |
| `kastree` | Abandoned, last commit 2020, requires full Kotlin compiler on classpath. | Not relevant for C output; use kotlin-reflect sparingly if you need Kotlin introspection |
| ANTLR / KSP for the internal IR | gbkt doesn't parse source — it records DSL execution as IR. KSP/ANTLR solve different problems (annotation processing, grammar parsing). | The existing recording context + sealed IR hierarchy |
| Global mutable state in codegen | The current `GBDKCodeGenerator` uses `internal var currentBank = 0` as mutable field. This makes passes non-reentrant and hard to test. | Immutable pass results: each `CompilerPass` takes input and returns new output, no mutation |
| String regex for `splitByBank()` | The current bank-splitting uses `funcDefPattern`, `funcDeclPattern`, regex parsing of generated C text. Fragile, maintenance-heavy. | Typed `CFunction(bank = N)` property in C AST; bank files become a simple filter/group pass |
| `scrimage` for pure pixel access | Heavy dependency if you only need pixel ARGB values from PNG. | `ImageIO.read(file).getRGB(x, y)` is zero-dependency and sufficient |

---

## Stack Patterns by Variant

**If building a simple game (pong/breakout — <4 banks):**
- Skip the `BankAllocationPass` altogether; use GBDK `bankpack` auto-assignment
- Single-file C output is fine; skip `generateMultiFile()`
- Asset pipeline needs only 2bpp tile encoder; no scrimage

**If building a complex RPG (explorer/labyrinth — 16-31 banks):**
- `BankAllocationPass` is mandatory for pre-compile validation
- Multi-file split must be driven by C AST bank property, not regex
- scrimage useful for sprite sheet normalization
- String bank allocation with FFD (already implemented in `BankAllocator.allocateForStrings()`)

**If adding a new backend (GBA, NES):**
- The C AST layer is shared only if the target is also C
- Each backend gets its own `CodegenVisitor<CFile>` that walks the same IR but emits different C idioms
- Bank allocation is backend-specific (GBA has IWRAM/EWRAM, not bank-switched ROM)

---

## Version Compatibility

| Package | Compatible With | Notes |
|---------|-----------------|-------|
| Kotlin 2.3.0 | Gradle 9.0 | Gradle 9 Kotlin DSL uses Kotlin 2.2.x runtime — compatible |
| Kotlin 2.3.0 | JVM 21 | Explicit JVM toolchain target in each module's `kotlin { jvmToolchain(21) }` |
| kotest 6.1.3 | JUnit 5 | Requires `kotest-runner-junit5` on classpath; confirmed compatible |
| kotest 6.1.3 | kotlin-test 2.3.0 | Can coexist; use kotest for property tests, kotlin-test for simple assertions |
| scrimage 4.3.6 | JVM 11+ | Requires JVM 11 minimum — JVM 21 is fine |
| org.json 20251224 | any JVM | No dependencies, compatible with all JVM versions |
| Gradle 9.0 | JVM 21 | Gradle 9 requires JVM 17 minimum; JVM 21 fully supported |

---

## Architecture Note: Why No External C AST Library

A common question is why not use an existing C code generation library. Investigation shows:

1. No actively maintained JVM C AST library exists. The closest is tree-sitter (C grammar parsing, not generation) and LLVM bindings (heavy, not appropriate for a compiler targeting a 4 MHz Z80).

2. The C subset needed for GBDK is small: `#include`, `#define`, `typedef struct`, global variables, `static inline` functions, banked functions, `#pragma bank`, and basic control flow. A complete typed model is ~400 lines of Kotlin data classes.

3. GBDK-specific constructs (`BANKED`, `#pragma bank N`, MBC addressing) require customization that no general library provides.

4. The rebuild's entire bank splitting problem (`splitByBank()` = 250 lines of regex) disappears when `CFunction` carries its `bank: Int` property. The "split" becomes `files.groupBy { it.bank }`.

**Verdict:** Build a minimal internal C AST. It is the right tool for this domain, and it's ~2 days of work vs. months of fighting a general library.

---

## Sources

- Kotlin 2.3.0 blog post — key JVM changes (new API for registering generated sources, Java 25 support): https://blog.jetbrains.com/kotlin/2025/12/kotlin-2-3-0-released/
- KotlinPoet 2.2.0 latest release, May 2025: https://github.com/square/kotlinpoet/releases
- JavaPoet deprecation notice (October 2020): https://github.com/square/javapoet
- Kotest v6.1.3 stable release (February 5, 2025): https://github.com/kotest/kotest/releases
- scrimage v4.3.6 stable release (February 5, 2025): https://github.com/sksamuel/scrimage/releases
- kotlinx-coroutines 1.10.2 current stable: https://github.com/Kotlin/kotlinx.coroutines/releases
- GBDK 2020 bankpack documentation: http://gbdk.org/docs/api/docs_toolchain.html
- GBDK 2020 ROM/SRAM Banking: http://gbdk.org/docs/api/docs_rombanking_mbcs.html
- Gradle 9.0 upgrade guide (Kotlin DSL breaking changes): https://docs.gradle.org/current/userguide/upgrading_major_version_9.html
- Game Boy OAM specification (Pan Docs): https://gbdev.io/pandocs/OAM.html
- RGBDS rgbgfx 2bpp format reference: https://rgbds.gbdev.io/docs/master/rgbgfx.1
- gbspack (GB Studio bank packing reference implementation): https://github.com/chrismaltby/gbspack
- Existing BankAllocator.kt (FFD for strings, first-fit for tiles): `/gbkt-core/src/main/kotlin/io/github/gbkt/core/assets/BankAllocator.kt`
- Existing GBDKCodeGenerator.kt (current string-based approach): `/gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/GBDKCodeGenerator.kt`

---
*Stack research for: gbkt Kotlin DSL-to-C compiler pipeline (Game Boy / GBDK)*
*Researched: 2026-02-17*
