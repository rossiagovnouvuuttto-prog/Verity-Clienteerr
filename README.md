# Rockstar Client 1.21.11 Fabric port

GitHub Actions workspace for building the user's Rockstar Client source port for Minecraft 1.21.11 Fabric.

Expected source archive at repository root: `ROCKSTAR-1.21.11-source.zip`.

The workflow extracts the archive, builds it with Java 21 using the included Gradle wrapper, and uploads the non-sources JAR from `build/libs/` as a GitHub Actions artifact.
