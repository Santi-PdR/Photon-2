# Photon 2 upstream: `26.2.2.9` → `26.3.2.11`, qué se puede portar y qué no

Fecha del análisis: 2026-11-07. Upstream es [Low-Drag-MC/Photon](https://github.com/Low-Drag-MC/Photon)
(rama `1.21` = Photon 1.21.1 / 2.2.x, ramas `26.1`/`26.2`/`26.3` = los ports nuevos).

## Cómo se obtuvo (para poder repetirlo)

El CDN de Modrinth **no es alcanzable** desde el sandbox (`cdn.modrinth.com` corta el TLS con
`SSL_ERROR_SYSCALL`), así que el JAR de `26.3.2.11` no se puede bajar aquí. El código fuente sí:

```bash
git clone --depth 1 --branch 1.21 https://github.com/Low-Drag-MC/Photon.git upstream
cd upstream
git fetch --depth 1 origin 26.2:upstream262
git fetch --depth 1 origin 26.3:upstream263
git diff --stat upstream262 upstream263     # el diff real de la versión
```

`gradle.properties` de la rama `26.3` declara `mod_version=26.3.2.11`, `minecraft_version=26.3`,
`ldlib2_version=26.3.2.45.a`. Es decir: **26.3 no es un parche de 26.2, es otro Minecraft**.

## Nuestra base

`gradle.properties` de este repo: `minecraft_version=1.20.1`, `mod_version=26.2.2.9-forge.1.20.1`.
El port está sobre la lógica de **26.2.2.9**, en Forge 1.20.1, con LDLib2 `2.2.39.a+forge.1.20.1`.

## El diff, en números

`git diff --stat upstream262 upstream263 -- src/main/java` → **147 archivos, +5.334 / −1.128**.
De esos, 16 archivos nuevos (6 en `client/render`, 4 mixins, 6 escenarios de UI test) y 3 borrados
(accessors de `GpuDevice`/`ShaderKey`/`DeviceCaches`, que en 26.3 no existen).

Archivos nuevos de 26.3:

| Archivo | Qué es |
| --- | --- |
| `client/render/PhotonOit.java` (291 l.) | transparencia order-independent: variantes de pipeline por draw |
| `client/render/PhotonOitComposite.java` | el pase de composición de lo anterior |
| `client/render/PhotonFrameSeams.java` (231 l.) | los dos puntos sin pase abierto donde Photon puede dibujar |
| `client/render/PhotonBakeWork.java` (192 l.) | geometría/instancias en hilos de trabajo (`parallel_bake`) |
| `client/render/PhotonRecordArena.java` | arena de grabación de draws |
| `client/render/PhotonRenderStats.java` | contadores del nuevo camino de render |
| `core/mixins/LevelRendererMixin.java` | corta el pase del nivel en los seams |
| `core/mixins/WorldSceneRendererMixin.java` | lo mismo para una escena de LDLib2 |
| `uitest/{Oit,OitShowcase,EditorOit,FrameSeams,RenderBench,LifetimeModesRender}Scenario.java` | escenarios de verificación |

Cambios grandes por archivo: `PhotonWorldRenderState` (+530), `Emitter` (+340),
`CustomShaderAdapter` (+295), `TileParticleRenderer` (+105), `DynamicLightRenderer` (+93).

## Qué hay en 26.3 que este port no tiene

### a) Exclusivo de 26.3 (no se puede portar a 1.20.1)

Todo lo de `26.3.2.10` es **render de Minecraft 26.3**: `OitPipelineSet`/`OitStage`, `RenderPipeline`
de `com.mojang.renderpearl`, `LevelRenderer` partido en pases, `Identifier` en vez de
`ResourceLocation`. Nada de eso existe en 1.20.1 ni en LDLib2 `2.2.39.a`, y no tiene equivalente
razonable: la transparencia mejorada es una feature del motor nuevo, no del mod.

- Order-independent transparency (config `order_independent_transparency`, AUTO/OFF por emitter).
- FX translúcidos después de nubes y clima; FX opacos dentro de la capa del shader pack.
- Los dos "frame seams" y los mixins de `LevelRenderer`/`WorldSceneRenderer` que los abren.
- `parallel_bake` (bake en hilos de trabajo) y el estadístico de renders nuevos.
- Adaptación automática de shaders 26.1/26.2 a las reglas GLSL de 26.3 (`#include`, locations
  explícitas, `gl_VertexIndex`).

### b) Compartido con la rama 1.21 / 2.2.x — **sí se puede portar** (esto es lo interesante)

Los primeros siete puntos del changelog están también en la rama `1.21` como **v2.2.11**
(ver `CHANGELOG.md` de la rama `1.21`), o sea, misma generación de Minecraft y misma familia de
LDLib2. Son portables a mano:

| Feature | Archivos upstream | Estado en este port | Veredicto |
| --- | --- | --- | --- |
| **Timeline sections** (start/loop/end con nombre, `FXRuntime.sections()`, `/photon fx section`) | `fx/timeline/TimelineSection.java`, `SectionClock.java`, `SectionControl.java`, `command/EffectSectionCommand.java`, `gui/editor/view/timeline/SectionStrip.java` | runtime + comando + menú de regla **sí**; banda gráfica `SectionStrip` **sí** (port 26.3 → LDLib2 2.2.x) | el más valioso para efectos de combate |
| **Release sections** (parar sin forzar reproduce un cierre) | `TimelineSection` + `Emitter`/`FXObject` | sí (con sections) | medio |
| **Exit mode `Drain`** en control clips (deja de emitir y las partículas se apagan) | `Emitter` | sí | barato y visible |
| **Lifetime `Loop` / `PingPong`** por partícula | `gameobject/particle/ParticleLifetime.java` | sí | barato |
| Fix: efectos parados sin forzar revivían objetos, partículas con Start Lifetime 0 no morían, luces se apagaban antes | `FXRuntime.isTracked`, `FXObject.isDiscarded`, `FXInstanceObject.windDown`, `TileParticle.endAge` | **sí** | luces/partículas siguen en pantalla durante el fade |
| Fix: animaciones del timeline tartamudeaban con rate ≠ 1 | `TimelinePlayer.frame` (`partialTicks * frameRate`) | **sí** | interpolación al rate del último tick |
| Fix: `/photon fx remove entity` saltaba los siguientes objetivos | `EffectRemoval` (`continue`) | **sí** | no corta el resto de entidades |

De `26.2.2.10` (entre nuestra base y 26.3), el port ya cubre lo portable:

- `ParticleEmitter(ParticleConfig)` público;
- glTF recarga por `PhotonMeshCache.pollFileChanges` / `invalidate`;
- billboards world/custom en `TileParticleRenderer.applyOuterRotation`;
- FXPack manager con `ScrollerView` 360×240;
- pegar sobre curva no revierte (`CurveConfigurator.setPastable(false)`);
- *Revert* de instancia FX en jerarquía y diálogo de overrides;
- traducciones de 26.2 completas; claves de Drain/Seam del clip de control añadidas.

## Recomendación de orden

1. **Lifetime Loop/PingPong** (48 l., cero riesgo, mejora inmediata de efectos largos).
2. **Exit mode Drain** (visible en el editor, no toca el formato de archivo).
3. **Verificar los dos fixes** (rate ≠ 1 y `fx remove entity`): si el bug existe en el port, son
   arreglos pequeños con impacto real.
4. **Timeline sections**: proyecto aparte; añade datos al `.fxproj`, así que necesita versión de
   formato y migración. No meterlo en un parche suelto.

## Lo que este análisis NO afirma

- No se pudo ejecutar el JAR de 26.3 (sin JDK en el sandbox y sin CDN), así que la lista de
  features sale del **código fuente y el changelog**, no de probar el mod.
- El diff es entre `26.2` y `26.3` de upstream. Nuestra base es `26.2.2.9`, anterior a `26.2.2.11`:
  los puntos de la sección (b) marcados "puede afectarnos" hay que confirmarlos leyendo el diff
  `upstream262` contra nuestro archivo equivalente.
