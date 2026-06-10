---
phase: 06-complete-gap-closure
plan: 04
subsystem: sound-codegen
tags: [sound, audio, NRxx, hUGETracker, waveform, AudioMixer, codegen]
dependency_graph:
  requires: [06-02]
  provides: [real-NRxx-register-codegen, music-scriptops, waveform-export, AudioMixer-stubs]
  affects: [GBDKPipelineV2, GBDKSystemVisitor, ScriptOpVisitor]
tech_stack:
  added: [SoundEffectDef, SoundPreset, SoundChannel, SoundRegisters, MusicPlay, MusicStop, MusicPause, MusicResume]
  patterns: [Pan-Docs-NRxx-bit-layout, visitor-dispatch, hUGETracker-integration]
key_files:
  created:
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/SoundRegisterCodegenTest.kt
    - gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/MusicCodegenTest.kt
  modified:
    - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/Types.kt
    - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/ScriptOp.kt
    - gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/ScriptOpVisitorI.kt
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SystemBuilders.kt
    - gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/GameBuilder.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/GBDKSystemVisitor.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ScriptOpVisitor.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt
    - gbkt-core/src/test/kotlin/io/github/gbkt/core/ir/IRHierarchyTest.kt
decisions:
  - SoundEffectDef stored in GameIR.soundEffects (not systems) — sound effects are data, not systems; avoids conflation with SystemIR visitor dispatch pattern
  - SoundEffectDef.fromPreset() companion factory replaces old getPresetConfig() pattern — preset-to-register mapping is a one-way IR construction, not a runtime lookup
  - hUGEDriver.h include is conditional (hasMusicOps() scan) — only added when music ops are actually used; avoids spurious header bloat in non-music games
  - hUGE_dosound() in main loop is conditional on hasMusicOps() — same rationale; music driver tick only wired when needed
  - AudioMixer stubs are no-op (defer full mixing) — full hardware channel group mixing is post-Phase-06; stubs allow compilation and linking cleanly
  - CLiteral emits 'u' suffix for non-negative values — MusicPause/Resume test checks hUGE_set_pause(1u) not hUGE_set_pause(1)
metrics:
  duration: 35 min
  completed: 2026-02-21T12:22:00Z
  tasks: 2
  files: 10
---

# Phase 06 Plan 04: Sound/Music Codegen Implementation Summary

Real Game Boy audio register codegen with Pan Docs NRxx bit layout, hUGETracker music integration, custom WAVE channel waveform export, SoundSystem visitor wiring, and AudioMixer stubs — replacing hashCode-based dummy sound IDs.

## Tasks Completed

### Task 1: A1+A3+A4 — Real NRxx Register Writes, Waveform Export, SoundSystem Wiring

**Commit:** `b6597d3`

**IR types added (Types.kt):**
- `SoundChannel` enum: PULSE1, PULSE2, WAVE, NOISE
- `DutyCycle` enum: 12.5%, 25%, 50%, 75% with `bits` field for NR11/NR21 encoding
- `EnvelopeDirection` enum: INCREASE, DECREASE
- `SweepDirection` enum: INCREASE, DECREASE
- `SweepConfig`, `EnvelopeConfig` data classes
- `SoundRegisters` data class with all channel-specific fields (frequency, duty, length, trigger, lengthEnable, envelope, sweep, waveform, noiseClockShift, noiseWidthMode, noiseDivisor, waveOutputLevel)
- `SoundPreset` enum: 10 presets (BEEP, HIT, JUMP, COIN, BUMP, WIN, LOSE, SHOOT, EXPLODE, POWERUP)
- `SoundEffectDef(id, channel, registers)` data class with `fromPreset()` companion factory

**Music ScriptOps added (ScriptOp.kt + ScriptOpVisitorI.kt):**
- `MusicPlay(songId)`, `MusicStop`, `MusicPause`, `MusicResume` — each with `accept()` visitor dispatch
- Visitor interface methods added to `ScriptOpVisitorI<T>`
- No-op stubs added to `ScriptOpInterpreter` (gbkt-core)

**DSL updated:**
- `SoundEffectBuilder` rewrites: now produces `SoundEffectDef` (not `SoundSystem`); adds `SoundRegistersBuilder` for manual register config
- `GameBuilder.soundEffect()` registers into `soundEffectDefs: MutableList<SoundEffectDef>`; convenience `soundEffect(id, preset)` overload added
- `GameBuilder.build()` includes `soundEffects = soundEffectDefs.toList()` in GameIR

**Register codegen (GBDKPipelineV2.kt):**
- `buildSoundWrapperFunction()` signature extended to `(soundId, gameIR)` — looks up `SoundEffectDef` from `gameIR.soundEffects`
- Falls back to stub comment when no def registered (backward compat for PlaySound without preset)
- New `buildNRxxRegisterWrites()` generates Pan Docs-correct NRxx writes per channel:
  - PULSE1: NR10 (sweep: time×4 | direction×3 | shift), NR11 (duty×6 | length), NR12 (vol×4 | dir×3 | pace), NR13 (freq-low), NR14 (trigger×7 | lenEnable×6 | freq-high×3)
  - PULSE2: NR21-NR24 (no sweep)
  - WAVE: NR30=0x00u disable before AUD3WAVERAM load, then NR30=0x80u re-enable, NR31 length, NR32 output level, NR33/34 freq+trigger
  - NOISE: NR41-NR44

