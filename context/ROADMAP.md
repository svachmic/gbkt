# gbkt Road to Release

Outstanding work for gbkt v1.0 release.

---

## Pre-Release Tasks

### Developer Experience
- [ ] Docker Compose for development
- [ ] CLI bundle templates (minimal, platformer, rpg, puzzle)

### Sample Games Extraction
Move to separate `gbkt-examples` repository:
- [ ] Create repository
- [ ] Move `LabyrinthOfTheDragon/` and `LabyrinthOfTheDragon-port/`
- [ ] Update main README with link
- [ ] Remove from settings.gradle.kts

### Distribution
- [ ] Publish Docker image to GitHub Container Registry
- [ ] Generate SBOM in releases

### Publishing Setup
- [ ] Create OSSRH account at https://s01.oss.sonatype.org/
- [ ] Generate GPG key pair for artifact signing
- [ ] Add repository secrets:
  - `OSSRH_USERNAME` / `OSSRH_PASSWORD`
  - `SIGNING_KEY` / `SIGNING_PASSWORD`
  - `GRADLE_PUBLISH_KEY` / `GRADLE_PUBLISH_SECRET`
- [ ] First release to Maven Central (manual staging approval)
- [ ] First submission to Gradle Plugin Portal (manual approval)

### Testing (Optional for Alpha)
- [ ] GBDK compilation verification (requires GBDK in CI)
- [ ] ROM verification in emulator (headless mGBA/BGB)
- [ ] CI benchmark tracking
- [ ] Memory usage profiling

---

## Deferred (Post-Release)

### Windows Support
- Add `mingwX64` target to CLI (no Windows machine for testing)

### Documentation Site
- Docusaurus/MkDocs setup
- Tutorials, API reference from KDoc
- Hosted example games

### IDE Enhancements
- Context-aware code completion
- Go to Definition for DSL constructs
- Quick fixes, refactoring support
- Live C code preview

---

## Vision: Phases 7-9

Long-term goal: **Spring Initializr-like experience** for GBC game development.

### Phase 7: IntelliJ Project Generator
- New Project wizard with template selection
- Generated project structure with Gradle wrapper
- Template library: Minimal, RPG, Platformer, Puzzle

### Phase 8: Asset Pipeline
- PNG → GBDK C arrays (auto-slice, palette validation)
- Tiled (.tmx) → tilemap + collision data
- GIMP/Aseprite palette import
- MOD/XM → GBT Player, WAV → SFX

### Phase 9: IDE Integration
- Sprite/tilemap/palette preview in editor
- One-click emulator launch with breakpoint sync
- Built-in asset editors

### Success Criteria
1. Install IntelliJ + gbkt plugin
2. Create project via wizard
3. Drop PNGs into `res/sprites/`
4. Write Kotlin DSL
5. Click "Build ROM" → playable .gbc
6. Click "Run" → opens in emulator

**No manual GBDK setup. No command-line builds. No asset conversion scripts.**
