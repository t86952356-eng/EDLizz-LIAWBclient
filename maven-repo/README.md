# Vendored Gradle plugins (maven-repo)

This directory is a **local Maven repository** that makes the build independent of
jitpack.io. It exists because in November 2026 jitpack evicted the cached artifacts of
`com.github.ccbluex:ForgeGradle:a3d86a59c0` (and its dependency
`com.github.abrarsyed.jastyle:jAstyle:1.3`) — the build status stayed "ok" but the files
returned HTTP 404, so every fresh CI build failed with
`Plugin Repositories (could not resolve plugin artifact ...)`.

`settings.gradle` resolves plugins from this directory FIRST, before jitpack.

## Contents

| Coordinates | What it is | How it was produced |
|---|---|---|
| `com.github.ccbluex:ForgeGradle:a3d86a59c0` | The ForgeGradle 2.x Gradle plugin used by LiquidBounce Legacy (Minecraft 1.8.9) | **Rebuilt from source**: `git clone https://github.com/CCBlueX/ForgeGradle && git checkout a3d86a59c08c471c8a9b822642d0b7ce71ff4455`, then `JAVA_HOME=<jdk8> ./gradlew publish --no-daemon` (Gradle 7.0.2 wrapper, Temurin JDK 8). The generated POM (`net.minecraftforge.gradle:ForgeGradle:2.1`) had its groupId/version rewritten to the jitpack coordinates. The jar embeds the shade deps (SpecialSource, RetroGuard, MCInjector, Srg2Source) exactly like the jitpack build did. |
| `com.github.xcfrg:mixingradle:ae2a80e` | MixinGradle plugin (applies the `org.spongepowered.mixin` plugin) | Downloaded from jitpack while still available (December 2025 build). POM deps: guava 17.0, asm-debug-all 5.0.3 (both on Maven Central). |
| `com.github.abrarsyed.jastyle:jAstyle:1.3` | Java source formatter library (runtime dep of ForgeGradle) | **Rebuilt from source**: `git clone https://github.com/abrarsyed/jAstyle` (master), `javac` compile of `src/main/java` with JDK 8, `jar cf`. No dependencies. |

## Notes for future agents

- Remaining ForgeGradle POM dependencies resolve from Maven Central
  (`com.github.jponge:lzma-java:1.3`, `com.github.tony19:named-regexp:0.2.3` ARE on
  Central) and from `https://maven.minecraftforge.net/` (`net.minecraftforge:fernflower:2.0-SNAPSHOT`).
- If you ever need to rebuild ForgeGradle yourself: it needs JDK 8 + its Gradle 7.0.2
  wrapper, a local repo holding the pinned SpecialSource snapshot
  (`1.7.4-SpecialSource-1.6.1-gf5cd633-34`, still downloadable from jitpack) and jAstyle,
  plus two small patches to its `build.gradle` (add the local repo; pin the SpecialSource
  version — a SNAPSHOT string in the pom and the directory name must match exactly, or
  Gradle fails with "inconsistent module metadata").
- Source licenses: ForgeGradle is LGPL-2.1 (see https://github.com/CCBlueX/ForgeGradle),
  jAstyle is LGPL-3.0, MixinGradle is MIT.
