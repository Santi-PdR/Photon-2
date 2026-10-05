# Photon-2 Forge 1.20.1 Port Audit

Audit date: 2026-10-05

## Repository state

- Remote: `https://github.com/Santi-PdR/Photon-2`
- Branch: `main`, tracking `origin/main`
- Starting commit: `6956f47` (`Initial commit`)
- Tracked source tree: only `README.md` (`# Photon-2`)
- Existing build system, mod source, assets, tests, workflows, and port branches: none
- Current port status: **NO IMPLEMENTATION PRESENT**

## Original project and supplied artifact

The upstream Low-Drag-MC Photon project describes Photon as an in-game VFX toolkit/editor. Its documented systems include a Unity-style emitter/particle system, trails and beams, editor and timeline, shader graphs and post-processing, mesh/model particles, FX Packs, commands, and a Java integration API. The upstream `1.21` branch targets Minecraft 1.21.1 with NeoForge 21.1+ and depends on LDLib2.

The supplied `/home/Santipdr/Descargas/photon-neoforge-26.2-26.2.2.3.jar` is a Photon 26.2.2.3 NeoForge build for Minecraft 26.2, not a Forge 1.20.1 reference build. Its metadata requires NeoForge 26.2+, LDLib2, and KilaGraph. Inventory: 882 files (740 classes, 135 assets, 52 shader files, 6 OBJ files); no `data/` entries, `.fx` projects, or `.fxpack` examples. This is useful for identifying a newer implementation and packaged assets, but its target platform/dependencies do not match the requested target.

## Port coverage matrix

| System | Original reference | Current repo | State | Work needed | Verification |
|---|---|---|---|---|---|
| Build/metadata/Forge entrypoint | Upstream project documents NeoForge; supplied artifact metadata targets NeoForge 26.2 | None | NO IMPLEMENTADO | Establish Forge 1.20.1 / Forge 47.x / Java 17 project after permission gate | None |
| Registries, lifecycle, config, commands | Upstream source/JAR references | None | NO IMPLEMENTADO | Port compatible core and registrations | None |
| FX runtime, particles, emitters, modules | Upstream project and JAR | None | NO IMPLEMENTADO | Port runtime semantics and dependencies | None |
| Trails, beams, GPU/render pipeline | Upstream project and JAR | None | NO IMPLEMENTADO | Port renderers and shaders to Forge 1.20.1 | None |
| Timeline, curves, graphs, post-processing | Upstream project and JAR | None | NO IMPLEMENTADO | Port editor/data models and visual pipeline | None |
| Editor GUI and resource browser | Upstream project and JAR | None | NO IMPLEMENTADO | Port GUI and required widget/library capabilities | None |
| Model/mesh sources and FX Packs | Upstream project and JAR | None | NO IMPLEMENTADO | Port loaders, formats, pack/resource handling | None |
| Networking, client/server split, persistence | Upstream source/JAR references | None | NO IMPLEMENTADO | Audit packets and side-only loading; add persistence/sync | None |
| Assets, shaders, localization, examples | 135 packaged assets in supplied JAR; no data entries | None | NO IMPLEMENTADO | Trace resources to source references and port compatible assets | None |
| Tests and runtime validation | None in target repo | None | NO IMPLEMENTADO | Add focused tests and run Forge client/server checks | None |

## Technical path

1. Obtain written authorization from Photon copyright holder KilaBash for the Minecraft 1.20.1 port. The upstream project states ports outside 1.21.x require prior written consent, and modified versions must remain open source under CC BY-NC-SA 4.0, credit Photon, and must not be monetized.
2. Select a compatible source baseline. The target repo contains no source; the supplied JAR is for Minecraft 26.2 / NeoForge and the public upstream branch inspected documents 1.21.1 / NeoForge. Neither is a 1.20.1 Forge source baseline.
3. Inventory source, libraries, APIs, classes, assets, and data from the authorized baseline, then map Forge 1.20.1 equivalents and dependency compatibility.
4. Port the runtime and content formats first, then render pipeline, editor, integrations, and compatibility; verify client and dedicated-server loading and multiplayer synchronization.
5. Build and inspect the final JAR, run requested tests and runtime checks, and record any features that could not be exercised.

## Current blockers and decision

- The target repository is only a README, so there is no existing implementation to continue or compare.
- The supplied JAR is not the requested Minecraft/loader version and does not include project source or example FX data.
- **Implementation based on Photon source or bytecode is on hold pending the upstream author's required written permission for a 1.20.1 port.** The user's authorization to modify this repository does not itself grant that permission.
- No Forge build, tests, client/server launch, or artifact verification has been run because no project exists yet and the port permission is unresolved.
