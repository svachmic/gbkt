# gbkt VS Code Extension

Lightweight syntax support for gbkt (Game Boy Kotlin) DSL development.

## Features

- **Syntax Highlighting** - gbkt DSL keywords injected into Kotlin files
- **Code Snippets** - Quick scaffolding for `gbGame`, `scene`, `entity`, `dialog`, and more
- **Build Command** - Build ROM with `Cmd+Shift+B` (macOS) or `Ctrl+Shift+B` (Windows/Linux)
- **Status Bar** - One-click ROM build button

## Full IDE Experience

For advanced features like:
- Code completion with documentation
- Go to definition
- Symbol rename
- Real-time validation (sprite/palette limits)
- Error diagnostics

**Use IntelliJ IDEA** with the gbkt plugin. Kotlin is a JetBrains language and IntelliJ provides the best development experience.

## Installation

### From Source

```bash
cd vscode-extension
npm install
npm run compile
```

### Run in Development Mode

1. Open this folder in VS Code
2. Press `F5` to launch Extension Development Host
3. Open a gbkt project in the new window

### Package for Distribution

```bash
npm run package
```

Creates `gbkt-x.x.x.vsix` for installation.

## Snippets

Type these prefixes in Kotlin files to get quick scaffolding:

| Prefix | Description |
|--------|-------------|
| `gbgame` | Full game entry point |
| `scene` | Scene with lifecycle hooks |
| `entity` | Entity with position |
| `entitysprite` | Entity with sprite and hitbox |
| `sprite` | Sprite with size and position |
| `dialog` | Dialog with text box |
| `menu` | Menu system |
| `pool` | Entity pool |
| `camera` | Camera with smoothing |
| `savedata` | Save data structure |
| `whenever` | Condition handler |
| `branch` | Multi-branch conditions |
| `every` | Frame handler |
| `animations` | Sprite animations |
| `u8var` / `u16var` | Variable declarations |

## Commands

| Command | Keybinding | Description |
|---------|------------|-------------|
| `gbkt.buildRom` | `Cmd/Ctrl+Shift+B` | Build ROM via Gradle |

## Configuration

| Setting | Default | Description |
|---------|---------|-------------|
| `gbkt.gradleWrapper` | `./gradlew` | Path to Gradle wrapper |

## Project Structure

```
vscode-extension/
├── src/
│   └── extension.ts        # Extension entry point
├── syntaxes/
│   └── gbkt.tmLanguage.json # TextMate grammar
├── snippets/
│   └── gbkt.code-snippets  # DSL snippets
├── language-configuration.json
└── package.json
```

## License

MIT
