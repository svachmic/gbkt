# Diagnosis fragment — PongStepAgentTest (×1) — F2

**Plan:** 15-04 · **Requirement:** REQ-5 · **Evidence tier:** D-03b (static; OAM *count* is an internal metadata-vs-runtime truth, not a "visible on screen" verdict — Pitfall 3)

## Symptom

`PongStepAgentTest > metadata and symbol table agree on variable names` fails:
`verifyMetadataSymbolAgreement: actor 'paddle1' OAM count mismatch — expected=2, actual=1`.

## Root cause (provably-stale assertion)

`GBDKPipeline.buildMetadataFile` (gbkt-backend-gbdk/.../GBDKPipeline.kt L227-229) derives OAM
count with a deliberate 16px OAM slot:

```kotlin
// (16px OAM slot). This makes oamCount correct for emulator agent assertions.
val oamSlotHeight = if (sprite.size.height <= 8) 8 else 16
val oamCount = tilesWide * ((sprite.size.height + oamSlotHeight - 1) / oamSlotHeight)
```

Pong's paddle is `size(4, 16)` (Pong.kt:69,77): `tilesWide = ceil(4/8) = 1`, `oamSlotHeight = 16`,
`oamCount = 1 * ceil(16/16) = 1`. Ball is `size(4,4)` → `oamSlotHeight=8` → `oamCount=1`.

Generated `gbkt-examples/pong/build/gbkt/generated/game_metadata.json` (fresh `generateC`) reports
`paddle1.oamCount=1, paddle2.oamCount=1, ball.oamCount=1` (total 3).

The test (`PongStepAgentTest.kt` L53-55) still hard-codes the pre-16px-slot values
`{PADDLE1:2, PADDLE2:2, BALL:1}` / `expectedTotalOam=5`. Hence "expected=2 actual=1".

### A2 runtime-OAM guard (CONFIRMED — metadata is correct, the test drifted)

The failure message's `actual=1` is the **runtime StepAgent OAM read** itself: the harness reads
the actual hardware OAM count from the running ROM and compares it to the test's expectation. It
observed **1** — matching the metadata's `oamCount=1`. So the runtime, the metadata, and the
pipeline rule all agree on 1; only the test's hard-coded `expected=2` is stale. (A2 was "if the
ROM were 8x8 mode the paddle needs 2 OAM and metadata would be the bug" — refuted: a 4×16 sprite
in the 16px-slot rule is one hardware OAM, and the runtime read confirms 1.)

## Fix Path

**`provably-stale-assertion`** — correct the test's expectation to the proven runtime/metadata
value `{PADDLE1:1, PADDLE2:1, BALL:1}` / `expectedTotalOam=3`. NOT threshold-weakening and NOT
deletion: the expectation is realigned to the deliberately-corrected metadata (the pipeline comment
states the 16px-slot rule is intentional "to make oamCount correct for emulator agent assertions").

## Codegen-touch status (D-02 input for plan 06)

**NO `gbkt-backend-gbdk` codegen was edited** — this is a TEST-ONLY change (PongStepAgentTest.kt).
The contingency D-01 inline-codegen path was NOT taken (A2 confirmed metadata correct).

## Evidence ref

- Pipeline rule: `gbkt-backend-gbdk/.../codegen/pipeline/GBDKPipeline.kt:227-229` (16px-OAM-slot + comment)
- Generated metadata: `gbkt-examples/pong/build/gbkt/generated/game_metadata.json` → paddle1/paddle2/ball oamCount = 1
- Runtime confirmation: the StepAgent failure `actual=1` (runtime OAM read == metadata)
- Sprite dims: `gbkt-examples/pong/src/main/kotlin/io/github/gbkt/examples/pong/Pong.kt:69,77` `size(4, 16)`
