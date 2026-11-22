# Photon 2 for Minecraft 1.20.1 Forge

This repository contains a Forge 1.20.1 port of Photon from the upstream `v26.2.2.9` release. The port targets Java 17 and builds against Forge 47.4.28. Its mod metadata supports Forge 47.4.10 and later 47.x releases.

## Build

Use JDK 17 and run:

```powershell
.\gradlew.bat clean build
```

The distributable is `build/libs/photon-forge-1.20.1-26.2.2.9-forge.1.20.1-all.jar`. It embeds the matching LDLib2 2.2.39.a and KilaGraph 20.1.0.15 Forge dependencies. Install that `-all.jar` in the instance `mods` folder; do not install the slim JAR alongside it.

The project also expects LDLib 1.0.52.a in the instance. It is a separate required mod and is listed in `META-INF/mods.toml`.

Photon does not need Kotlin for Forge and does not conflict with it. The embedded LDLib2 is `libs/ldlib2-2.2.39.a.jar`, the upstream build with its Kotlin runtime relocated under `com.lowdragmc.photon.internal.embedded` (see `libs/upstream/README.md`): shipping the upstream jar with its loose `kotlin-stdlib` created a JPMS module `kotlin.stdlib` that split the `kotlin.*` packages with Kotlin for Forge and stopped the game from starting for every Kotlin mod on the instance. CI fails the build if a loose Kotlin runtime ever comes back into the distributable. The same rule covers the audio codecs (jLayer, JAAD, jVorbisEnc): they used to travel as Jar-in-Jar modules exporting `javazoom.*`, `net.sourceforge.jaad.*` and `biniu.*`, which split packages with any mod carrying its own copy (Citadel ships JAAD: `Modules citadel and jaad.MILESTONE export package net.sourceforge.jaad.spi.javasound`); since round 19 `shadowJar` relocates them into `com.lowdragmc.photon.internal.embedded` inside the mod jar itself, and CI both audits the packaged jar for original package roots and runs the relocated codecs.

## Port notes

The source adapts the Minecraft APIs, Forge networking and shader integration, resource serialization, and editor UI to the Forge 1.20.1 / LDLib2 2.2.39.a APIs. The upstream interactive UI scenarios are excluded from the production source set because they target newer LDLib2 APIs and are developer-only tools.

## Kila material presets

The editor includes a growing catalogue of ready-to-edit Kila materials: impact flash, wind slash, ash cloud, blue flame, spore patch, glacier crack, celestial gate, gravity well, circuit armor, molten armor, frost nova, thunderclap and lunar eclipse, alongside the existing water, dust, elemental, trail, projected-ground and mesh effects. The built-in gallery lays larger categories out in a compact grid; match each preset to its renderer (trails, mesh or projected ground effects have dedicated targets). See the [Kila material preset guide](docs/Photon2-Kila-Presets.md) for descriptions and usage notes.

## FX packs, models, shaders and bloom

See [Photon 2 FX assets and bloom guide](docs/Photon2-FX-Assets-and-Bloom.md) for the folder layout, custom resource rules, and where to turn on Bloom. For the very short, plain-language setup for server owners and players, see [Photon server in five minutes](docs/Photon2-Servidor-en-5-minutos.md); for details on `.fxpack` files and sync limits, see the [full server distribution guide](docs/Photon2-FXPack-Guia-Servidor.md). For the port's technical and testing history, see [Photon 2 Forge port context](docs/Photon2-Port-Context.md). For a single-document handover covering the whole project (architecture, fixes, CI, releases and pending work), see the [full project context](docs/Photon2-Contexto-Completo.md).

### Share LDLib2 resources

- As an operator, run `/photon fx send <player>` to request a private transfer of your local `<gameDir>/ldlib2/assets` to one online player. Your client asks you to confirm before reading or uploading any files; the server retains the recipient-specific pack for future logins.
- To create a standalone `.fxpack` for Discord or another file host, run `/photon_client export_ldlib2_assets` and confirm when prompted. The file is written to `<gameDir>/photon/fxpacks/`.
- Current cap: 512 MiB compressed and 2 GiB expanded per pack. If the file is too large for a chat attachment, use file hosting.

## License

Photon is licensed under CC BY-NC-SA 4.0. See [LICENSE](LICENSE).

