# Photon 2 for Minecraft 1.20.1 Forge

This repository contains the Forge 1.20.1 port of Photon 2, based on upstream Photon 2.2.9. The port targets Java 17 and builds against Forge 47.4.28. Its mod metadata supports Forge 47.4.10 and later 47.x releases.

## Build

Use JDK 17 and run:

```powershell
.\gradlew.bat clean build
```

The distributable is `build/libs/photon-forge-1.20.1-2.2.9-forge.1.20.1-all.jar`. It embeds the matching LDLib2 2.2.39.a and KilaGraph 20.1.0.15 Forge dependencies. Install that `-all.jar` in the instance `mods` folder; do not install the slim JAR alongside it.

The project also expects LDLib 1.0.52.a in the instance. It is a separate required mod and is listed in `META-INF/mods.toml`.

## Port notes

The source adapts Photon 2.2.9's Minecraft APIs, Forge networking and shader integration, resource serialization, and editor UI to the Forge 1.20.1 / LDLib2 2.2.39.a APIs. The upstream interactive UI scenarios are excluded from the production source set because they target newer LDLib2 APIs and are developer-only tools.

## License

Photon is licensed under CC BY-NC-SA 4.0. See [LICENSE](LICENSE).
