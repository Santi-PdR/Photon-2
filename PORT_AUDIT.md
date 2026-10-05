# Photon-2 Forge 1.20.1 Port Audit

Audit date: 2026-10-05

## Repository state

- Remote: `https://github.com/Santi-PdR/Photon-2`
- Branch: `main`, tracking `origin/main`
- Starting commit: `6956f47` (`Initial commit`)
- Starting tracked source tree: only `README.md` (`# Photon-2`)
- Port branch: none existed; work is currently on `main`
- Current tree now has the official Forge 1.20.1 MDK build foundation and a minimal `@Mod("photon")` entrypoint. No Photon runtime, editor, rendering, gameplay, or data systems have been ported.
- Current port status: **STUB BOOTSTRAP ONLY; NO PHOTON FUNCTIONALITY**

## Original project and supplied artifact

The upstream Low-Drag-MC Photon project describes Photon as an in-game VFX toolkit/editor. Its documented systems include a Unity-style emitter/particle system, trails and beams, editor and timeline, shader graphs and post-processing, mesh/model particles, FX Packs, commands, and a Java integration API. The upstream `1.21` branch targets Minecraft 1.21.1 with NeoForge 21.1+ and depends on LDLib2.

The supplied `/home/Santipdr/Descargas/photon-neoforge-26.2-26.2.2.3.jar` is a Photon 26.2.2.3 NeoForge build for Minecraft 26.2, not a Forge 1.20.1 reference build. Its metadata requires NeoForge `[26.2,)`, Minecraft `[26.2, 26.2]`, LDLib2 `[26.2.2.41.a,)`, and KilaGraph `[26.2.0.15,)`. It declares Java 21 mixin compatibility, embeds KilaGraph 26.2.0.15, and its class files have bytecode major version 69 (Java 25). Inventory: 882 files (740 Photon classes, 135 assets, 52 shader files, 6 OBJ files); no `data/` entries, `.fx` projects, or `.fxpack` examples. 589 of the Photon classes are under `client/`; render, post-FX, FX runtime, and editor GUI dominate the remaining client tree. It declares a required Mixin plugin, client mixins, and access transformers for Minecraft and OpenGL/Iris internals. The artifact is useful for mapping the newer architecture and assets, but its target platform, Java bytecode, and dependencies do not match the requested target.

The largest Photon class groups in the supplied artifact are `client/gameobject` (311 classes), `client/render` (92), `client/postfx` (77), `gui/editor` (75), and `client/fx` (71). Packaged resource groups are 92 shader files (shader sources plus pipeline JSON), 35 PNGs, 6 primitive OBJ models, and two language files. There are no packaged sound files or data resources. This gives a concrete port inventory: the substantial work is client runtime/rendering/editor/shader code rather than blocks, items, or entities.

An unofficial LDLib2 Forge 1.20.1 port exists publicly, which may provide a candidate UI/rendering dependency if its license and exact APIs are suitable. I built the pinned HEAD `a8b70b84a36cf9162a95aee7c043b95418315a80` with Java 17: its build passed 237 unit tests (20 suites, no failures), and it produced a 5,046,634-byte `-all.jar`. It declares Minecraft 1.20.1, Forge 47.x, Java 17, and a mandatory KotlinForForge 4+ runtime dependency. The project says to build from source; it does not provide a published 1.20.1 Maven coordinate, so clean CI distribution/integration needs a source pin or an explicitly managed local artifact. This build verifies the candidate library's own code/tests, not compatibility with Photon 2. It must not be confused with the separate Photon 1.x Forge 1.20.1 releases.

## Port coverage matrix

| System | Original reference | Current repo | State | Work needed | Verification |
|---|---|---|---|---|---|
| Build/metadata/Forge entrypoint | Upstream project documents NeoForge; supplied artifact metadata targets NeoForge 26.2 | ForgeGradle 6 / Gradle 8.8 wrapper / Java 17 / Forge 47.4.26; minimal `@Mod` entrypoint | STUB | Replace shell with actual common/client initialization as systems are ported | `./gradlew build` passed; bootstrap JAR inspected |
| Registries, lifecycle, config, commands | Upstream source/JAR references | None | NO IMPLEMENTADO | Port compatible core and registrations | None |
| FX runtime, particles, emitters, modules | Upstream project and JAR | None | NO IMPLEMENTADO | Port runtime semantics and dependencies | None |
| Trails, beams, GPU/render pipeline | Upstream project and JAR | None | NO IMPLEMENTADO | Port renderers and shaders to Forge 1.20.1 | None |
| Timeline, curves, graphs, post-processing | Upstream project and JAR | None | NO IMPLEMENTADO | Port editor/data models and visual pipeline | None |
| Editor GUI and resource browser | Upstream project and JAR | None | NO IMPLEMENTADO | Port GUI and required widget/library capabilities | None |
| Model/mesh sources and FX Packs | Upstream project and JAR | None | NO IMPLEMENTADO | Port loaders, formats, pack/resource handling | None |
| Networking, client/server split, persistence | Upstream source/JAR references | None | NO IMPLEMENTADO | Audit packets and side-only loading; add persistence/sync | None |
| Assets, shaders, localization, examples | 135 packaged assets in supplied JAR; no data entries | None | NO IMPLEMENTADO | Trace resources to source references and port compatible assets | None |
| Dependency/API compatibility | Artifact requires LDLib2 26.2 and KilaGraph 26.2, Java 21 | Candidate community LDLib2 Forge 1.20.1 source build inspected | PARCIAL | Pin/reproducibly build the candidate; resolve KotlinForForge and other runtime deps; verify Photon API compatibility after permission gate | Candidate library build + its 237 unit tests pass; Photon integration not tested |
| Tests and runtime validation | None in target repo | No test sources yet | NO IMPLEMENTADO | Add focused tests and run Forge client/server checks | `./gradlew test` passed with `NO-SOURCE`; no gameplay/runtime behavior verified |

