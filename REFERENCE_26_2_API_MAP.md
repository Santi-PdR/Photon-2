# Photon 26.2.2.3 Reference API Map

This is a behavior/API inventory from the user-supplied `photon-neoforge-26.2-26.2.2.3.jar`. CFR 0.152 generated 410 readable Java source files under `/tmp/photon26-decompiled` for inspection; that decompiler output is not copied into this repository. These notes identify migration work and are not a claim that any Photon subsystem is ported.

## Startup, sides, and configuration

- `Photon` is the mod entrypoint (`photon`). It initializes common infrastructure, then selects a client proxy or common/server proxy from the physical distribution.
- The client registers client configuration and a config screen; the common/server path avoids constructing the client proxy. The 1.20.1 port must preserve this isolation because the render/editor packages are client-only.
- The config currently has 11 entries covering bloom, Iris compositing, FX compositing, custom effects, and post-FX memory budget.
- The common proxy registers two command-argument types, networking registration, and Photon registry initialization.

## Registries and content model

Six Photon registries are initialized through LDLib2: FX object types, materials, number functions, shapes, model sources, timeline tracks, and animated properties. Several use LDLib2 annotations and classpath scanning rather than vanilla block/item registries. An equivalent Forge 1.20.1 implementation must preserve stable string IDs and registration order while replacing any incompatible LDLib2 APIs.

## Commands and networking

- Server command tree: `/photon fx <location> block|entity ...` and `/photon remove block|entity ...`; client commands are registered separately.
- Four packet types are registered for client playback/removal: block effect, entity effect, remove block effect, and remove entity effect. The inspected packet registrar sends these to clients; handlers then resolve FX data and create/stop client runtimes. No C2S Photon payload is registered by the inspected network class.
- The current implementation uses NeoForge `RegisterPayloadHandlersEvent`, `CustomPacketPayload`, `StreamCodec`, and `PacketDistributor`.
- Forge 1.20.1 should use a versioned `SimpleChannel`, explicit message IDs, `FriendlyByteBuf` encode/decode, and side-aware handlers. Forge's documented SimpleImpl supports client, tracking-chunk, and all-player sends. Thread scheduling, entity/level lookup, and payload bounds need explicit validation during the port.

## Client runtime, resources, and rendering

- Client startup registers render pipelines and particle groups; adds FX Pack resource repositories; and installs mesh, FX, shader, and post-FX reload listeners.
- Frame and level events drive material/post-FX previews, post-FX composition, world-state cleanup, opaque depth capture, Iris interop, and GUI overlays.
- The supplied JAR has 311 `client/gameobject` classes, 71 FX/runtime classes, 92 render classes, 77 post-FX classes, 16 shader-graph classes, 75 editor classes, and 26 UI test/scenario classes.
- The current client event path references `RegisterRenderPipelinesEvent`, `FrameGraphSetupEvent`, `RenderFrameEvent`, and newer render-state camera objects. Forge 1.20.1 has no direct API-equivalent for the whole 26.2 render graph path; it needs a separately designed render-stage/frame lifecycle using 1.20.1 events, render targets, and targeted mixins.
- The JAR declares a required Mixin plugin and client mixins for particle engine, shader manager, global shader uniforms, and Iris internals. These are high-risk API seams requiring individual compatibility checks.

## Java and Minecraft API deltas

| Reference API (26.2 / NeoForge) | Forge 1.20.1 migration direction |
|---|---|
| Java 25 classfiles (major 69) | Recompile source for Java 17; do not load/reference the supplied bytecode as a runtime dependency |
| `net.minecraft.resources.Identifier` | `ResourceLocation` |
| `RegisterPayloadHandlersEvent` + `StreamCodec` custom payloads | Forge `SimpleChannel` messages with `FriendlyByteBuf` codecs and explicit protocol version |
| NeoForge `DeferredRegister` / event packages | Forge 47 registries and event-bus packages |
| New render-pipeline/frame-graph events and render-state camera | Rebuild against Forge 1.20.1 render stages and rendering APIs; use narrowly scoped mixins where Forge events cannot preserve behavior |
| New command permission-check API and identifier argument types | Brigadier/Forge 1.20.1 command APIs and argument registration |

The Forge networking direction is based on the [Forge 1.20.x networking guide](https://docs.minecraftforge.net/en/1.20.x/networking/); client-only particle provider registration is documented as client-sided in the [Forge particle guide](https://docs.minecraftforge.net/en/1.20.1/gameeffects/particles/).

## Dependencies

The reference metadata requires NeoForge 26.2+, Minecraft 26.2, LDLib2 `[26.2.2.41.a,)`, and KilaGraph `[26.2.0.15,)`; KilaGraph 26.2.0.15 is jar-in-jar. These exact dependency lines cannot satisfy the Forge 47 / Java 17 target. A community LDLib2 Forge 1.20.1 candidate builds and has had a limited client startup smoke test, but it is not yet integrated, its client run did not reach a confirmed main-menu checkpoint, and no Photon API compatibility test exists.

## Port coverage from this reference

| Area | Evidence from reference | Port state |
|---|---|---|
| Bootstrap/config/registries | Entrypoint, proxy, config, annotation-driven registries inspected | STUB in target repo |
| Commands/packets | Command tree and four S2C packet families inspected | NO IMPLEMENTATION |
| FX runtime/object graph | Class/resource inventory only; behavior mapping still needed | NO IMPLEMENTATION |
| GPU render/post-FX/shader graph | Classes, resources, events, and mixins inventoried; major API redesign required | NO IMPLEMENTATION |
| Editor/timeline/FX Packs | Package/resource inventory only | NO IMPLEMENTATION |
| Dedicated-server/client compatibility | Common/client initialization split inspected; not tested in target | NO IMPLEMENTATION |

Original-code port implementation remains pending the upstream author's stated written-authorization condition for versions outside 1.21.x.