**SoundSystem wiring (GBDKSystemVisitor.kt A4):**
- `visitSoundSystem()` returns `emptyList()` — dispatches through visitor (no silent drop via filterIsInstance)
- AudioMixer stubs: `buildAudioMixerStubs()` generates `trigger_<id>()`, `set_group_volume(group, vol)`, `mute_group(group)`, `unmute_group(group)` stub functions
- "audio_mixer" GenericSystem type dispatched to `buildAudioMixerStubs()` in `visitGenericSystem()`

**Tests: SoundRegisterCodegenTest.kt — 12 tests:**
1. BEEP NR1x register writes (not hashCode)
2. BEEP NR12 envelope bit layout (0xC3u)
3. HIT NR4x register writes (NOISE channel)
4. HIT NR42 envelope value (0xF2u)
5. HIT NR44 trigger register (0x80u)
6. WAVE channel AUD3WAVERAM + NR30-NR34
7. WAVE channel disables CH3 before loading (NR30=0x00u)
8. SoundEffectDef without PlaySound still generates wrapper
9. SoundSystem dispatched via GBDKSystemVisitor (not silently dropped)
10. JUMP (PULSE2) NR2x registers, no NR10 sweep
11. POWERUP NR10 sweep register (0x22u)
12. No hashCode() in generated C output

### Task 2: A2+A5 — Music ScriptOps, hUGETracker Integration, AudioMixer Stubs

**Commit:** `00fee5d`

**hUGETracker integration (GBDKPipelineV2.kt):**
- Added imports: MusicPlay, MusicStop, MusicPause, MusicResume, FadeOp
- `hasMusicOps(gameIR)` — recursively scans all scene ops for any music op
- `containsMusicOp(ops)` — recursive helper for IfOp/WhileOp/ForOp/FadeOp/ShowMenu nesting
- `buildHomeFile()`: conditionally adds `<hUGEDriver.h>` to includes when `hasMusicOps()` is true
- `buildMainFunction()`: conditionally adds `hUGE_dosound()` call in game loop body when `hasMusicOps()` is true

**ScriptOpVisitor.kt (already implemented from previous plans):**
- `visitMusicPlay` → `CRawCode("hUGE_init(&song_<songId>);")`
- `visitMusicStop` → `CRawCode("hUGEDriver_mute_channel(0); ... hUGEDriver_mute_channel(3);")`
- `visitMusicPause` → `CExprStatement(CCall("hUGE_set_pause", listOf(CLiteral(1))))`
- `visitMusicResume` → `CExprStatement(CCall("hUGE_set_pause", listOf(CLiteral(0))))`

**Tests: MusicCodegenTest.kt — 12 tests:**
1. MusicPlay → hUGE_init(&song_theme) in bank1.c
2. MusicStop → hUGEDriver_mute_channel(0/1/2/3) in bank1.c
3. MusicPause → hUGE_set_pause(1u) in bank1.c
4. MusicResume → hUGE_set_pause(0u) in bank1.c
5. hUGEDriver.h included in main.c when MusicPlay present
6. hUGE_dosound() in main loop when MusicPlay present
7. No hUGEDriver.h when no music ops
8. No hUGE_dosound() when no music ops
9. MusicStop alone triggers hUGEDriver.h include (any music op triggers it)
10. AudioMixer generates set_group_volume stub
11. AudioMixer generates mute_group + unmute_group stubs
12. AudioMixer generates trigger_mixer no-op function

## Test Results

- `gbkt-backend-gbdk:test`: 244 tests, 0 failures
- `gbkt-core:test`: passes
- Pre-existing spotless formatting issue in `gbkt-lang:VariableBuilders.kt` fixed with `spotlessApply` (Rule 1 auto-fix)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Pre-existing spotless formatting violation in VariableBuilders.kt**
- **Found during:** Test suite execution
- **Issue:** `VariableBuilders.kt` had line-length violations from a previous linter reformat; `spotlessKotlinCheck` failed, blocking test compilation
- **Fix:** Ran `./gradlew :gbkt-lang:spotlessApply` to normalize formatting
- **Files modified:** `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/VariableBuilders.kt`
- **Commit:** (inline with spotlessApply, no separate commit needed — code logic unchanged)

**2. [Rule 1 - Bug] CLiteral 'u' suffix causing test assertion mismatch**
- **Found during:** Task 2 test execution
- **Issue:** CEmitter emits `CLiteral(1)` as `"1u"` (unsigned suffix for non-negative values); test checked `hUGE_set_pause(1)` and `hUGE_set_pause(0)`, which never matched
- **Fix:** Updated MusicCodegenTest assertions to check `hUGE_set_pause(1u)` and `hUGE_set_pause(0u)`
- **Files modified:** `MusicCodegenTest.kt`

**3. [Rule 2 - Missing functionality] GameIR.soundEffects already existed**
- **Found during:** Task 1 implementation
- **Issue:** `GameIR.soundEffects: List<SoundEffectDef>` was already present in HEAD (committed by a previous session); no need to add it again
- **Action:** Skipped re-adding the field; used the existing field directly

## Self-Check: PASSED

**Files created:**
- `gbkt-backend-gbdk/src/test/.../SoundRegisterCodegenTest.kt` — FOUND
- `gbkt-backend-gbdk/src/test/.../MusicCodegenTest.kt` — FOUND

**Commits:**
- `b6597d3` feat(06-04): implement real NRxx register codegen — FOUND
- `00fee5d` feat(06-04): add hUGETracker integration — FOUND