## Technical path

1. Obtain written authorization from Photon copyright holder KilaBash for the Minecraft 1.20.1 port. The upstream project states ports outside 1.21.x require prior written consent, and modified versions must remain open source under CC BY-NC-SA 4.0, credit Photon, and must not be monetized.
2. Select a compatible source baseline. The target repo contains no source; the supplied JAR is for Minecraft 26.2 / NeoForge / Java 21 and the public upstream branch inspected documents 1.21.1 / NeoForge. Neither is a 1.20.1 Forge source baseline. Do not substitute Photon 1.x for the requested Photon 2.
3. Inventory source, libraries, APIs, classes, assets, and data from the authorized baseline, then map Forge 1.20.1 equivalents and dependency compatibility. Evaluate LDLib2 commit `a8b70b84a36cf9162a95aee7c043b95418315a80` as a UI/editor candidate; it builds but has no published Forge 1.20.1 artifact coordinate.
4. Port the runtime and content formats first, then render pipeline, editor, integrations, and compatibility; verify client and dedicated-server loading and multiplayer synchronization.
5. Build and inspect the final JAR, run requested tests and runtime checks, and record any features that could not be exercised.

## Current blockers and decision

- The target repository is only a README, so there is no existing implementation to continue or compare.
- The supplied JAR is not the requested Minecraft/loader version and does not include project source or example FX data.
- The supplied artifact’s Java 21 / NeoForge 26.2 and LDLib2/KilaGraph 26.2 dependency line must be replaced or ported for the Java 17 / Forge 47 target; no compatibility is assumed.
- The Forge shell build is verified, but it contains only the entrypoint and `pack.mcmeta`; it is not a usable Photon port or release artifact.
- **Implementation based on Photon source or bytecode is on hold pending the upstream author's required written permission for a 1.20.1 port.** The user's authorization to modify this repository does not itself grant that permission.
- A client/server runtime launch has not been done. `test` currently has no test sources; no Photon behavior is verified.

## Bootstrap build evidence

- Forge source: official 1.20.1 MDK for Forge `47.4.26`, downloaded from Forge Maven and SHA-1 verified as `34859392cc1eddcc05a0596d8e0af690a5fe8f17`.
- `./gradlew build --no-daemon --console=plain` with Temurin Java `17.0.20.1`: **BUILD SUCCESSFUL**.
- `./gradlew test --no-daemon --console=plain` with Temurin Java `17.0.20.1`: **BUILD SUCCESSFUL, NO-SOURCE**.
- `git diff --check`: passed.
- Bootstrap JAR: `build/libs/photon-0.0.1-dev.jar`, 1,976 bytes; SHA-256 `e5dffef6a2ac02c81341dc1a220d6176678eef529ccd83d5727113dab7c9d584`.
- JAR currently contains only manifest, expanded Forge `mods.toml`, `Photon.class`, and `pack.mcmeta`. It is not the requested completed mod artifact.

## Dependency candidate validation

- Repository: `https://github.com/Ku00115/LDLib2-Forge-1.20.1`
- Pinned HEAD: `a8b70b84a36cf9162a95aee7c043b95418315a80`
- `./gradlew build --no-daemon --console=plain` with Temurin Java `17.0.20.1`: **BUILD SUCCESSFUL**.
- Unit tests: **237 passed, 0 failed, 0 skipped** across 20 suites.
- Candidate all-in-one JAR: `ldlib2-forge-1.20.1-2.2.28-forge-1.20.1-all.jar`, 5,046,634 bytes; SHA-256 `19df2af170079c73becc4ed859444f5bdf779286d1132cc63103cc2c5a4a54a7`.
- Its metadata requires Forge 47+, Minecraft 1.20.1, and KotlinForForge 4+; the artifact has not been launched in Minecraft or integrated into this Photon project.
