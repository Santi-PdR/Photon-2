# Photon 2 — Forge 1.20.1 · Contexto completo del proyecto

> Documento de traspaso. Reúne **todo** el contexto acumulado: qué es el proyecto, qué se ha
> portado, qué se arregló y por qué, cómo se verifica, cómo se distribuye, y qué queda pendiente.
> Si alguien (Santi, Alex u otra persona o IA) retoma esto sin el historial del chat, empieza aquí.
>
> Última actualización: **2026-11-07** · commit de referencia `0fbf2ad` · build CI #526 ✅

---

## 1. Resumen en cinco líneas

- Es el **port de Photon 2 (`v26.2.2.9`) a Minecraft 1.20.1 / Forge 47.x**, Java 17.
- El JAR distributivo (`-all.jar`) **incluye LDLib2 y KilaGraph**; LDLib 1 sigue siendo dependencia externa.
- Ya funciona: editor visual, FX, materiales Kila, trails, beams, luces dinámicas, bloom, audio OGG, **`.fxpack`** y **sincronización de packs desde servidor**.
- Se arreglaron bugs reportados por Alex: bloom lavado, materiales animados, trails a alta velocidad, shaders ausentes, y el **selector de FX** (lista congelada).
- Estado del repo: `main` + ramas de trabajo; este documento acompaña la limpieza del repositorio a una sola rama.

---

## 2. Identidad del proyecto

