# Lizz b12.8 — Changelog (Size Optimization Release)

## Size optimization (the whole point of this release)

Dramatically reduced the download size of both release artifacts with **zero functional changes**:

| Artifact | Before (b12.7) | After (b12.8) | Saved |
|---|---|---|---|
| Mod JAR | 43.77 MB | **25.12 MB** | −43% |
| Source zip | 58.67 MB | **28.43 MB** | −52% |

### What was removed
- **Apache PDFBox + FontBox (~3.2 MB)** — pulled transitively by xchart for PDF/SVG chart export; only PNG bitmap export (`BitmapEncoder`) is ever used. Excluded via dependency excludes + shadowJar excludes.
- **Lombok (~2 MB raw)** — compile-time-only library that was being shaded into the runtime JAR. Now `compileOnly` + `annotationProcessor`.
- **`src/main.rar` (18.5 MB)** — an unrelated RAR archive committed inside `src/`, referenced by nothing. Removed from the repository.
- **Dead ClickGUI assets (5.4 MB)** — `assets/minecraft/fdpclient/nextgen-clickgui/` was only referenced by the not-yet-compiled FDP "nextgen web-clickgui" classes (excluded from compilation in `build.gradle`). Recoverable from git history when the port happens.
- **Unused UI sounds (0.75 MB)** — `fdpclient/sound/{disable,enable,pop,swing}.wav` were not referenced anywhere in code (no `sounds.json` either).

### What was optimized
- **All PNG assets quantized to 256 colors** (FASTOCTREE + dithering): 10.9 MB → 2.3 MB (−79%). No visible quality loss on backgrounds/halos/target images.
- **All JPEG assets re-encoded at quality 70**: 3.1 MB → 2.1 MB (−32%) — jumpcircle animation frames and halo images.
- **New `optimizeJar` Gradle task** (runs automatically after `reobfShadowJar`):
  - strips `LocalVariableTable` / `LocalVariableTypeTable` debug attributes from every `.class` (LineNumberTable is **kept**, so stack traces stay readable),
  - repacks the whole JAR with deflate level 9 (best compression),
  - drops stale signature files if any.
- **`source.zip` is now compressed at level 9** in the release workflow instead of `git archive`'s default.

### Technical
- No code behavior changed; all modules, mixins and reobfuscation flow untouched.
- `optimizeJar` failure for any single class falls back to keeping that class as-is (safe by design).
- Post-release verification: zip integrity OK, zero dead-weight entries (pdfbox/fontbox/lombok), manifest + mcmod.info correct (b12.8), SRG reobf confirmed, LocalVariableTable stripped while LineNumberTable/StackMapTable fully preserved.
