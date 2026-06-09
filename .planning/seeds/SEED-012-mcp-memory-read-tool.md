---
id: SEED-012
status: dormant
planted: 2026-05-19
planted_during: v1.0 / Phase 10.1-19 (D-V3 GBC palette diagnostic)
trigger_when: next time a UAT diagnostic needs runtime hardware-register state (palette RAM, OAM raw bytes, LCDC bits, etc.) that cannot be exposed as a named sym-file variable
scope: small
---

# SEED-012: MCP `emulator_read_memory` Tool

## Why This Matters

During Plan 10.1-19 (DEF-13-C / D-V3 GBC palette diagnostic), the user
requested an orchestrator-driven runtime confirmation that the OCPD palette
RAM (`0xFF6B`) was indeed all-zeros before approving Plan 10.1-20's
bootstrap-order fix. The existing `mcp__gbkt-emulator__emulator_read_variable`
tool only resolves named symbols from the `.noi` / `.sym` file — hardware
registers like `OCPD_REG` / `BCPD_REG` / `LCDC` / `OAM` are defined as
`#define`'d memory accessors in GBDK headers, NOT as named variables in
the sym file, so they cannot be read via the existing tool.

The static deduction was sufficient to name the cause in 10.1-19 (all four
hypotheses traced end-to-end via emission grep + reference cross-reference),
but the gap will surface again the next time a visual-evidence-rule diagnostic
needs to peek at raw hardware state for confirmation.

## When to Surface

**Trigger:** the next time a `/gsd:plan-phase` produces a diagnostic plan
whose verification step requires reading raw memory at a specific address.

Likely candidates:
- Future audio diagnostics (need raw NR5x register values)
- Future input/joypad diagnostics (need raw `P1` / `JOYP` register)
- Future banking diagnostics (need raw `MBC5` `ROMB0`/`ROMB1`/`RAMB` bytes)
- Future tilemap / OAM diagnostics (need raw VRAM / OAM contents)

## Proposed Tool Signature

```kotlin
// In gbkt-mcp-server/src/main/.../McpToolRegistry.kt
ToolDef(
    name = "emulator_read_memory",
    description = "Read N bytes from a raw memory address. " +
        "Useful for inspecting hardware registers (OCPD 0xFF6B, BCPD 0xFF69, etc.) " +
        "or memory regions not exposed as named variables.",
    parameters = jsonSchema {
        property("address", "integer", "Raw memory address (0x0000..0xFFFF)")
        property("count", "integer", "Number of bytes to read (default 1, max 256)")
    },
    handler = { params ->
        val addr = params.getInt("address")
        val count = params.optInt("count", 1).coerceAtMost(256)
        val bytes = (0 until count).map { i ->
            agent.readMemory(addr + i)
        }
        json {
            put("address", "0x%04X".format(addr))
            put("count", count)
            put("bytes", JsonArray(bytes.map { JsonPrimitive("0x%02X".format(it.toInt() and 0xFF)) }))
        }
    },
)
```

## Scope Estimate

**Small** — 1 file change in `gbkt-mcp-server/`, 1 method addition to
`StepAgent.readMemory(address: Int): Byte` (probably already exists internally
for sym-file lookup — just exposed at the MCP boundary).

Estimated effort: 30-60 minutes including a sanity test that reads
`LCDC_REG` (0xFF40) at boot vs post-DISPLAY_ON and asserts bit 7 transitions.

## Write companion (added 2026-05-19, Phase 10.2 revision)

The original SEED-012 spec was read-only. During Phase 10.2 planning (revision 1, 2026-05-19),
the user authorized extending this seed with a **paired `emulator_write_memory(address, byte)` MCP tool**.

Rationale: Phase 10.2's D-07 evidence shape requires a full per-slot BCPD/OCPD palette RAM dump
(8 palettes × 4 colors × 2 LE bytes = 64 bytes per port). The GBC hardware uses indirect addressing
via the BCPS/OCPS index port pair — writing the index to 0xFF68 (BCPS) / 0xFF6A (OCPS), then reading
from 0xFF69 (BCPD) / 0xFF6B (OCPD). Without a write-memory MCP tool, the orchestrator cannot drive
the index register, so the dump degrades to a 1-byte sentinel — which the user explicitly rejected
as scope reduction.

Write tool signature (mirrors the read tool):

```kotlin
ToolDef(
    name = "emulator_write_memory",
    description = "Write a single byte to a raw Game Boy memory address. " +
        "Required for driving the BCPS/OCPS index registers (0xFF68/0xFF6A) before " +
        "reading BCPD/OCPD palette RAM via emulator_read_memory. " +
        "Value is masked to a byte (0..255).",
    parameters = jsonSchema {
        property("address", "integer", "Raw memory address (0x0000..0xFFFF)")
        property("value", "integer", "Byte value to write (0..255); higher bits masked off")
    },
    handler = { params ->
        val addr = params.getInt("address")
        val value = params.getInt("value") and 0xFF
        agent.writeMemory(addr, value)
        json {
            put("success", true)
            put("address", "0x%04X".format(addr))
            put("value", "0x%02X".format(value))
        }
    },
)
```

The write companion lands ALONGSIDE the read tool in the same Plan 10.2-01 deliverable.
`StepAgent.writeMemory(address: Int, value: Int): Unit` is added as a sibling to the existing
`StepAgent.readMemory(Int): Int`; both delegate to the already-existing `MemoryAccess` interface
methods (`writeByte`/`readByte` at `GbEmulator.kt:101-110`).

Scope: still **small**. Extending from 1 file change to 2 (StepAgent + ToolHandlers + the test for
each). ~60-90 minutes total instead of 30-60.

## Related

- Plan 10.1-19 (D-V3 GBC palette diagnostic) — surfaced this gap
- `feedback_visual_evidence_for_visual_truths.md` — runtime confirmation of
  hypotheses is part of the "necessary AND sufficient" discipline; adding
  this tool strengthens future diagnostic plans
