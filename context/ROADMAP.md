# gbkt Road to Release

Outstanding work for gbkt v1.0 release.

---

## Phase 7: VSCode Extension Polish

Extension publishing preparation.

- [ ] 7.1 Create/update CHANGELOG.md
- [ ] 7.2 Add marketplace icon (128x128 PNG)
- [ ] 7.3 Cross-file symbol resolution
- [ ] 7.4 Full error diagnostics (undefined refs, type mismatches)
- [ ] 7.5 Replace regex-based symbol parsing with proper AST analysis
- [ ] 7.6 Auto-generate dsl-data.ts from DSL source code

---

## Phase 8: Developer Experience & Onboarding

Adoption barriers.

### Sample Projects
- [ ] 8.1 Add actual PNG assets to sample projects (minimal, dialog, save, adventure)
- [ ] 8.6 Add README to sample-game (currently undocumented)
- [ ] 8.7 Add README to sample-modular (currently undocumented)

### Documentation
- [ ] 8.4 Add Quick Start tutorial to main README (5-minute Hello World)
- [ ] 8.5 Add error troubleshooting guide to context/TOOLING.md

### Tooling
- [ ] 8.2 Docker Compose for development
- [ ] 8.3 CLI bundle templates (minimal, platformer, rpg, puzzle)

---

## Phase 9: Distribution

- [ ] 9.1 Publish Docker image to GitHub Container Registry
- [ ] 9.2 Generate SBOM in releases

---

## Phase 10: Publishing (Final Steps)

### Maven Central
- [ ] GPG key generation
- [ ] OSSRH (Sonatype) account
- [ ] Gradle publishing config
- [ ] POM metadata (license, developers, SCM)
- [ ] Automated release workflow

### VSCode Marketplace
- [ ] Register publisher account
- [ ] Publish extension

---

## Deferred (Post-Release)

### Windows Support
- Add `mingwX64` target to CLI
- Reason: No Windows machine available for testing

### Documentation Site
- Set up documentation framework (Docusaurus/MkDocs)
- Tutorials: Getting Started, First Game walkthrough, Asset pipeline
- Generate API reference from KDoc
- Host example games with source

### Future API Enhancements
- Pool typed state accessors (replace string indexing)
- Loop constructs in DSL (repeat, for)
- Ternary expression support
- Entity/Pool unified abstraction
- Array assignment support