| Campo | Valor |
| --- | --- |
| Repositorio | [Santi-PdR/Photon-2](https://github.com/Santi-PdR/Photon-2) |
| Base upstream | Photon `v26.2.2.9` (NeoForge) |
| Destino | Minecraft **1.20.1**, **Forge 47.x** (compila con `47.4.28`, declara mínimo `47.4.10`), **Java 17** |
| Mod id | `photon` |
| Licencia | CC BY-NC-SA 4.0 |
| Artefacto | `photon-forge-1.20.1-26.2.2.9-forge.1.20.1-all.jar` |
| Tamaño aprox. | ~13.4 MB |
| Código | 575 archivos `.java`, 822 archivos versionados |

### Dependencias

| Componente | Versión / configuración | Papel |
| --- | --- | --- |
| Minecraft | 1.20.1 | Versión objetivo |
| Forge | build `47.4.28`; mínimo declarado `47.4.10` | Loader y runtime |
| Java | 17 | Toolchain |
| LDLib2 | `2.2.39.a+forge.1.20.1` | Editor y APIs base — **empaquetado dentro del JAR** |
| KilaGraph | `20.1.0.15` | Grafo de shaders y materiales Kila — **empaquetado dentro del JAR** |
| LDLib | `1.0.52.a` | Dependencia externa requerida (no sustituida por LDLib2) |
| Oculus / Iris | opcional | Compatibilidad de shaderpacks (el bloom con shaderpack tiene su propio ajuste) |

Las restricciones finales están en `src/main/resources/META-INF/mods.toml`; versiones de compilación en `gradle.properties` y `build.gradle`.

### Entorno de referencia de Alex (para reproducir reportes)

Minecraft 1.20.1 · Forge `47.4.20` · Photon (build de este repo) · LowDragLib2 `2.2.39` · KilaGraph ·
GPU **AMD RX Vega 11**, OpenGL 4.6. Servidor sin packs de FX (0 ofrecidos) en el momento del reporte.

---

## 3. Qué ofrece el mod hoy

- **Editor visual** para crear y previsualizar efectos (`.fx`) y proyectos (`.fxproj`), con jerarquía, timeline, inspector, previsualización en escena.
- **Emisores y partículas**: materiales, formas (shapes), renderers, modelos (OBJ / glTF / JSON), texturas, shaders, trails, beams, ara-trails, force fields, colisiones.
- **Iluminación dinámica** con sombras (voxel/screen/contact), luces y volúmenes de niebla.
- **Post-procesado**: bloom, máscaras (`CustomMask`), graph de shader y grafo fullscreen, clips de post-proceso.
- **Materiales Kila** con presets, módulos (mask, dissolve, ramp, fresnel, UV, flipbook…), bindings a datos de partícula.
- **Carga por namespace** e importación de contenido **Photon 1** (`.fx` y `.fxproj` crudos o gzip, assets legados).
- **Audio OGG** desde la pista Audio (clip, loop, volumen, pitch, categoría, atenuación).
- **`.fxpack`**: paquete ZIP autosuficiente para repartir un efecto con sus assets.
- **Sincronización de packs desde servidor**: el cliente descarga y monta solo los packs que le faltan.
- **Tiempo de juego** en materiales animados (`GameTime`) y fallbacks seguros cuando falta un shader.

---

## 4. Arquitectura y mapa del código

Rutas relativas a `src/main/java/com/lowdragmc/photon/`.

### 4.1 Arranque y registro

| Archivo | Función |
| --- | --- |
| `Photon.java` | Mod entry, registro de assets/paths, logs. |
| `PhotonClientProxy.java` | Setup de cliente: `addPackFinders`, reload listeners, `FXHelper.clearCache()`, migración de defaults de bloom, montaje de packs. |
| `PhotonCommonListeners.java` | Login/logout → `FxPackSync`. |
| `PhotonRegistries.java` | Registros `LDLRegistry.String` (`fx_object`, `timeline_track`, `animated_property`) y `AutoRegistry` (`material`, `number_function`, `shape`, `model_source`). |
| `PhotonConfig.java` | Config de Forge: bloom (`enable_bloom`, `bloom_mip_level`, `bloom_threshold`, `bloom_intensity` 0..2 default 0.18, Iris), `fx_composite_mode`, `enable_custom_effects`, post-FX budget, luces dinámicas, colisiones. |
| `PhotonNetworking.java` | Canal de red (manifiesto/chunks de packs). |

### 4.2 FX y packs

| Archivo | Función |
| --- | --- |
| `client/fx/FXHelper.java` | Carga y caché de `.fx`; `listAllFX()` (cacheado, lo invalida `clearCache()`), **`searchFX(...)`** (búsqueda en vivo), `readTag` (NBT crudo o gzip), `applyVersionFixes`, `preload/keepPreloaded`. |
| `client/fx/fxpack/FXPacks.java` | Montaje de `.fxpack` como resource packs de prioridad baja, `rewrite` del zip, `resolve`, gc de assets compartidos. |
| `client/fx/fxpack/FXPackExporter.java` | Exportación: `.fx` + texturas + modelos + shaders + materiales/graphs (`assets/photon_fx/`) + audio. |
| `client/fx/fxpack/ClientFxPackSync.java` | Descarga por chunks, verificación SHA-256, recarga automática, limpieza al desconectar. |
| `network/FxPackSync.java` | Lado servidor: escaneo de `photon/fxpacks/`, manifiesto, streaming por `RandomAccessFile`, límites. |
| `gui/editor/FXProjectConverter.java` | `.fx`/`.fxproj` → proyecto del editor; repunta recursos empaquetados a `<proyecto>_resources/`. |
| `gui/editor/FXFileActions.java` | Exportar FX, exportar FXPack, convertir Photon 1, avisos de resolución (live/shadowed/not mounted). |
| `gui/editor/FXInstanceStyle.java` | Picker de FX para instancias (usa `FXHelper.searchFX`). |

### 4.3 Emisores y datos

| Archivo | Función |
| --- | --- |
| `client/gameobject/emitter/particle/ParticleEmitter.java` | Emisor principal (`TYPE`, iconos, bindings). |
| `.../emitter/data/SubEmittersSetting.java` | Sub-emisores; configurador del FX objetivo (picker en vivo). |
| `.../emitter/data/IdLists.java` | Buscadores de bloques/entidades/`#tags` (relee el registro por búsqueda). |
| `.../emitter/data/material/{TextureMaterial,CustomShaderMaterial,...}` | Materiales; `CustomShaderMaterial` degrada con aviso si el shader no existe. |
| `.../material/kila/*` | Material Kila, presets, módulos, picker de texturas, preview con `GameTime`. |
| `client/postprocessing/PhotonPostProcessing.java` | Cadena de post-proceso; bloom con máscara de escena. |
| `client/gameobject/emitter/renderpipeline/RenderPassPipeline.java` | Destinos, `isSceneBackedBuild()`, composición tardía. |

### 4.4 Editor (UI)

| Archivo | Función |
| --- | --- |
| `gui/editor/view/scene/SceneView.java` | Vista de escena; barra superior con toggles (bloom, efectos, luces, máscara, shape, cull, colisión, vistas) y el **control de intensidad de bloom**. |
| `gui/editor/view/scene/SceneToggleBuilder.java` | Constructor de toggles con icono/tooltip. |
| `gui/editor/view/scene/SceneBloomIntensityControl.java` | **Nuevo**: slider `0.00`–`2.00` + lectura numérica que escribe `bloom_intensity`. |
| `gui/editor/view/scene/FXObjectInfoView.java` | Panel flotante de información de escena (tiempo, pausa, seed, timers). |
| `gui/editor/view/FXHierarchyView.java` | Jerarquía y menú contextual (Add FX Object / Add FX Instance…). |

### 4.5 Assets y shaders

- `assets/photon/shaders/core/`: `hdr_particle`, `pixel_hdr_particle`, `bright_pass` (con `SceneMask`), `down_sampling`, `up_sampling`, `bloom_final_scatter_pass`.
- `assets/photon/textures/kila/`: 23 texturas integradas + carpeta `ui`.
- `assets/photon/textures/particle/`: `circle`, `smoke`, `ring`, `laser`, `kila_tail`, `thaumcraft`, `white`.
- `assets/photon/lang/`: `en_us.json`, `es_es.json`, `zh_cn.json`.

> Nota: **no existe** un shader `photon:slash` en el mod (solo el preset Kila `KilaPresets.SLASH` y la textura `KilaTextures.SLASH`).

---

## 5. Rutas de usuario en disco

```text
<instancia>/photon/
├── assets/<namespace>/...      # effects, PNG, modelos, shaders (workspace montado como resource pack)
├── projects/*.fxproj           # proyectos editables
├── resources/global/           # recursos del editor: materiales, mallas, curvas, graphs
└── fxpacks/                    # .fxpack instalados o exportados  (+ server-<hash>.fxpack del servidor)

<servidor>/photon/fxpacks/      # packs que el servidor reparte automáticamente
```

También se montan como assets legados: `<instancia>/ldlib/assets` (Photon 1) y `<instancia>/ldlib2/assets` (Photon 2 previo al workspace).

---

## 6. Cronología de problemas y arreglos

### 6.1 Base del port

Cientos de commits adaptando APIs NeoForge → Forge 1.20.1: NBT, modelos OBJ (loader `neoforge:obj` registrado), mixins (particle engine, viewport), líneas de tiempo, datos de persistencia LDLib2, etc. El CI compila, arranca servidor dedicado y arranca cliente headless con Xvfb + software GL.

### 6.2 Reportes de Alex (capturas de chat) y respuestas

| Reporte | Diagnóstico | Arreglo |
| --- | --- | --- |
| Bloom lava la escena / no se ve | Umbral y máscara mal calibrados | `bright_pass` con sampler de escena y `SceneMask`; umbral con el canal RGB más brillante; migración de defaults viejos (`0.7/1.0 → 0.18`, `1.001/0.1/0.15/0.65 → 0.5`) |
| Materiales animados congelados | Falta `GameTime` en la preview Kila | `KilaSlotPreview` sube `RenderSystem.getShaderGameTime()` |
| `Failed to find shader photon:slash` | El proyecto referencia un shader no instalado | `CustomShaderMaterial` registra **un warning de una línea** y usa el shader de partículas integrado (`compiledErrorMessage`/`shaderCleanable` quedan `null`) |
| Uniforms `Threshold`/`Knee` sobrando | Pasadas del port | Eliminados de `bloom_final_scatter_pass.json` |
| Trails a alta velocidad | Muestreo insuficiente | Sub-muestreo con tope de 64 puntos + prueba de gradiente de color |
| Textura `brutality:...trail_particle.png` ausente | El asset no existe en la instancia | Se documenta: el exportador avisa la ruta; no puede inventar el archivo |
| **"Sigue sin funcionar el cosito de elegir partículas"** | Los buscadores (`SearchComponentConfigurator`) copiaban los candidatos **una sola vez** al construir el configurador y nunca se refrescaban → lista vacía si el panel se abrió antes de la primera carga de recursos o después de una recarga | `FXHelper.searchFX(...)` lee la lista (cacheada) **en cada búsqueda**; lo usan sub-emisores, `FXInstanceStyle.pickFx` y `IdLists` |
| Pedido: botón para cambiar intensidad de bloom | Solo existía en la pantalla de config | `SceneBloomIntensityControl` en la barra de escena: slider 0..2 + valor, sincronizado con la config, **sin tocar el default** |

### 6.3 Pedido de Alex: explicar `.fxpack` y el flujo de servidor

Respondido con [`Photon2-FXPack-Guia-Servidor.md`](Photon2-FXPack-Guia-Servidor.md) (lenguaje llano) y resumido en la sección 8.

---

## 7. Cómo funciona `.fxpack` (resumen técnico)

1. **Contenedor**: ZIP con layout de resource pack: `assets/<ns>/fx/<nombre>.fx` + todo lo que referencia + `assets/photon_fx/` (materiales, graphs, mallas empaquetadas).
2. **Exportación** (editor → *File → Export FXPack…*): serializa el FX, camina el NBT, y arrastra texturas/modelos/shaders/audio; los recursos de librería se copian bajo `photon_fx` y las referencias se reescriben.
3. **Montaje local**: cada `<instancia>/photon/fxpacks/*.fxpack` (y cada carpeta descomprimida) se registra como pack **oculto, siempre activo, de prioridad más baja**. Un resource pack normal o el workspace del autor tienen prioridad.
4. **Detección**: requiere recarga de recursos (F3+T) para archivos nuevos; `FXPacks.rewrite` sirve ediciones de packs ya montados sin recargar.
5. **IDs**: `namespace:nombre` (namespace = nombre del `.fxpack`; ruta = nombre dado al exportar; ambos saneados a `[a-z0-9/._-]`).

### 7.1 Distribución desde servidor

```text
login → servidor escanea <server>/photon/fxpacks/*.fxpack
      → manifiesto (nombre, tamaño, SHA-256) al cliente
      → cliente pide solo hashes que no tiene (chunks 24 KiB)
      → valida hash + formato (debe contener assets/*/fx/*.fx)
      → guarda photon/fxpacks/server-<hash>.fxpack
      → recarga recursos automáticamente
logout → desmonta los packs del servidor (los del jugador se quedan)
```

Límites en código (`FxPackSync`): **32 packs**, **64 MiB por pack**, **128 MiB totales**, **256 MiB expandidos**, **20 000 entradas**; rechaza rutas ZIP sospechosas (`../`, `\`, absolutas).

### 7.2 Lo que NO hace

- No actualiza packs en caliente a jugadores ya conectados (se anuncia en el login).
- No sustituye al mod: sin Photon en el servidor no hay anuncio; sin Photon en el cliente no hay render.
- No transporta código: solo contenido.

---

## 8. Verificación (CI)

Workflow: `.github/workflows/forge-1.20.1.yml` (Java 17, Gradle, Ubuntu).

Pasos: build → verificación de mixins/refmap en el JAR → subida del distributable → arranque de **servidor dedicado** → preparación de mundo + fixtures (`ci-fixtures/real-{scan,shield-trail,tornado}`) + pack de prueba de servidor → **cliente headless** con Xvfb y GL software → ~25 verificaciones por `grep` en el log.

Marcas de humo relevantes (deben aparecer en el log del cliente):

```
[bloom-smoke] red FX halo is present while non-highlight scene pixels stay intact
[fx-picker-smoke] the fx search lists every loadable effect
[bloom-control-smoke] dragging the scene bar's bloom slider writes bloom_intensity=…
[builtin-material-picker-smoke] Kila and all built-in material presets are listed
[trail-fast-movement-smoke] … 64-point cap
[trail-colour-smoke] red-to-blue trail gradient …
[fxproj-smoke] loaded synthetic .fxproj with burst
[photon1-editor-project-smoke] opened both raw Photon 1 .fxproj layouts
[server-fxpack-sync-smoke] server-only FX pack transferred in chunks …
```

Los escenarios de UI (`uitest/`, LDLib2 `UIScenario`) cubren inspector Kila, picker de texturas, galería de presets, instancias FX, luces dinámicas, packs, etc. Se activan con `-Dphoton.fxprojSmokeTest=true` y similares.

### Comandos útiles

```bash
# Build local
./gradlew build

# Descargar el último JAR construido por CI
gh run download <RUN_ID> -R Santi-PdR/Photon-2 -n photon-forge-1.20.1

# Forzar el smoke completo en local
./gradlew runClient -Dphoton.fxprojSmokeTest=true
```

---

## 9. Estado de los entregables

| Build | Commit | CI | Artifact |
| --- | --- | --- | --- |
| Ronda 1 (bloom + fallbacks) | `b9489f2` | run `42310663551` ✅ | `photon-forge-1.20.1` id `13548900490` |
| **Ronda 2 (picker + control de bloom + guía)** | `0fbf2ad` | run `42384055373` (build **#526**) ✅ | `photon-forge-1.20.1` id `13578638461` (13 378 526 B) · diagnóstico `13578569377` |
| Docs y limpieza de repo | `c5bf27b` | run `42386451673` ✅ | — (solo documentación) |
| **Merge del port a `main`** | `61fcf63` | PR #1 ✅ | build lanzado desde `main` |

- Ruta de descarga directa: `https://github.com/Santi-PdR/Photon-2/actions/runs/42384055373/artifacts/13578638461`
- Los artifacts de GitHub caducan a los **90 días**.
- En `mods/` debe quedar **un solo** `photon-…-all.jar`; borrar duplicados tipo `…-all (2).jar` antes de probar.

### JARs validados históricamente (los `backup/*` borrados)

Estos registros vivían en las ramas `backup/photon-forge-*`, que se eliminaron al limpiar el repo. Se conservan aquí porque eran su único contenido útil:

| Run | Commit | JAR SHA-256 | Tamaño |
| --- | --- | --- | --- |
| `40427683465` (#155) | `9be43ef0…` | `3157688AF68B58BF369B4F90C41849BBEC580F91AB9CDCC77F3FB952537E31EE` | 13 450 945 B |
| `40431947405` (#157) | `0d74f6a9…` | `7E34D88C14CD5CA4C4A09915914014B2CEAEA3440B867178C870B5641B80EBF4` | 13 451 717 B |
| `40522056464` (#177) | `2a0e0f9d…` | `950348E8730980BEFD77209223FDE0ED98D6F0C14A631F3AE369C8633E44FB84` | 13 453 205 B |

(Sus artifacts de GitHub ya expiraron el 2026-11-10; los builds actuales están en la tabla anterior.)

---

## 10. Estado del repositorio y limpieza de ramas

### 10.1 Situación final (2026-11-07)

El port **está en `main`**. Estado tras la limpieza:

| Rama | Estado |
| --- | --- |
| `main` | **Rama única de trabajo.** Contiene el port completo + rondas 1 y 2. Commit de merge `61fcf63`. |
| `arena/9edfd416-photon-2` | Rama de la sesión del agente en Arena; se conserva porque la plataforma la usa para seguir el trabajo. Apunta al mismo contenido que `main` y no debe usarse como base de desarrollo. |

Ramas **eliminadas** (todas verificadas antes con `git diff`/`git merge-base`):

| Rama | Por qué se pudo borrar |
| --- | --- |
| `port/forge-1.20.1-2.2.9` | Ancestro directo de `main` tras el merge del PR #1. |
| `fix/photon-owned-workspace` | 0 commits propios; contenido ya integrado. |
| `backup/port-forge-before-alex-captures-24d4306` | 0 commits propios; snapshot previo a las capturas de Alex. |
| `fix/alex-four-captures-2026-11-06` | 27 commits, todos integrados vía PR #3 (squash). Sus diferencias son solo el estado **anterior** del código que la ronda 2 ya reemplazó. |
| `port/forge-1.20.1-sync-sound` | 4 commits, integrados vía PR #2 (squash). |
| `backup/photon-forge-1.20.1-run155` | Solo notas de JARs validados → **rescatadas en la sección 9**. |
| `backup/photon-forge-run177` | Solo notas de JARs validados + workflows temporales (`backup-run177.yml`, `verify-candidate-run205.yml`). |

### 10.2 Política recomendada de aquí en adelante

- Todo entra por **PR a `main`**; ramas de trabajo cortas (`fix/...`, `feat/...`) borradas al mergear.
- Para hitos estables usar **tags** (ya existe `v26.2.2.9-forge.1.20.1`), no ramas `backup/*`.
- Los JARs se publican como **release adjunta al tag**; los artifacts de Actions caducan a los 90 días.
- El contexto se mantiene en **este documento**, no repartido en ramas.

## 11. Pendientes y siguientes pasos sugeridos

1. **Verificación en el cliente de Alex** del build `0fbf2ad`: picker de FX (sub-emisores e instancias) y slider de bloom.
2. Si el selector sigue fallando: pedir `latest.log` de la sesión concreta y revisar si el fallo está en la apertura del desplegable (no en la lista de candidatos).
3. **Iris/DH**: revisar `iris_composite_mode` y el bloom bajo shaderpacks en hardware AMD.
4. **Rendimiento**: presupuesto de post-FX (`postfx_pool_budget_mb`), actualización de partículas en paralelo con colisiones.
5. Considerar publicar el JAR como **release con tag** para que los jugadores tengan enlace permanente (los artifacts caducan).

---

## 12. Referencias rápidas

| Documento | Uso |
| --- | --- |
| [`Photon2-FXPack-Guia-Servidor.md`](Photon2-FXPack-Guia-Servidor.md) | Guía llana de `.fxpack` y del flujo de servidor |
| [`Photon2-FX-Assets-and-Bloom.md`](Photon2-FX-Assets-and-Bloom.md) | Layout de assets, recursos ausentes, controles de bloom |
| `docs/Photon2-Port-Context.md` | Contexto original del port (se mantiene) |
| `.github/workflows/forge-1.20.1.yml` | Definición real de la verificación |
| `ci-fixtures/` | Proyectos reales usados por el smoke (`scan`, `tornado`, `shield-trail`) |
