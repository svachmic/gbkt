---
phase: 11-port-banks-gbdk-example-to-gbkt
plan: 13
type: execute
wave: 5
depends_on: ["11-10"]
files_modified:
  - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/anchor3-cartridge-byte.txt
  - .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/oracle-comparison.md
autonomous: true
requirements:
  - BANK-03         # UAT anchor 3 (MBC5 cartridge byte 0x0147)
  - BANK-4TH-SIGNAL # 4th-signal artifact: .noi parse — all DEF l__CODE_<N> ≤ 16384 (CONTEXT D-15, ROADMAP success criterion)
user_setup: []
must_haves:
  truths:
    - "ROM file byte at offset 0x0147 is `0x1b` (MBC5+RAM+BATT — matches reference)"
    - "All `DEF l__CODE_<N>` entries in `banks.noi` are ≤ 16384 (0x4000) — no bank overflow"
    - "Evidence artifacts capture both signals under `evidence/`"
  artifacts:
    - path: ".planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/anchor3-cartridge-byte.txt"
      provides: "Anchor 3 mechanism evidence: hex dump of ROM offset 0x0147"
      contains: "0x1b"
    - path: ".planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/oracle-comparison.md"
      provides: "4th-signal artifact: per-bank CODE section sizes from banks.noi"
      contains: "DEF l__CODE_"
  key_links:
    - from: "evidence/anchor3-cartridge-byte.txt"
      to: "build/gbkt/output/banks.gb byte 0x0147"
      via: "python3 file read"
      pattern: "0x14[7]"
    - from: "evidence/oracle-comparison.md"
      to: "build/gbkt/output/banks.noi DEF l__CODE_N entries"
      via: "regex parse"
      pattern: "DEF l__CODE_"
---

<objective>
Produce the two non-runtime artifacts: anchor 3 (MBC5 cartridge byte ROM-file read) and the 4th-signal bank-layout artifact (`.noi` parse asserting all `DEF l__CODE_<N>` ≤ 16384).

Purpose: Anchor 3 is mechanism-level (variable evidence sufficient per Visual Evidence Rule corollary). The 4th-signal artifact is the ROADMAP-required bank-layout check beyond the standard three-signal contract — per CONTEXT D-15 and the ROADMAP §"Success Criteria" line for Phase 11.

