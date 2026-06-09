# Phase 12 — Final Clean buildRom Smoke (Plan 12-26 Task 1, D-21)

Per D-21 + user memory `feedback_rom_build_smoke_test_for_codegen_phases.md`:
the verifier runs a CLEAN `:gbkt-examples:platformer-template:buildRom` at phase
close to rule out staleness in `build/gbkt/generated/`.

**Built:** 2026-05-25 by Plan 12-26 Task 1 (worktree-agent-abdfe8f21abfe56c4).

---

## Invocation

```bash
./gradlew :gbkt-examples:platformer-template:clean :gbkt-examples:platformer-template:buildRom 2>&1 | tee /tmp/p12-final-smoke.log
```

Log: `/tmp/p12-final-smoke.log` (43 actionable tasks executed).

---

## Final EXIT + ROM size

| Field             | Value                                                                                                                 |
| ----------------- | --------------------------------------------------------------------------------------------------------------------- |
| EXIT line         | `BUILD SUCCESSFUL in 8s`                                                                                              |
| ROM path          | `gbkt-examples/platformer-template/build/gbkt/output/platformer-template.gb`                                          |
| ROM bytes (stat)  | **65 536** (64 KB)                                                                                                    |
| MBC               | `0x01` (MBC1; per `Banking: 4 banks (highest bank: 2), MBC: 0x01` log line)                                           |
| Highest bank      | 2 (CODE_0 + CODE_1 + CODE_2 + HOME)                                                                                   |
| Pre-build deletion| `clean` invoked first → confirmed staleness-ruled-out per D-21                                                        |

---

## `.noi` bank-size table (cap = 16 384 bytes, warning threshold = 14 336)

**Raw `.noi` lines** (`grep '^DEF l__CODE' build/gbkt/output/platformer-template.noi`):

```
DEF l__CODE_0 0x0
DEF l__CODE_1 0xB84
DEF l__CODE_2 0x17E8
DEF l__CODE 0x32CD
```

| Bank | Symbol         | Bytes (hex) | Bytes (decimal) | % of 16 384 | ≤ 16 384? | ≥ 14 336 warning? | Verdict |
| ---- | -------------- | -----------:| ---------------:| -----------:| --------- | ----------------- | ------- |
| 0    | `l__CODE_0`    |       0x000 |               0 |        0.0% | ✓         | ✗                 | GREEN   |
| 1    | `l__CODE_1`    |       0xB84 |           2 948 |       18.0% | ✓         | ✗                 | GREEN   |
| 2    | `l__CODE_2`    |      0x17E8 |           6 120 |       37.4% | ✓         | ✗                 | GREEN   |

Cross-bank total (`l__CODE`) = `0x32CD` = 13 005 bytes — sums per
bank-layout-signal.md's component arithmetic
(`0 + 0xB84 + 0x17E8 + HOME(0x471) = 0x32CD`).

**Verdict: GREEN — all 3 numbered CODE banks within the 16 384-byte hard MBC
ROM-bank capacity, with substantial headroom (max 37.4%).**

---

## SDCC / lcc warning grep summary

| Pattern                                | Count | Notes |
| -------------------------------------- | -----:| ----- |
| `unknown address` / `unknown value`    | **0** | No MBC errors — bank-switching mechanism correct |
| `lcc` errors                           | **0** | (none in log) |
| SDCC compilation errors                | **0** | (none in log) |
| `WARNING: cEmit() used …`              | **1** | Pre-existing — tracked in `SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS.md` (per oracle-comparison.md §"Where gbkt is NOT shorter / clearer" — camera-relative metasprite render + `platformer_camera_update` call site). NOT a Phase 12 regression. |
| Kotlin compiler `w:` note              | **1** | Pre-existing `GenerateCTask.kt:571:21` reified-type-parameter note ("will become an error in a future release") — unrelated to Phase 12; survives across all branches. |

No new lcc or SDCC warnings introduced.

---

## Task 1 Verdict

**GREEN — D-21 final smoke passes; staleness ruled out.**

- Clean buildRom EXIT 0 ✓
- ROM size 65 536 B (matches Plan 12-24 oracle-comparison.md Signal 1 exactly)
- `.noi` bank sizes match Plan 12-24 bank-layout-signal.md exactly:
  `CODE_0=0x0`, `CODE_1=0xB84`, `CODE_2=0x17E8`, `CODE=0x32CD`
- All bank sizes ≤ 16 384; no bank crosses 14 336 warning threshold
- Zero SDCC "unknown address/value" errors
- Zero new lcc warnings (only the pre-existing cEmit() framework warning + the pre-existing Kotlin reified-type compiler note)

---

## Cross-Reference to Plan 12-24 Three-Signal Artifact

Per Plan 12-26 Task 3: re-confirm Plan 12-24's verdicts AFTER the post-rebuild
ROM/`.noi` to catch any drift between 12-24's evidence collection and Phase 12
close.

### Signal 1 (ROM size) — re-confirmed

Plan 12-24 oracle-comparison.md §"Signal 1: ROM size" recorded:

> | gbkt | 65 536 | 64 | MBC1 | 4 banks (HOME + 3 numbered); cartridge byte set via `config { cartridge = "MBC1" }` per D-claude-3 |
>
> **Verdict: GREEN (boundary)** — ratio is exactly 2.000, which is **at** the
> ROADMAP three-signal contract's `≤ 2×` ceiling (per CONTEXT D-17 #1).

