# Code Style & Conventions

## Kotlin Style
- Standard Kotlin conventions (camelCase for functions/variables, PascalCase for classes)
- DSL builder pattern extensively used — receiver lambdas, extension functions
- Detekt for static analysis with specific exclusions for codegen/IR/DSL packages

## Deliberate Detekt Exclusions
- `**/codegen/**`: LongMethod, TooManyFunctions — C codegen produces large methods by nature
- `**/ir/**`: TooManyFunctions — 60+ operator overloads for DSL ergonomics
- `**/dsl/**`: UnusedParameter — receiver pattern has intentionally "unused" `this`
- `**/rpg/**`, `**/entity/**`: LongParameterList — RPG domain models require many fields
- Global: MagicNumber disabled (game dev constants), UnusedPrivateMember (DSL optional properties)

## Design Principles
1. DSL ergonomics over internal code metrics
2. IR as boundary between DSL and codegen
3. Domain-driven modeling for RPG types
4. Generated C code correctness over readability

## Code Generation Patterns
- `setBank(N)` / `returnToHome()` for bank switching
- `splitByBank` auto-adds BANKED to function defs in non-zero banks
- Forward declarations skipped from bank files — game.h provides prototypes with BANKED
