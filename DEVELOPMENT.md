# Smart Recipe Book - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Installation

Drop the jar in your client's `mods` folder alongside its declared dependencies (see `fabric.mod.json`). On a server, drop it in the server's `mods` folder as well: that is what gives clients the full recipe list and the brewing recipes. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## Building

```bash
./gradlew build
```

Output JAR is in `build/libs/`.

To run a development client:

```bash
./gradlew runClient
```
