# Phase 09 — Deferred Items

Items discovered during phase execution that are OUT of scope for Phase 09 per the
deviation-rules SCOPE BOUNDARY ("Only auto-fix issues DIRECTLY caused by the current
task's changes. Pre-existing warnings, linting errors, or failures in unrelated files
are out of scope.").

These are logged here for future-phase consideration, NOT fixed in this phase.

## DEFERRED-09-01 — gbkt scaffolding emits 4 pre-existing SDCC warnings

**Discovered:** Plan 05 (Task 1, when running `:gbkt-examples:simple-physics:buildRom
--info`).

**Symptoms (from `evidence/buildrom-log.txt`):**

```
main.c:57:  warning 84: 'auto' variable '_d' may be used before initialization
main.c:74:  warning 85: in function show_sprites_range unreferenced function argument : 'from'
main.c:74:  warning 85: in function show_sprites_range unreferenced function argument : 'to'
main.c:204: warning 126: unreachable code
```

**Cause:** Three pre-existing gbkt-emitted scaffolding patterns:

1. `delay_frames(UINT8 n)` declares `UINT8 _d;` without initialiser, then loops
   `for (; _d < n; _d++)`. SDCC 84 flag.
2. `show_sprites_range(UINT8 from, UINT8 to)` is a stub — body is a comment, parameters
   never referenced. SDCC 85 flag × 2.
3. `main()` has an infinite `while (1)` loop followed by `return;`. SDCC 126 flag
   (unreachable code after the loop).

**Verified pre-existing:** the identical four warnings fire on
`:gbkt-examples:pong:buildRom --info` (Plan 05 control build). Out of scope for the
simple_physics codegen-quality oracle (D-09 part 1), which is about whether the *port
itself* is clean, not whether *the gbkt platform's pre-existing scaffolding* is clean.

**Status:** Deferred. Seed candidate for a future codegen-hygiene phase. Candidate
fixes:

- `delay_frames`: emit `UINT8 _d = 0u;` (one-byte init).
- `show_sprites_range`: silence with `(void)from; (void)to;` or remove the stub
  entirely (it has been semantically replaced by `update_sprites()` driven OAM sync).
- `main`: drop the trailing `return;` after the infinite loop, or mark `main` as
  non-returning.

Estimated impact: 0–4 bytes of HOME code per game; uniform across all gbkt examples;
unlocks a "zero SDCC warnings across the entire example suite" bar.

**Owner:** None assigned — surface to a future gbkt-codegen-hygiene phase.

## DEFERRED-09-02 — single-scene games force MBC5 due to bank-1 default

**Discovered:** Plan 05 (Task 1, observed during `buildRom`).

**Symptom (from `evidence/buildrom-log.txt`):**
```
Cartridge upgraded from ROM_ONLY to MBC5 (banking detected)
Banking: 2 banks (highest bank: 1), MBC: 0x19
```

**Cause:** gbkt's `BankingConfig` default places scene code in bank 1 even for trivial
single-scene games. The reference `simple_physics` ROM is single-bank ROM_ONLY (MBC
0x00).

**Status:** Deferred. Capturing this as a Phase 9.1 candidate (per
`09-RESEARCH.md` §"Risks 4 — default cartridge config may emit unneeded bank-switching
for a single-bank game"). Would also unlock a small further `l__CODE` reduction (bank
trampolines + MBC bookkeeping). Not load-bearing for D-09 PASS — gbkt is already at
1.025× reference.

**Owner:** None assigned — Phase 9.1 candidate.