Output: 2 evidence files. No new JVM tests; this plan is shell/Python-only.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-CONTEXT.md
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-RESEARCH.md
@.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-PATTERNS.md
@gbkt-examples/banks/11-UAT.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: Capture anchor 3 cartridge byte from built ROM</name>
  <read_first>
    - 11-RESEARCH.md §"Cartridge-Byte Emission" (lines 343–375 — `cartridge = "MBC5_RAM_BATTERY"` → ROM[0x0147] = 0x1B)
    - 11-RESEARCH.md §"Common Pitfalls" Pitfall 5 (0x19 vs 0x1B confusion — must be 0x1B for this phase per Plan 11-05 D-07 decision)
    - 11-UAT.md anchor 3 (expected: `0x1b`)
  </read_first>
  <files>.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/anchor3-cartridge-byte.txt</files>
  <action>
    Ensure the ROM is built. If `build/gbkt/output/banks.gb` is missing, run:
    ```
    ./gradlew :gbkt-examples:banks:buildRom --quiet
    ```

    Capture cartridge byte:
    ```
    python3 -c "
    import sys
    p = 'gbkt-examples/banks/build/gbkt/output/banks.gb'
    with open(p, 'rb') as f:
        f.seek(0x147)
        b = f.read(1)[0]
    print(f'ROM file: {p}')
    print(f'Offset:   0x0147')
    print(f'Byte:     0x{b:02x}')
    print(f'Expected: 0x1b (MBC5+RAM+BATT — matches reference -Wl-yt0x1B)')
    print(f'Result:   {\"PASS\" if b == 0x1B else \"FAIL\"}')
    sys.exit(0 if b == 0x1B else 1)
    " | tee .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/anchor3-cartridge-byte.txt
    ```

    If python3 is unavailable, use `xxd` or `hexdump`:
    ```
    BYTE=$(xxd -s 0x147 -l 1 -p gbkt-examples/banks/build/gbkt/output/banks.gb)
    echo "ROM file: gbkt-examples/banks/build/gbkt/output/banks.gb"
    echo "Offset:   0x0147"
    echo "Byte:     0x${BYTE}"
    echo "Expected: 0x1b"
    echo "Result:   $([ "$BYTE" = "1b" ] && echo PASS || echo FAIL)"
    ```

    If the byte is NOT `0x1b`:
    - DO NOT manually edit the artifact to make it pass.
    - Mark the artifact `FAIL` and STOP. Either Banks.kt has the wrong `cartridge` string (Plan 11-05 regression — re-verify the literal `"MBC5_RAM_BATTERY"` per Plan 11-05 acceptance) OR a propagation regression elsewhere (CompileRomTask.readMbcType / GenerateCTask.writeBuildMetadata). Capture a seed: "anchor 3 cartridge byte regression — got 0x<N>, expected 0x1b — root cause needs Plan 11-09-style buildRom-log inspection."

    Note: Anchor 3's expected value is `0x1b` per RESEARCH §Cartridge-Byte Emission AND Plan 11-05's locked `cartridge = "MBC5_RAM_BATTERY"`. Do NOT loosen to `0x19` — that would imply Banks.kt regressed.
  </action>
  <verify>
    <automated>test -f .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/anchor3-cartridge-byte.txt && grep -q "Byte:     0x1b" .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/anchor3-cartridge-byte.txt && grep -q "Result:   PASS" .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/anchor3-cartridge-byte.txt</automated>
  </verify>
  <acceptance_criteria>
    - File `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/anchor3-cartridge-byte.txt` exists
    - File contains literal line `Byte:     0x1b`
    - File contains literal line `Result:   PASS`
    - File does NOT contain `Result:   FAIL` (failure means a regression — Plan 11-05 acceptance must be re-run)
  </acceptance_criteria>
  <done>Anchor 3 PASS; cartridge byte matches reference oracle.</done>
</task>