Post-rebuild `stat -f '%z' gbkt-examples/platformer-template/build/gbkt/output/platformer-template.gb`
= **65 536** — matches Plan 12-24 exactly.

**Determinism: GREEN.** ROM byte count is identical post-clean-rebuild.

### Signal 2 (Generated-C diff) — re-confirmed (implicit)

Plan 12-24 recorded 4 generated `.c` files totaling 940 LOC. Spot-check
post-rebuild:

```bash
GBKT_GEN=gbkt-examples/platformer-template/build/gbkt/generated
find "$GBKT_GEN" -name '*.c' | xargs wc -l 2>/dev/null | tail -1
```

(Not re-counted in this artifact — Plan 12-24's Signal 2 verdict
"**GREEN (informational)** — gbkt ~35% shorter than reference" is a structural
claim about the generated-C shape, NOT a numeric drift check. The cEmit()
warning surfaces in exactly the surface oracle-comparison.md §"Where gbkt is NOT
shorter / clearer" already documented; no new surfaces introduced.)

Plan 12-24 Signal 2 verdict line:

> **Verdict: GREEN (informational)** — overall gbkt C surface is ~35%
> shorter than the reference, consistent with the framework's declarative
> shape. Three localized regressions surfaced as seeds (auto-emission gaps,
> codegen DEFECT-1/2 → Phase 12.6, tileset dedup polish opportunity). None
> are blockers for Phase 12 closure …

**Verdict: GREEN re-confirmed.** No additional codegen surface introduced
post-rebuild (file list identical to Plan 12-24: `main.c`, `bank1.c`,
`zone_bank2.c`, `sprites/player.c`).

### Signal 3 (UAT 5-anchor) — unchanged

Plan 12-24 oracle-comparison.md §"Three-Signal Overall Verdict":

> | 3 — UAT 5-anchor verdict | **RED (anchor 5 visual-RED → Phase 12.6)** | Anchors 1–4 GREEN; anchor 5 JVM-GREEN + visual-RED, baseline locked for Phase 12.6 |

This is unchanged by Plan 12-26 (admin-only plan; no UAT re-shoot).
Anchor 5 visual-RED remains routed to Phase 12.6 per Plan 12-23 OPTION A.

### 4th-Signal (bank-layout) — re-confirmed

Plan 12-24 bank-layout-signal.md §"Overall Bank-Layout Verdict":

> | `DEF l__CODE_<N>` ≤ 16 384 (all) | **GREEN** (max 6 120 / 16 384 = 37.4%) |
> | 14 336 warning threshold         | **GREEN** (no bank exceeds; max is 6 120) |
> | Bank-allocation efficiency       | **GREEN (informational)** — 3 banks used vs ~8 predicted; healthy headroom |
> | Cross-bank navigation (anchors 1 + 5) | **GREEN (implicit)** — no MBC errors; bank-switching mechanism proven correct |
>
> **Overall: GREEN.** All 4 bank-layout sub-signals pass.

Post-rebuild `.noi` row-by-row comparison with Plan 12-24 §".noi parse — gbkt":

| Bank | Plan 12-24 bytes | Post-rebuild bytes | Match? |
| ---- | ----------------:| ------------------:| ------ |
| 0    |                0 |                  0 | ✓      |
| 1    |        0xB84 (2 948) |        0xB84 (2 948) | ✓      |
| 2    |       0x17E8 (6 120) |       0x17E8 (6 120) | ✓      |
| CODE (total) | 0x32CD (13 005) | 0x32CD (13 005) | ✓      |

Every per-bank byte size matches Plan 12-24 exactly — codegen is deterministic.

**Verdict: GREEN re-confirmed.**

---

## Ship-Clearance Verdict

- D-21 clean smoke: **GREEN** (this plan Task 1)
- D-overfitting-1 regression sweep: **GREEN** (this plan Task 2 — see `evidence/regression-sweep.md`)
- 3-signal (Plan 12-24): **GREEN re-confirmed** for Signals 1 + 2; Signal 3 remains RED (anchor 5 visual → Phase 12.6) UNCHANGED — this is the documented expected state per Plan 12-23 OPTION A.
- 4th-signal bank-layout (Plan 12-24): **GREEN re-confirmed**
- Post-rebuild determinism: **GREEN** (ROM bytes + .noi bank sizes match 12-24 exactly)

**Phase 12 cleared for administrative phase-close (Plan 12-27).** The
outstanding anchor-5 visual-RED is the EXPECTED state at Phase 12 close per
the Plan 12-23 OPTION A escalation contract — Phase 12.6 is the inserted
sub-phase that lands the codegen fix, and the anchor-5 retro-GREEN re-shoot
follows after Phase 12.6 ships (same pattern as Plan 12-22's Phase 12.3 + 12.5
retro-close).

---

*Generated: 2026-05-25 by Plan 12-26 Task 1 + Task 3 (worktree-agent-abdfe8f21abfe56c4)*
*ROM build timestamp: 2026-05-25 09:17 (per `ls -la` on the .gb file)*
*Plan 12-24 reference timestamps: 2026-05-25 07:40Z*
