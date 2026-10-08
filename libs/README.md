# Runtime library inputs

Photon's profile-facing legacy dependency is LDLib `1.0.52.a`, from the local `ghouls` profile. It is declared in `META-INF/mods.toml` and included in the development classpath.

Photon still uses newer LDLib2 and KilaGraph APIs for its editor, graph resources, and renderer. Their Forge 1.20.1 runtime jars are nested in the `-all.jar` artifact so a profile does not need separate copies. The KotlinForForge 4.11.0 runtime jar already present in `test-1` is nested as a library; it has no `mods.toml`, so Photon does not declare it as a Forge mod dependency.

The artifact carries the licenses for the bundled components under `META-INF/licenses/`: LDLib2 (LGPL-3.0), KotlinForForge (LGPL-2.1), Kotlin runtime libraries (Apache-2.0), and KilaGraph (MIT). Their jars remain under `META-INF/jarjar/`.

## KilaGraph for Forge 47.4.10

`kilagraph-forge-1.20.1-forge4710-20.1.0.15.jar` is rebuilt from [supermerlin204/kilagraph-1.20.1-forge](https://github.com/supermerlin204/kilagraph-1.20.1-forge), commit `ad949506394ac73f18c3599b6ee81334b671ba17`. The patch in `patches/KilaGraph-forge-47.4.10.patch` changes its Forge target to 47.4.10 and assigns local version `20.1.0.15` to satisfy Photon. Before installing the all-in-one jar, the standalone 20.1.0.14 copy in `test-1` must be moved out of its `mods/` directory to avoid loading two KilaGraph mods. The source compiled and was reobfuscated against Forge 47.4.10; no Minecraft instance was launched.

The upstream MIT license is included at `licenses/KilaGraph-MIT.txt` and in the Photon artifact.
