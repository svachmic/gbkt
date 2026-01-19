# Localization Guide

This guide covers how to localize gbkt games using the GNU gettext format (.po/.pot files).

## Overview

gbkt uses industry-standard GNU gettext files for localization:

- **`.pot` files** (Portable Object Template) - Source of truth, contains all string keys
- **`.po` files** (Portable Object) - Translations for a specific language (en.po, de.po, ja.po)

This format is supported by professional translation tools like POEdit, Crowdin, Lokalise, and Weblate.

## File Structure

```
res/strings/
├── messages.pot          # Template (source of truth)
├── en.po                 # English (base language)
├── de.po                 # German translation
└── ja.po                 # Japanese translation
```

## PO File Format

```po
# Header with metadata
msgid ""
msgstr ""
"Language: en\n"
"Content-Type: text/plain; charset=UTF-8\n"

#. Ability names
msgctxt "ability"
msgid "fireball"
msgstr "Fireball     "

#. Battle feedback
msgctxt "battle"
msgid "monster_attacks"
msgstr "%monster attacks!"
```

### Entry Components

| Component | Purpose |
|-----------|---------|
| `#.` | Extracted comment (preserved for translators) |
| `msgctxt` | Namespace/context (maps to ROM bank groups) |
| `msgid` | String key (used in code references) |
| `msgstr` | Translated value (empty in .pot templates) |

## Bank Allocation

By default, banks are **automatically allocated** using a bin-packing algorithm. However, you can specify explicit bank assignments using `@bank` comments:

```po
#. @bank 0
#. misc namespace (35 strings)

msgctxt "misc"
msgid "potion"
msgstr "Potion"
```

### How Bank Allocation Works

1. Namespaces with `@bank N` comments use the specified bank
2. Remaining namespaces are sorted by size (largest first)
3. Each is assigned to the first bank with space (banks 1-7 by default)

### Bank 0 (Home Bank)

Bank 0 is special - it's always present without bank switching. Use it for frequently accessed strings:

```po
#. @bank 0
#. ability namespace - used often in combat
msgctxt "ability"
msgid "fireball"
msgstr "Fireball     "
```

```kotlin
// In your build script (if using manually)
val bankAllocator = BankAllocator()
val stringTable = PoParser.parse(File("res/strings/en.po"))
val allocated = bankAllocator.allocateForStrings(stringTable)
```

## Game Boy Constraints

### Character Limits

- **18 characters per line** (dialog box width)
- **5 lines maximum** (dialog box height)
- **90 characters total** per dialog box

### Padding

Pad strings to fixed length for consistent display:

```po
# Ability names should be 13 chars
msgctxt "ability"
msgid "fireball"
msgstr "Fireball     "  # Padded with spaces

msgid "cure_wounds"
msgstr "Cure Wounds  "  # Padded with spaces
```

### Placeholders

Use `%name` style placeholders for dynamic content:

```po
msgctxt "battle"
msgid "player_damage"
msgstr "You deal %damage damage!"

msgid "monster_attacks"
msgstr "%monster %c attacks!"
```

Common placeholders:
- `%damage` - Damage amount
- `%monster` - Monster name
- `%c` - Monster index (A, B, C)
- `%level` - Player level
- `%exp` - Experience points

## Using Strings in Code

### C Code Reference

The generated C code creates string constants:

```c
// Generated from PO file
const char str_ability_fireball[] = "Fireball     ";
const char str_battle_monster_attacks[] = "%monster attacks!";

// In battle.c
show_message(str_battle_monster_attacks);
```

### String Lookup

For dynamic string selection:

```c
// Get string by ID
const char* msg = get_string(STR_BATTLE_VICTORY);

// With bank switching
SWITCH_ROM_BANK(_string_bank[STR_ABILITY_FIREBALL]);
show_message(str_ability_fireball);
```

## Validation

### POEdit Workflow

1. Open `messages.pot` as source
2. Create new translation → Select language
3. Translate strings
4. Save as `{lang}.po`

### IntelliJ Plugin

The gbkt IntelliJ plugin provides:

- PO file syntax highlighting
- GB font preview panel
- Character count validation
- Namespace grouping view

### Programmatic Validation

```kotlin
val (table, warnings) = PoParser.parseWithValidation(File("en.po"))
for (warning in warnings) {
    println("Warning: $warning")
}
```

## Best Practices

### Namespace Organization

Group related strings by context:

| Namespace | Content |
|-----------|---------|
| `ability` | Ability/skill names |
| `battle` | Combat messages |
| `player` | Player action feedback |
| `monster` | Monster action messages |
| `item` | Item names and descriptions |
| `floor1-8` | Floor-specific dialog |
| `credits` | Credits text |

### Consistent Padding

Keep related strings the same length for menu alignment:

```po
# Items should be 10 chars
msgctxt "item"
msgid "potion"
msgstr "Potion    "
msgid "ether"
msgstr "Ether     "
msgid "elixir"
msgstr "Elixir    "
```

### Translator Comments

Add context for translators:

```po
#. This appears after defeating a boss, keep it dramatic
msgctxt "battle"
msgid "boss_defeated"
msgstr "The beast falls!"
```

## Table Schema Validation

For balance data tables (.csv), use JSON schema files:

```
res/data/
├── tables.csv
└── tables.schema.json
```

Schema example:

```json
{
  "name": "game_balance",
  "columns": {
    "exp_by_level": {
      "type": "uint16_t",
      "min": 0,
      "max": 65535
    },
    "monster_hp_c": {
      "type": "uint8_t",
      "min": 1,
      "max": 255
    }
  }
}
```

Validation in code:

```kotlin
val (table, errors, warnings) = TablesParser.parseWithSchema(File("tables.csv"))
if (errors.isNotEmpty()) {
    errors.forEach { println("ERROR: $it") }
}
```

## See Also

- [DSL_REFERENCE.md](DSL_REFERENCE.md) - DSL syntax reference
- [ARCHITECTURE.md](ARCHITECTURE.md) - IR and codegen structure
- [DEVELOPER_EXPERIENCE.md](DEVELOPER_EXPERIENCE.md) - Extending the framework
