# Task Completion Checklist

When completing a task on this project, ensure:

## Always
1. Run `./gradlew build` to verify compilation
2. Run `./gradlew :gbkt-core:test` for core tests
3. If modifying codegen: run `./gradlew :LabyrinthOfTheDragon-port:generateC` to verify C output

## If Modifying Codegen
- Check bank assignments: ensure `setBank()`/`returnToHome()` are balanced
- Verify no bank overflow: check `.noi` file sizes after `buildRom`
- Ensure BANKED calling convention is maintained for non-zero bank functions

## If Modifying DSL/IR
- Ensure sealed interface exhaustive matches are updated in codegen
- Check that new IR nodes have corresponding codegen handlers

## Common Pitfalls
- Forgetting `returnToHome()` after bank-specific codegen → bank leaks
- `debugGraphics = true` corrupts tilemaps with custom tilesets
- Old generated files may persist — `clean` before regenerating