<task type="auto">
  <name>Task 2: Parse banks.noi and write 4th-signal artifact</name>
  <read_first>
    - 11-RESEARCH.md §"4th-Signal `.noi` Extraction" (lines 292–337 — file location, format, parse regex, assertion)
    - 11-CONTEXT.md D-15 (4th signal — each `DEF l__CODE_<N>` ≤ 16384)
    - 11-CONTEXT.md D-04 corollary (no per-bank parity comparison with reference — threshold check only)
    - ROADMAP §"Phase 11" Success Criteria line ("generated `.noi` file's `DEF l__CODE_<N>` sizes are within reasonable bounds")
  </read_first>
  <files>.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/oracle-comparison.md</files>
  <action>
    Parse `gbkt-examples/banks/build/gbkt/output/banks.noi` and emit `evidence/oracle-comparison.md` containing the bank-size table.

    Script:
    ```
    python3 -c "
    import re, sys
    noi = 'gbkt-examples/banks/build/gbkt/output/banks.noi'
    out = '.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/oracle-comparison.md'
    with open(noi) as f:
        content = f.read()
    entries = []
    for m in re.finditer(r'DEF l__CODE_(\d+) 0x([0-9a-fA-F]+)', content):
        bank = int(m.group(1))
        size = int(m.group(2), 16)
        pct = size / 16384 * 100
        entries.append((bank, size, pct))
    entries.sort()
    overflow = [(b, s) for b, s, _ in entries if s > 16384]
    with open(out, 'w') as f:
        f.write('# Phase 11 — 4th-Signal Artifact: Bank-Layout Threshold\n\n')
        f.write('Source: ' + noi + '\n\n')
        f.write('Per CONTEXT D-15: each DEF l__CODE_<N> byte size MUST be <= 16384 (hard ROM-bank capacity).\n')
        f.write('Per CONTEXT D-04 corollary: no per-bank parity comparison with reference (FFD nondeterminism).\n\n')
        f.write('| Bank | Code section size (bytes) | Hex | % of 16384 |\n')
        f.write('|------|---------------------------|-----|-----------|\n')
        for b, s, p in entries:
            f.write(f'| {b}    | {s:>8}                  | 0x{s:04x} | {p:5.1f}%    |\n')
        f.write('\n')
        f.write('## Verdict\n\n')
        if overflow:
            f.write(f'**FAIL** — {len(overflow)} bank(s) exceed 16384 bytes: {overflow}\n')
        else:
            f.write(f'**PASS** — all {len(entries)} bank(s) within 16384-byte capacity.\n')
    print('Wrote ' + out)
    if overflow:
        print('FAIL — bank overflow detected')
        sys.exit(1)
    else:
        print('PASS')
        sys.exit(0)
    "
    ```

    If `banks.noi` does not exist, run `./gradlew :gbkt-examples:banks:buildRom --quiet` first.

    Do NOT compare per-bank sizes against the reference ROM's `banks.noi` (CONTEXT D-04 corollary explicitly rejects per-bank ratio comparison — FFD nondeterminism). The artifact reports gbkt's sizes only, with the 16384 threshold.

    Optionally — if `evidence/reference/banks.noi.txt` exists from a prior reference-ROM build (Phase 11 may produce this via the Makefile recipe in `gbkt/banks/Makefile`), include a parallel table for the reference but DO NOT assert per-bank equivalence. Label it informational only.
  </action>
  <verify>
    <automated>test -f .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/oracle-comparison.md && grep -q "PASS" .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/oracle-comparison.md && ! grep -q "FAIL" .planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/oracle-comparison.md</automated>
  </verify>
  <acceptance_criteria>
    - File `.planning/phases/11-port-banks-gbdk-example-to-gbkt/evidence/oracle-comparison.md` exists
    - File contains literal `# Phase 11 — 4th-Signal Artifact: Bank-Layout Threshold`
    - File contains a markdown table with at least one row matching `| <N>    | <bytes>`
    - File contains literal `**PASS**` and does NOT contain `**FAIL**`
    - File contains literal `DEF l__CODE_` reference (proves it parsed the .noi)
    - File explicitly states "no per-bank parity comparison" (per D-04 corollary)
  </acceptance_criteria>
  <done>4th-signal artifact written; all banks within capacity; ROADMAP Phase 11 success criterion satisfied.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| ROM file read | Local build artifact; trusted source |
| .noi parse | Plain text; no untrusted input |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-11-28 | Tampering | Manual edit of evidence files | mitigate | Acceptance gates grep for PASS markers produced by the script; manual edits would need to forge the script output shape |
| T-11-29 | Repudiation | Per-bank parity comparison (forbidden) | mitigate | Artifact body explicitly cites D-04 corollary; no equivalence claim against reference |
| T-11-30 | Denial of service | Bank overflow (>16384) | mitigate | Script exits 1 on overflow; PASS marker absent |
| T-11-SC | Tampering | npm/pip/cargo installs | n/a | Python3 + standard library only |
</threat_model>

<verification>
  - Both artifacts exist with PASS markers.
  - No FAIL markers.
  - .noi parsed and bank sizes within threshold.
</verification>

<success_criteria>
  - Anchor 3 PASS.
  - 4th-signal artifact PASS.
  - Both evidence files committed.
</success_criteria>

<output>
Create `.planning/phases/11-port-banks-gbdk-example-to-gbkt/11-13-SUMMARY.md` with: cartridge byte (decimal + hex), bank table (count + per-bank sizes), 2 file paths.
</output>
