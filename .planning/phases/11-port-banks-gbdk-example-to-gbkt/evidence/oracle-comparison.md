# Phase 11 — 4th-Signal Artifact: Bank-Layout Threshold

Source: gbkt-examples/banks/build/gbkt/output/banks.noi

Per CONTEXT D-15: each DEF l__CODE_<N> byte size MUST be <= 16384 (hard ROM-bank capacity).
Per CONTEXT D-04 corollary: no per-bank parity comparison with reference (FFD nondeterminism).

| Bank | Code section size (bytes) | Hex | % of 16384 |
|------|---------------------------|-----|-----------|
| 0    |        0                  | 0x0000 |   0.0%    |
| 1    |       51                  | 0x0033 |   0.3%    |
| 2    |        1                  | 0x0001 |   0.0%    |

## Verdict

**PASS** — all 3 bank(s) within 16384-byte capacity.
