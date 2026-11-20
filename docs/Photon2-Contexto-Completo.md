# Photon 2 — Forge 1.20.1 · Contexto completo del proyecto

> Documento de traspaso. Reúne **todo** el contexto acumulado: qué es el proyecto, qué se ha
> portado, qué se arregló y por qué, cómo se verifica, cómo se distribuye, y qué queda pendiente.
> Si alguien (Santi, Alex u otra persona o IA) retoma esto sin el historial del chat, empieza aquí.
>
> Actualizado: **2026-11-19** · integra el contexto del chat, la transferencia/exportación de recursos con consentimiento previo, los merges PR #9/#10/#11, la ronda de auditoría de la rama `arena/2db16c95-photon-2` (sección 6.5) y su cierre: regresiones de codec/ZIP, traducción `es_es` completa y gate de paridad de idiomas en CI. CI: `44449444574`, `44450496049`, `44458049882`, `44458368154`, `44460817801` ✅ · [JAR preview](https://github.com/Santi-PdR/Photon-2/releases/download/preview-44460817801-1/photon-forge-1.20.1-26.2.2.9-forge.1.20.1-all.jar).

---

## 1. Resumen en cinco líneas

- Es el **port de Photon 2 (`v26.2.2.9`) a Minecraft 1.20.1 / Forge 47.x**, Java 17.
- El JAR distributivo (`-all.jar`) **incluye LDLib2 y KilaGraph**; LDLib 1 sigue siendo dependencia externa.
- Ya funciona: editor visual, FX/materiales, bloom, sincronización pública de `.fxpack`, transferencia privada de `ldlib2/assets` a un jugador y exportación local de un pack compartible.
- Se arreglaron bugs reportados por Alex: bloom lavado, materiales animados, trails a alta velocidad, shaders ausentes, y el **selector de FX** (lista congelada).
- Estado verificado al cierre del chat: PR #9 (funcionalidad + consentimiento + guías) y PR #10 (actualización del handoff/JAR) están mergeados en `main`; merge commits `e774dec` y `1064853`. Después se mergeó PR #11 (handoff completo + guías de verificación, merge `36df1b3`). La rama Arena activa de esta sesión de auditoría es `arena/2db16c95-photon-2`, basada en `36df1b3117ab83ec087857c1814a9f7a65bdfa80`; los hallazgos corregidos están en la sección 6.5.

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
- **Materiales Kila** con presets, módulos (mask, dissolve, ramp, fresnel, UV, flipbook…), bindings a datos de partícula y galería organizada por destino. Los presets recientes incluyen cristal esmerilado, limo tóxico, roca incandescente, llama de alma, estela de aurora, estallidos de lava/glacial y cuarenta más: aura curativa, niebla tóxica, estallido de agua, estela de cometa, portal del vacío, rayo solar, runas de suelo, escudo prismático, fragmento de cristal, remolino de polvo, estallido de brasas, anillo glacial, relámpago ramificado, estela de plasma, impacto de meteorito, obsidiana, manto de sombras, portal estelar, vaho ascendente, estallido de arena, marejada, nube de hongo, polvo de hadas, estela rúnica, grieta de sombras, río de lava, aura cristalina, pulso de fuerza, tormenta de polvo, lanza solar, destello de impacto, corte de viento, nube de ceniza, llama azul, mancha de esporas, grieta glaciar, portal celestial, pozo gravitatorio, armadura de circuito y armadura fundida; ver [guía de presets Kila](Photon2-Kila-Presets.md). La galería coloca categorías extensas en cuadrícula y configura flipbooks/distorsión como corrientes. El smoke de arranque valida texturas, presupuesto de samplers y round-trip de serialización de todos los presets; la galería de desarrollo comprueba sus variantes de shader por contexto. La búsqueda de módulos descartados por el presupuesto de samplers ahora usa lookup por identidad constante, conservando la lista ordenada para diagnóstico. El explorador reutiliza el material de la miniatura al seleccionar y la miniatura flotante del inspector tolera que su nodo ya se haya desmontado.
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
| `network/FxPackSync.java` | Lado servidor: escaneo de packs públicos, manifiesto, streaming por chunks, upload privado, cuotas y almacenamiento por UUID. |
| `client/fx/fxpack/LocalFxResourceUpload.java` | Prompt confirm/cancel antes de leer, empaquetado de todo `ldlib2/assets`, upload privado o exportación local. |
| `command/SendFxResourcesCommand.java` | `/photon fx send <player>` (requiere operador). |
| `command/ActiveEntityEffects.java`, `ActiveWorldEffects.java` | Snapshots/replay en memoria de FX loop para entidades y ubicaciones. |
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
| `gui/editor/view/scene/SceneView.java` | Vista de escena; barra superior con toggles (bloom, efectos, luces, máscara, shape, cull, colisión, vistas) y el **control de intensidad de bloom**. El toggle de bloom y la tecla `B` escriben `FXData.bloomEnabled` del FX abierto. |
| `gui/editor/view/scene/SceneToggleBuilder.java` | Constructor de toggles con icono/tooltip. |
| `gui/editor/view/scene/SceneBloomIntensityControl.java` | Slider `0.00`–`2.00` + lectura numérica que escribe `bloomIntensity` **del FX abierto** (`FXData`; propiedad por efecto, viaja al exportar). No toca la config del cliente. |
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
└── fxpacks/                    # .fxpack instalados/exportados (+ server-<hash>.fxpack del servidor)

<servidor>/photon/fxpacks/      # packs públicos ofrecidos a todos
<servidor>/photon/private-fxpacks/<UUID>/  # packs privados para un destinatario
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
| **"Sigue sin funcionar el cosito de elegir partículas"** (1.ª vez) | Los buscadores (`SearchComponentConfigurator`) copiaban los candidatos **una sola vez** al construir el configurador y nunca se refrescaban → lista vacía si el panel se abrió antes de la primera carga de recursos o después de una recarga | `FXHelper.searchFX(...)` lee la lista (cacheada) **en cada búsqueda**; lo usan sub-emisores, `FXInstanceStyle.pickFx` y `IdLists` |
| **"Lo de las partículas y la lista de fx siguen igual"** (2.ª vez) | La lista de candidatos estaba bien; el fallo era del **widget** de búsqueda de LDLib 2.2.39: al abrirse busca con el texto que el campo ya tiene, que es el nombre del valor **ya elegido**. El desplegable mostraba una sola línea (o ninguna la primera vez), y teclear **concatenaba** al nombre viejo (`nombreElegidoX`), así que no encontraba nada. LDLib lo arregló en 2.2.39.8, versión que no se puede usar aquí | `SearchComponentMixin` (nuevo): repite la búsqueda en vacío al abrir y selecciona el texto, para que la primera tecla reemplace el nombre en vez de sumarse. Arregla **todos** los selectores que usan ese widget de una sola vez. Candado: `[search-box-smoke]` |
| Slider de bloom que "volvía" a 0.18 | La migración de defaults antiguos corría en trabajo diferido y comparaba por valor: pasar el slider por **1.0** (un default viejo) devolvía el valor a 0.18 | La migración es ahora **una sola vez**, al arrancar, antes del trabajo diferido |
| Pedido: botón para cambiar intensidad de bloom | Solo existía en la pantalla de config | `SceneBloomIntensityControl` en la barra de escena: slider 0..2 + valor. **Actualización posterior:** la intensidad se rehízo como propiedad por FX (`FXData.bloomIntensity`, default 0.18) y el slider escribe esa; el `bloom_intensity` de la config quedó obsoleto y se quitó de la pantalla de config. El pase usa la intensidad más alta entre los FX con bloom activo; `enable_bloom`/`bloom_threshold`/`bloom_mip_level` siguen globales |

### 6.3 Pedido de Alex: explicar `.fxpack` y el flujo de servidor

Respondido con [`Photon2-FXPack-Guia-Servidor.md`](Photon2-FXPack-Guia-Servidor.md) (lenguaje llano) y resumido en la sección 8.

### 6.4 Conversación reciente: sincronización, consentimiento y continuidad

Decisiones y resultado, en el orden en que se acordaron:

1. Compartir con un jugador **todo** `<gameDir>/ldlib2/assets`, no solo los `.fxpack` ya creados ni las dependencias detectadas por FX. Se añadió el comando de operador `/photon fx send <jugador>`, entrega privada al jugador nombrado y almacenamiento por UUID en `<server>/photon/private-fxpacks/` para futuras conexiones. Los demás jugadores no reciben ese pack.
2. Admitir workspaces grandes: 512 MiB comprimidos y 2 GiB expandidos por pack, con cuotas públicas/privadas agregadas indicadas en la sección 7.
3. Permitir compartir fuera del juego: `/photon_client export_ldlib2_assets` crea `ldlib2-assets-<hash>.fxpack` en `<gameDir>/photon/fxpacks/`, para adjuntar/transferir por Discord u otro medio. El amigo lo instala en su propia carpeta `photon/fxpacks/`.
4. Persistencia de FX: conservar snapshots en memoria del servidor y replay tardío para FX con contenido loop, en entidades/jugadores y ubicaciones/bloques; reemitir al empezar a trackear entidades o mirar chunks. FX del jugador se vuelven a emitir al cambiar de dimensión/respawn. Los comandos de borrado existentes quitan también los snapshots para que no se recreen. **No es persistencia en disco**: reiniciar el servidor borra el estado.
5. Se pidió consentimiento explícito antes de inspeccionar/leer y subir archivos locales. La revisión inicial encontró que no había tal confirmación. Se añadió un mensaje cliente con botones confirm/cancel tanto al envío privado como a la exportación local; crear el ZIP no empieza hasta confirmar, y cancelar no lee ni sube archivos.
6. Se actualizaron README, guías de servidor (rápida, detallada y técnica) y este documento; los límites viejos de 64/128/256 MiB se reemplazaron. PR #9 integró funcionalidad y docs; PR #10 actualizó el contexto y el enlace al preview. Ambos están mergeados.
7. Solicitud actual de continuidad: documentar íntegramente este contexto y preparar un prompt general para revisar el repositorio, encontrar bugs/errores y luego mejorar texturas y materiales. El prompt reutilizable está en la sección 12.

**Verificación:** tres scripts locales (`verify_kila_preset_catalog.py`, `verify_kila_blend_ordinals.py`, `verify_shader_normal_safety.py`) y `git diff --check` pasaron. CI en GitHub compiló el JAR, inició servidor Forge dedicado y cliente headless con pruebas de humo; runs `44449444574` y `44450496049` terminaron exitosamente. En el sandbox no había Java local. Queda pendiente validación manual visual/multicliente.

### 6.5 Ronda de auditoría 2026-11-19 (rama `arena/2db16c95-photon-2`, base `36df1b3` = PR #11)

Auditoría de código (lógica, lifecycle, networking, permisos, límites/ZIP, serialización, UI, shaders, rendimiento, compatibilidad Forge) y de texturas/materiales. Hallazgos corregidos en esta rama:

| Hallazgo | Ubicación | Impacto | Corrección |
| --- | --- | --- | --- |
| El escaneo de packs privados re-descomprimía y re-validaba todos los packs del destinatario (hasta 2 GiB) en **cada** login/respawn/cambio de dimensión/refresco, sin usar la caché de expansiones ya validadas | `network/FxPackSync.scanPrivatePacks` | Rendimiento del servidor: trabajo de fondo repetido en el pool compartido; con packs grandes podía entorpecer otras tareas | Se reutiliza `VALIDATED_PACK_EXPANSIONS` (por hash de contenido) y se memoiza el hash por `tamaño+mtime`, de modo que packs sin cambios no se re-leen ni re-descomprimen |
| El presupuesto agregado de descompresión por manifiesto (8 GiB, documentado) se ignoraba entre packs públicos y privados: `scanPrivatePacks` recibía `expandedBudget` y no lo usaba | `network/FxPackSync.scanPrivatePacks` | Límite de seguridad/DoS: un manifiesto podía superar el trabajo de validación documentado | El presupuesto restante tras los packs públicos se aplica ahora a los privados; los packs que lo exceden se omiten con el mismo criterio que los públicos |
| Un archivo cuyo nombre contiene `\`, `:` o caracteres de control en `ldlib2/assets` producía un ZIP que la validación rechazaba entero, con un mensaje engañoso («no valid FX») | `client/fx/fxpack/LocalFxResourceUpload.createArchive` | Exportación y envío privado podían fallar en bloque por un solo archivo con nombre raro (posible en Linux) | El empaquetado omite esos archivos (aviso en el log con la ruta) y el error final indica cuántos se omitieron; el resto del pack viaja y valida |
| Archivos `.part` huérfanos de subidas interrumpidas por un crash del servidor quedaban en `photon/private-fxpacks/.incoming/` para siempre (hasta 512 MiB cada uno) | `network/FxPackSync` | Espacio en disco | Barrido al arrancar el servidor (nadie puede estar subiendo) y al pararlo; se registra cada borrado |
| `EntityEffectCommand.decode` reservaba 0 bytes de cola al validar la lista de entidades, pero el paquete lleva un byte más (`replay`) | `command/EntityEffectCommand` | Endurecimiento mínimo; la comprobación de límite era más laxa de lo previsto | `EntityIdListCodec.read(buf, 1)` |
| El lector NBT de 1.20.1 no limita la profundidad de anidado (solo bytes): un `.fx` de un pack poco fiable con miles de compuestos anidados desborda la pila al parsear/serializar y crashea el cliente; los `catch (Exception)` de los límites de carga no contenían el `StackOverflowError` | `client/fx/FXHelper.readTag`/`deserialize`, `client/fx/fxpack/FXPacks.gc` | Endurecimiento: un pack del servidor podía tirar el cliente de quien lo descarga (no ejecución de código) | `StackOverflowError` se contiene en los tres límites: la carga falla con un aviso de una línea (sin traza enorme) y el gc conserva todo en vez de barrer con marca incompleta |
| `MaterialResource.addVanillaTextureMaterial` (sin uso) construía IDs `minecraft:textures/particle/…` inválidos (sin namespace propio) | `gui/editor/resource/MaterialResource` | Código muerto con trampa latente | Eliminado |
| `zh_cn.json` le faltaba `photon.material.pick_texture_as_material` (usado por el selector de materiales) | `assets/photon/lang/zh_cn.json` | Texto sin traducir en la UI china | Clave añadida |

Auditoría de texturas/materiales (204 recursos versionados): los 35 PNG Kila + `ui/checker` cuadran con `KilaTextures.ALL`; los 33 iconos de `textures/gui/icon` tienen todos uso; `particle/*`, `icon.png` y los modelos OBJ/shaders referenciados existen; no hay IDs con mayúsculas ni namespaces inválidos; todos los PNG son 8-bit no entrelazados; el smoke `[kila-texture-assets-smoke]` valida siluetas de alpha (esquina transparente, núcleo opaco) y `[kila-flipbook-sheets-smoke]` valida pares hoja/motion-vector de flipbooks. Sin recursos huérfanos ni faltantes. Observaciones sin cambio (no hay forma de verificar el resultado visual en este sandbox): `particle/ring.png` es 1000×1000 (no potencia de dos, 104 KB) y `particle/kila_tail.png` es 2048×2048 (598 KB) — dimensiones válidas para GL3 pero atípicas; `thaumcraft.png` (256×112) usa `blur: true` en su `.mcmeta`, que el cargador aplica.

Idiomas: `en_us` 2099 claves · `zh_cn` 2099 · `es_es` **2099 — traducción completa** (los tres en paridad exacta de claves, con placeholders `%s`/`%d` verificados). El catálogo de presets Kila está completo en los tres idiomas (`verify_kila_preset_catalog.py`). La paridad se mantiene con `verify_lang_parity.py`, enganchado al workflow (paso «Verify language file parity» y el gate de rebuild de `verify_*.py`): exige el mismo juego de claves que `en_us` en cada idioma (ni faltantes ni huérfanas) y que cada placeholder de la cadena inglesa aparezca en la traducción. En esta misma tandas se borraron de `zh_cn` las 3 claves huérfanas sin uso (`photon.gui.editor.timeline.channel_mode{,.curve,.expression}`) y se añadieron las 729 claves que faltaban en `es_es` (nodos de shader graph, config de emisor, trails, colisiones, fuerzas, luces, niebla y settings de model source), con el estilo «tú» técnico del archivo y los términos shader/FX sin traducir.

**Trabajo posterior a la ronda (misma rama):** (a) pruebas de regresión para los arreglos de la ronda en `FXPackDependencyRegression` — `verifyEffectCommandCodecBounds` (la lista de ids de `EntityEffectCommand` ahora se acota a `readableBytes - 1` reservando el byte `replay`; un count mentiroso debe lanzar `DecoderException` en vez de leer fuera del buffer) y `verifyUnsafeEntryNameParity` (`LocalFxResourceUpload.isSafeEntryName`, hecho package-private, acepta `ns/fx/a.fx`, `nombre-único`, `..hidden` y rechaza `\`, `:`, `..`, `.`, vacío/espacio, `//`, control — exactamente lo que `FxPackSync.isValidFxPack` rechaza como entradas ZIP; paridad export↔validación). Ambas corren bajo el marker requerido `[fxpack-stream-validation-smoke]`. (b) Traducción completa de `es_es` + verificador de paridad de idiomas (arriba). (c) **Persistencia de materiales creados en el editor** (auditoría + corrección): los materiales que uno crea viven en la biblioteca de recursos (file provider global `<gameDir>/photon/resources/global/`, escrito al instante por `addResource` y refrescado por el tick del panel) y los slots de material de un fx solo guardan una **referencia por ruta** (`ui_resource_material` → `resourcePath`). Verificado: `.fxproj` y `.fx` guardan solo referencias; `.fxpack` **sí incrusta** los materiales (`assets/photon_fx/material/<hash>.material.nbt`, referencias reescritas a `file(...)`); la conversión de proyectos Photon 1 descarta la biblioteca embebida con aviso (limitación conocida) y desempaqueta las referencias `photon_fx` a `<project>_resources/`. **Corrección**: `FXProject` ahora incrusta una instantánea de cada material de usuario referenciado (siguiendo cadenas `UIResourceMaterial`, tope 16 saltos) en el `.fxproj` (`data.libraryMaterials`) y, al cargar, **restaura** los que ya no resuelvan (la biblioteca local siempre gana; si la ruta guardada no es escribible aquí — otro juego/máquina —, se recrea en la biblioteca global local y las referencias del fx se reapuntan antes de deserializar). `FileResourceProvider.addResource` escribe el archivo al instante (verificado en el bytecode de LDLib2), así que la creación persiste sin depender del tick del panel. `[material-persistence-smoke]` (`MaterialPersistenceRegression`, enganchada al editor smoke y al CI): crea un material → llega a disco y hace round-trip → un proyecto que lo referencia lo incrusta → borrada la copia de la biblioteca, la carga del proyecto lo restaura y el slot resuelve. (d) Auditoría de materiales existentes con evidencia (decode PNG programático): `ring.png` 1000×1000 NPOT — forma puramente en alfa sobre blanco (49% transparente, margen ~38 px), sano; `thaumcraft.png` 256×112 con `blur: true` y alfa disperso, sano; `kila_tail.png` 2048×2048 **opaca con máscara de intensidad en RGB** (fondo negro 68%, centro gris) — pensada para blending **aditivo**; con ALPHA dibujaría un rectángulo negro (nota para artistas, sin cambio de píxeles). Sin texturas rotas ni huérfanas; ningún píxel modificado.

**Verificación de esta ronda:** los cuatro scripts Python (`verify_kila_preset_catalog.py`, `verify_kila_blend_ordinals.py`, `verify_shader_normal_safety.py`, `verify_lang_parity.py`) y `git diff --check` pasaron en local; sin Java en el sandbox, GitHub Actions compiló el JAR y ejecutó el smoke completo (servidor dedicado + cliente headless, ~25 greps): runs [`44458049882`](https://github.com/Santi-PdR/Photon-2/actions/runs/44458049882) y [`44458368154`](https://github.com/Santi-PdR/Photon-2/actions/runs/44458368154) ✅ (PR #12). El commit de regresiones y la paridad de idiomas se cierran en los runs siguientes de la misma rama. Nada de esto sustituye la prueba multicliente manual, que sigue pendiente.

---

## 7. Cómo funciona `.fxpack` (resumen técnico)

1. **Contenedor**: ZIP con layout de resource pack: `assets/<ns>/fx/<nombre>.fx` + todo lo que referencia + `assets/photon_fx/` (materiales, graphs, mallas empaquetadas).
2. **Exportación** (editor → *File → Export FXPack…*): serializa el FX, camina el NBT, y arrastra texturas/modelos/shaders/audio; los recursos de librería se copian bajo `photon_fx` y las referencias se reescriben.
3. **Montaje local**: cada `<instancia>/photon/fxpacks/*.fxpack` (y cada carpeta descomprimida) se registra como pack **oculto, siempre activo, de prioridad más baja**. Un resource pack normal o el workspace del autor tienen prioridad.
4. **Detección**: requiere recarga de recursos (F3+T) para archivos nuevos; `FXPacks.rewrite` sirve ediciones de packs ya montados sin recargar.
5. **IDs**: `namespace:nombre` (namespace = nombre del `.fxpack`; ruta = nombre dado al exportar; ambos saneados a `[a-z0-9/._-]`).

### 7.1 Distribución pública y privada desde el servidor

```text
Público: <server>/photon/fxpacks/*.fxpack → manifiesto para todos → descarga por bloques
         → SHA-256 + validación ZIP → recarga automática
Privado: /photon fx send <jugador> → cliente del operador empaqueta ldlib2/assets
         → servidor guarda bajo private-fxpacks/<UUID>/ → solo ese jugador descarga;
           el pack se vuelve a ofrecer en futuras conexiones
Local:   /photon_client export_ldlib2_assets → diálogo de confirmación → .fxpack en
         <instancia>/photon/fxpacks/ → el autor lo puede adjuntar en Discord/transferir
```

El comando de envío requiere operador (nivel 2), y remitente y destinatario deben estar conectados al iniciarlo. El cliente del remitente muestra **Confirm upload / Cancel** antes de leer archivos; la exportación local también solicita confirmación. Solo al confirmar se lee y empaqueta el árbol; cancelar no lee ni sube archivos. Se incluyen todos los archivos regulares de `ldlib2/assets`, incluso los que ningún FX referencia (se omiten enlaces simbólicos). La validación requiere al menos un FX en `assets/<namespace>/fx/*.fx`.

Límites actuales (`FxPackSync`): **512 MiB comprimidos y 2 GiB descomprimidos por pack**; 32 packs públicos / 512 MiB público total; hasta 16 packs privados / 2 GiB por destinatario; manifiesto de hasta 3 GiB y presupuesto agregado de validación de 8 GiB; **20 000 entradas ZIP**. Rechaza rutas inseguras (`../`, barras invertidas y rutas absolutas). La entrega directa requiere que remitente y destinatario estén conectados para iniciarla y permiso de operador (nivel 2).

### 7.2 Replay tardío de FX en loop

El servidor conserva snapshots **en memoria** (`ActiveEntityEffects` y `ActiveWorldEffects`) de comandos de FX. Cuando un cliente vuelve a ver la entidad (`StartTracking`) o empieza a observar el chunk (`ChunkWatchEvent`), se reemiten los efectos; el cliente valida que el FX tenga contenido loop mediante `FXLooping.hasLoopingContent` antes del replay. Los FX del propio jugador se reemiten en cambio de dimensión y respawn; al logout se eliminan. Al quitar/descartar entidades también se limpia su estado. Esto no persiste a disco ni sobrevive a un reinicio del servidor.

Los comandos existentes `/photon fx remove ...` eliminan el FX activo y su snapshot asociado para que el replay no lo resucite. Los snapshots están acotados: hasta 4 096 entidades/posiciones y 64 efectos por entidad/posición (se descartan los más antiguos al exceder cuota). Para revisar: `command/ActiveEntityEffects.java`, `command/ActiveWorldEffects.java`, `PhotonCommonListeners.java`, `client/fx/FXLooping.java` y los comandos `Remove*EffectCommand`.

### 7.3 Límites de uso

- Los packs públicos añadidos mientras hay jugadores conectados requieren `/photon fxpacks` como operador o `/photon_client fxpacks` en el cliente. Login y reentrada al nivel también ofrecen el manifiesto.
- La carpeta local `ldlib2/assets` no se comparte sola: usa `/photon fx send <jugador>` para enviarla de forma privada, o `/photon_client export_ldlib2_assets` para crear un archivo que puedas compartir por Discord/otro servicio.
- Sin Photon en el servidor no hay sincronización; sin Photon en el cliente no hay render. El `.fxpack` transporta recursos, no código.

---

## 8. Verificación (CI)

Workflow: `.github/workflows/forge-1.20.1.yml` (Java 17, Gradle, Ubuntu).

Pasos: build → verificación de mixins/refmap en el JAR → subida del distributable → arranque de **servidor dedicado** → preparación de mundo + fixtures (`ci-fixtures/real-{scan,shield-trail,tornado}`) + pack de prueba de servidor → **cliente headless** con Xvfb y GL software → ~25 verificaciones por `grep` en el log.

Antes del build corren **8 verificadores estáticos** en `.github/scripts/` (cada uno un paso «Verify …» que falla el job): `verify_kila_preset_catalog.py` (presets Kila registrados y traducidos), `verify_kila_blend_ordinals.py` (orden de blends Java ↔ shader), `verify_shader_normal_safety.py` (normales/proyección en 65 fuentes GLSL), `verify_lang_parity.py` (mismas claves en `en_us`/`es_es`/`zh_cn`), `verify_lang_key_usage.py` (todo literal `photon.*` y toda `@Configurable(name/tips)` resuelve), `verify_lang_placeholders.py` (claves con `%s` reciben argumentos), `verify_config_screen_coverage.py` (todo ajuste de `PhotonConfig` tiene fila, etiqueta y tooltip) y `verify_locale_safe_formatting.py` (ningún `%f` sin `Locale` explícito fuera de `uitest/`). Se ejecutan en local con `python3 .github/scripts/<nombre>.py` sin dependencias.

Tras los smokes, el paso «Summarize Photon warnings from the smoke logs» (`if: always()`, nunca falla) imprime y publica como anotaciones `notice` las líneas `WARN`/`ERROR` distintas de Photon/LDLib/Kila de `photon-client.log` y `photon-server.log` (números normalizados, authlib excluido, máx. 40 por log). Es la forma de auditar avisos del juego sin el archivo de logs: `gh api repos/Santi-PdR/Photon-2/check-runs/<job id>/annotations --jq '.[] | select(.title | startswith("photon ")) | .message'`.

Marcas de humo relevantes (deben aparecer en el log del cliente):

```
[bloom-smoke] red FX halo is present while non-highlight scene pixels stay intact
[fx-picker-smoke] the fx search lists every loadable effect
[bloom-control-smoke] dragging the scene bar's bloom slider writes this FX's bloomIntensity=…
[animated-property-load-smoke] unreadable animated properties are skipped without failing the track
[timeline-insert-time-smoke] insert time only accepts a finite positive gap and its undo is exact
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
| **FX en loop + envío privado/exportación LDLib2** | `520bd2f` | run `44446628854` ✅ | preview inicial [`preview-44446628854-1`](https://github.com/Santi-PdR/Photon-2/releases/tag/preview-44446628854-1) |
| **Consentimiento previo + documentación** | `0d107e4` | PR #9 mergeado (`e774dec`); run `44449444574` ✅ | [`preview-44449444574-1`](https://github.com/Santi-PdR/Photon-2/releases/tag/preview-44449444574-1) |
| **Handoff completo y enlace al preview** | merge `1064853` | PR #10 mergeado; runs `44450489962` y `44450496049` ✅ | [`preview-44450489962-1`](https://github.com/Santi-PdR/Photon-2/releases/tag/preview-44450489962-1) |

- Último preview directo: [descargar el JAR](https://github.com/Santi-PdR/Photon-2/releases/download/preview-44450489962-1/photon-forge-1.20.1-26.2.2.9-forge.1.20.1-all.jar) · [release](https://github.com/Santi-PdR/Photon-2/releases/tag/preview-44450489962-1) · [CI](https://github.com/Santi-PdR/Photon-2/actions/runs/44450489962).
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

### 10.1 Estado integrado y rama al documentar esta sesión

PR #9 integró sincronización FX, envío/exportación LDLib2, consentimiento y guías (`e774decd28c666e09d1a05b5b6ea6590483e9b73`). PR #10 integró la actualización del handoff y el enlace al JAR (`10648539c62b2c354ca88c8939efcd4d4a9e8f99`). Ambos están **MERGED** a `main`, con checks exitosos.

| Rama | Estado |
| --- | --- |
| `main` | Incluye PR #9, #10 y #11; base actual `36df1b31`. |
| `arena/2db16c95-photon-2` | Rama Arena activa de esta sesión (auditoría), branched desde `36df1b3117ab83ec087857c1814a9f7a65bdfa80`; usarla para este trabajo, sin cambiar de rama. |
| `arena/cd0a1dc2-photon-2` | Rama de la sesión anterior que produjo PR #11; ya integrada. |
| `arena/747b04a3-photon-2` | Rama de la sesión que produjo PR #9/#10; ya integrada. |

### 10.2 Historial de limpieza de ramas

La tabla siguiente es el registro de la limpieza anterior al port y no describe el estado actual:

| Rama | Por qué se pudo borrar |
| --- | --- |
| `port/forge-1.20.1-2.2.9` | Ancestro directo de `main` tras el merge del PR #1. |
| `fix/photon-owned-workspace` | 0 commits propios; contenido ya integrado. |
| `backup/port-forge-before-alex-captures-24d4306` | 0 commits propios; snapshot previo a las capturas de Alex. |
| `fix/alex-four-captures-2026-11-06` | 27 commits, todos integrados vía PR #3 (squash). Sus diferencias son solo el estado **anterior** del código que la ronda 2 ya reemplazó. |
| `port/forge-1.20.1-sync-sound` | 4 commits, integrados vía PR #2 (squash). |
| `backup/photon-forge-1.20.1-run155` | Solo notas de JARs validados → **rescatadas en la sección 9**. |
| `backup/photon-forge-run177` | Solo notas de JARs validados + workflows temporales (`backup-run177.yml`, `verify-candidate-run205.yml`). |

### 10.3 Política recomendada de aquí en adelante

- Todo entra por **PR a `main`**; ramas de trabajo cortas (`fix/...`, `feat/...`) borradas al mergear.
- Para hitos estables usar **tags** (ya existe `v26.2.2.9-forge.1.20.1`), no ramas `backup/*`.
- Los JARs se publican como **release adjunta al tag**; los artifacts de Actions caducan a los 90 días.
- El contexto se mantiene en **este documento**, no repartido en ramas.

## 11. Pendientes, riesgos conocidos y plan inmediato

### Pruebas funcionales manuales pendientes

1. Con dos clientes, probar `/photon fx send <jugador>`: confirmar que se empaqueta **todo** `ldlib2/assets`, que solo el destinatario recibe el pack, que el cliente recarga y que el pack reaparece tras reconectar. Probar cancelar antes de confirmar y verificar que no se lea/suba ningún archivo.
2. Exportar `/photon_client export_ldlib2_assets`; cancelar y confirmar que no hay lectura. Después confirmar exportación, verificar el `.fxpack`, transferirlo por archivo e instalarlo en una instancia limpia.
3. Probar loop FX en entidad/jugador y ubicación/bloque: replay al reentrar al rango/cargar chunk, cambio de dimensión del jugador, respawn, comandos de borrado y que un FX no-loop no se reproduzca tardíamente. Reiniciar servidor para confirmar que snapshots son solo de memoria.
4. Revisar selector de FX/materiales y bloom en cliente de Alex si sigue disponible; pedir log/captura de la sesión si hay fallo. Probar shaderpacks/Iris/DH en AMD cuando haya hardware representativo.

### Auditoría y mejoras solicitadas

**Hecho en la ronda 2026-11-19** (detalle en la sección 6.5): recorrido de código, recursos, shaders y red; 8 hallazgos corregidos con cambios acotados (rendimiento del escaneo de packs privados, presupuesto agregado de descompresión, nombres de archivo inseguros en la exportación LDLib2, temporales huérfanos, endurecimiento de decodificación de paquetes y de profundidad de NBT, código muerto y una clave de idioma); auditoría de texturas/materiales sin hallazgos que corregir (todo referenciado, sin huérfanos, formatos válidos). Los verificadores Python y `git diff --check` pasan en local; GitHub Actions compiló y pasó el smoke completo (servidor dedicado + cliente headless) en los runs [`44458049882`](https://github.com/Santi-PdR/Photon-2/actions/runs/44458049882) y [`44458368154`](https://github.com/Santi-PdR/Photon-2/actions/runs/44458368154) ✅ (PR #12; no hay Java en el sandbox).

Pendientes reales tras esa ronda (actualizado con el trabajo posterior, ver §6.5):

1. **Pruebas manuales multicliente** (siguen sin hacerse; ver arriba) — no presentar como verificado el envío privado, la exportación ni el replay hasta probarlos.
2. ~~**Completar `es_es.json`**~~ **HECHO**: los tres idiomas quedan en paridad exacta (2099 claves tras las limpiezas y altas posteriores; ver punto 12) y `verify_lang_parity.py` la mantiene en CI. Las 3 claves huérfanas de `zh_cn` (`photon.gui.editor.timeline.channel_mode*`) también se borraron.
3. **Verificación visual en juego** de las observaciones de assets (§6.5): `particle/ring.png` 1000×1000, `particle/kila_tail.png` 2048×2048, `thaumcraft.png` con `blur: true`. No cambiar sin comparar en pantalla.
4. ~~Limpieza menor pendiente: claves `photon.configuration.bloom_intensity*`~~ **HECHO (esta tanda, con la corrección de fondo)**: la auditoría confirmó que nada leía esas claves — y que la opción `bloom_intensity` del config era **write-only de punta a punta**: `PhotonConfigScreen` nunca tuvo su fila (mostraba `bloom_threshold` y saltaba a `enable_bloom_with_iris_shader`), el único lector era su propia migración de valores antiguos y un bloque comentado de un combine pass antiguo. El diseño vigente está documentado en el propio tooltip de `enable_bloom` («Intensity is authored per FX, not here») y en el javadoc de `FXBloom` («Intensity lives on the FX, not on the client config»). Se retiró el vestigio entero: entry `bloom_intensity` de `PhotonConfig`, rama de `migrateOldBloomDefaults` que lo reescribía, y las 6 claves de idioma `photon.configuration.bloom_intensity{,.tooltip}` (2096 claves por idioma, paridad verificada). Un jugador que edite `bloom_intensity` en `photon-client.toml` ya no encontrará la clave muerta; la intensidad del bloom se sigue autoreando por FX en el editor. **Regresión**: `.github/scripts/verify_config_screen_coverage.py` (en CI) exige que todo `PhotonConfig` de nivel superior tenga fila en la pantalla y que cada fila tenga etiqueta + tooltip en los tres idiomas, y acepta como excepción únicamente `migratedOldBloomDefaults` (guardián de migración de un solo disparo) y las secciones anidadas que renderiza la pantalla de secciones de LDLib2 (`dynamic_lights`, `assets`, `collision`).
5. ~~(b) de la ronda~~ **HECHO**: watchdog de transferencias en `ClientFxPackSync` (abajo). Spots sin re-auditar: ~~`EffectSectionCommand`~~ **auditado y limpio** (decode acotado con `MAX_ENTITY_TARGETS` = `EntityIdListCodec.MAX_ENTITY_IDS`, iteración defensiva por `List.copyOf`, registrado bajo `/photon fx` nivel 2) y ~~`FXProjectConverter`~~ (auditado en la tanda de materiales: `Unpacker` correcto y con guarda de ciclos; la biblioteca embebida de Photon 1 se descarta con aviso, limitación conocida). Huecos de smoke: ~~replay tardío de FX en loop y el flujo de subida `LocalFxResourceUpload`~~ **cubiertos en esta tanda** (abajo); queda solo la parte manual de la subida (confirmación por chat + red).
6. Nota de materiales (sin cambio de píxeles): `kila_tail.png` es una máscara de intensidad aditiva (fondo negro, sin alfa) — usarla con blend ALPHA dibuja un rectángulo negro; documentar en las guías de artistas si se publican. La verificación **visual** en juego de `ring.png`/`kila_tail.png`/`thaumcraft.png` sigue pendiente.
7. Limpieza de idiomas resuelta: `photon.configuration.bloom_intensity_in_scene{,.tooltip}` eliminadas de los tres idiomas (huérfanas confirmadas: solo estaban en los lang; el slider de escena usa lectura numérica sin etiqueta). Las claves `photon.configuration.bloom_intensity{,.tooltip}` se retiraron **más tarde** en la tanda del conversor de audio: la premisa de mantenerlas («la pantalla de config sigue definiendo la opción») resultó falsa — la pantalla nunca tuvo esa fila y la opción era write-only; ver el punto 4 corregido. Además el ruido `Expected BEGIN_OBJECT but was STRING` de los logs de CI quedó **identificado y cerrado**: es authlib — el runner no alcanza los servidores de sesión de Mojang, responde una página HTML y Gson revienta al parsearla (`AuthenticationUnavailableException` → `MinecraftClientException: Failed to read value <!DOCTYPE html…>` → `JsonSyntaxException`); ruido externo, documentado en el grupo «noise filter» del workflow y filtrado de los greps de diagnóstico para que no oculte fallos reales.
8. **Watchdog de sincronización de packs** (`ClientFxPackSync`, commit de esta tanda): las transferencias de chunks son petición/respuesta; si el servidor deja de enviar (conexión medio abierta, caída tras el manifiesto), la sesión quedaba pendiente para siempre y sus packs nunca se activaban. Ahora un watchdog (daemon, cada 30 s) barre transferencias sin actividad de chunks **y sin trabajo de IO que lo explique** durante 120 s (la llegada de un chunk en el hilo de red cuenta como actividad; mientras el hilo IO trabaja — hashing/validación — no se barre nada), las retira por el mismo camino que un chunk corrupto (borra el `.part`, log de aviso, `finishOne`) y la sesión termina con lo descargado. Regresión `[fxpack-stall-watchdog-smoke]` (`FxPackStallRegression`, obligatoria en CI): una transferencia caducada se barre y su temp desaparece, la viva sobrevive, y con el hilo IO ocupado no se barre ninguna.
9. **Cobertura de los dos huecos de smoke restantes**: (a) `[upload-archive-smoke]` (`LocalFxResourceUploadRegression`) ejercita el flujo de subida local de LDLib2 end-to-end en el lado archivo: `createArchive` (hecho package-private) lleva el árbol `ldlib2/assets` a `assets/<relativo>` con `pack.mcmeta`, **omite** nombres de archivo inseguros (aquí `bad:name.png`, con salto elegante en sistemas de archivos que los rechazan) y el resultado pasa la misma `FxPackSync.isValidFxPack` que aplica el servidor; la mitad manual (confirmación por chat, red) queda sin automatizar. (b) `[late-replay-smoke]` (`LateReplayRegression`) cubre el núcleo del replay tardío: los snapshots en memoria de `ActiveEntityEffects`/`ActiveWorldEffects` reenvían sus campos con el flag `replay`, los no-multi se reemplazan en vez de apilarse, el tope de 64 por entidad/posición expulsa los más viejos, `forget` vacía el replay, y el filtro por chunk/dimensión del lado de bloques es exacto (refactor mínimo: `pendingReplay(...)` separa la recolección de paquetes del envío por red).
10. **Conversor automático de audio → Ogg Vorbis** (petición del usuario): en las pistas de audio del timeline ahora se importa **cualquier** archivo de sonido — `.mp3`, `.wav`/`.aiff`, el audio de un `.mp4`/`.m4a` (pista AAC), `.aac` (ADTS) o `.ogg` — y se convierte automáticamente a Ogg Vorbis (lo único que Minecraft reproduce) al registrarlo en `photon/assets`. Implementación **sin ffmpeg ni nativos**: JLayer (mp3, JitPack `com.github.umjammer:jlayer:1.0.6`) y JAAD (`io.vacco.jaad:jaad`, Maven Central, dominio público — demux MP4 + AAC) decodifican a PCM 16-bit LE; jVorbisEnc (`com.github.umjammer:jVorbisEnc:0.0.2`, JitPack — puerto puro de libvorbis, licencia BSD-Xiph) codifica Vorbis (VBR q0.4, mono/estéreo, cualquier frecuencia 8–200 kHz; entradas con más de 2 canales se reducen a estéreo). Los tres van embebidos con **JarJar** (junto a ldlib2/kilagraph). La decodificación es **en streaming** (un frame a la vez) y el `.ogg` de trabajo es un temporal que se borra tras registrar. Interfaz: `AudioConverter` + `OggVorbisWriter` en `client/fx/timeline/`; `AudioAssetImporter.importSound` (corrige además el nombre base para extensiones de ≠4 letras); el selector de archivos acepta todas las extensiones (`Dialog.suffixFilter` varargs) y los fallos muestran el motivo en el chat (`photon.gui.editor.timeline.audio.import_failed`). Regresión `[audio-conversion-smoke]` (`AudioConversionRegression`): wav sintetizado → ogg verificado con **STBVorbis (el decoder de Ogg de Minecraft)** longitud/energía incluidas, registro en `sounds.json` + reproducción del archivo de workspace, fixtures ffmpeg de CI (mp3, m4a, **mp4 con vídeo** — «usar el sonido de un mp4» — y wav 48 kHz para cubrir el setup Vorbis de frecuencias altas) y mensaje de error para formatos no soportados. Los fixtures se generan en CI con ffmpeg antes del cliente (`run/photon/smoke-fixtures/`); en local se omiten con nota. *Bug upstream conocido (jVorbisEnc 0.0.2)*: `Psych44._vp_tonemask_adj_longblock` trae dos entradas (6 y 7) con 16 valores en vez de 17 y `initVBR` revienta con `ArrayIndexOutOfBoundsException` para cualquier calidad VBR en [0.4, 0.8) de los templates 44/32/X; `OggVorbisWriter` lo mitiga con calidad por defecto 0.3 (ventana segura) y un orden de intentos {solicitada, 0.3999, 0.8, 0.1} que reconstruye el encoder antes de escribir un solo byte (nunca queda un Ogg a medias).

11. **Ronda de auditoría post-audio (esta tanda, `9641b09`/`c9292c9`/`a6c667c`)** — tres hallazgos cerrados y dos verificadores nuevos en CI:
    - **Ruido Gson identificado y cerrado**: «Expected BEGIN_OBJECT but was STRING» ×2 en los logs de CI era **authlib** — el runner no alcanza los servidores de sesión de Mojang, la respuesta es una página HTML (`<!DOCTYPE html…`) y Gson revienta al parsearla (`AuthenticationUnavailableException` → `MinecraftClientException: Failed to read value …` → `JsonSyntaxException`). Ruido externo, no bug del mod: documentado en el grupo «noise filter» del workflow y filtrado de los greps de diagnóstico (`smoke assertion`, `client`, `client exception trace`) para que no ocupe su presupuesto y tape fallos reales. Igual se filtró el ruido netty `Reflective setAccessible` del sweep de trazas.
    - **`bloom_intensity` retirado por completo** (ver punto 4 corregido): era una opción write-only — sin fila en la pantalla, solo la migración la leía, el pass de bloom usa la intensidad por FX de `FXBloom`. Entry + rama de migración + 6 claves de idioma + los usos muertos del smoke de bloom, todo fuera. Verificador nuevo `verify_config_screen_coverage.py`.
    - **`photon.gui.editor.timeline.edit_signal` traducida**: era la única de las 28 etiquetas de historial (`pushEdit`/`pushApplied`) sin clave de idioma (27/28 traducidas) — al editar una señal en el timeline, el historial de edición enseñaba la clave cruda. Añadida en los tres idiomas junto a `add_signal`. Verificador nuevo `verify_lang_key_usage.py`: falla si cualquier literal `photon.*` referenciado no existe en los lang (excepciones revisadas: las dos claves de estado de tamaño de diálogo y los prefijos dinámicos).
12. **Segunda ronda de auditoría estática (`76ba109`/`c95a5a6`/`10a59a0`)** — tres bugs reproducibles cerrados, cada uno con su regresión en CI:
    - **Historial «Add %s» / «Remove %s»**: las etiquetas de deshacer de añadir/quitar una propiedad animada en el timeline llevan hueco (`photon.gui.editor.timeline.add_property` = «Add %s»), pero `TimelineContext.pushEdit(String, …)` traducía la clave **sin argumentos**; en 1.20.1 `TranslatableContents` cae al texto crudo cuando falta un argumento, así que el panel de historial enseñaba literalmente «Add %s». Ahora `TimelineContext` expone `pushEdit`/`pushApplied` con `Component` (lo que implementa `FXTimelineView`) y los atajos con `String` quedan como `default` para claves sin hueco (las otras 27 etiquetas no cambian); `AnimationTrackEditor` construye la etiqueta con el nombre visible de la propiedad (`propertyKey(type)`, la misma clave del menú «+ Add Property»), de modo que el historial dice «Add Size». **Regresión** `.github/scripts/verify_lang_placeholders.py` (paso «Verify placeholder translation keys get their arguments»): todo uso Java de una clave con `%s` debe estar dentro de `Component.translatable`/`I18n.get`/`translatableWithFallback` con argumentos suficientes (falla si se pasa a `pushEdit`, `tooltips`, hojas de menú o se guarda como String), y `es_es`/`zh_cn` deben conservar el mismo número de huecos que `en_us` (42 claves con hueco, 46 usos). Cazó las dos líneas pre-fix.
    - **Filas «curves» / «gradients» crudas** en el material **Custom Shader** (grupos «Sampler Curve» / «Sampler Gradient» del inspector): `CurveTexture`/`GradientTexture` usaban `@Configurable(name = "curves")` sin clave de idioma, así que LDLib2 pintaba la clave en los tres idiomas (también en el diálogo de overrides de instancias). Renombradas a `CurveTexture.curves` / `GradientTexture.gradients` (convención `Clase.campo`) con traducción en/es/zh (`Curves`/`Curvas`/`曲线`, `Gradients`/`Degradados`/`渐变`; el `name` solo se usa para mostrar — la persistencia es `ListTag` propia). **Regresión**: `verify_lang_key_usage.py` comprueba ahora además las 721 claves literales de `@Configurable(name/tips)` contra `en_us.json`.
    - **Un `valueType` desconocido tumbaba la carga de todo el efecto**: `Timeline.readTrack` y `AnimatedProperty.deserialize` saltan con aviso lo que no reconocen (tipo de pista / tipo de propiedad no registrados), pero `ConfigPropertyTypeDispatcher.deserialize` hacía `ConfigValueType.valueOf(...)` a pelo: un `.fx` de una build más nueva o editado a mano lanzaba `IllegalArgumentException` a través de `Track.readData` y fallaba el efecto entero. Ahora devuelve `null` (avisado) y `AnimationTrack.readExtra` descarta esa única propiedad. **Regresión** `AnimatedPropertyLoadRegression` en el smoke de cliente (marcador `[animated-property-load-smoke]`): una pista con un `valueType` desconocido, un tipo de propiedad no registrado y una propiedad legible carga con exactamente esa última.
    - Cruces que salieron **limpios** (no repetir): uniforms puestos desde Java vs declarados en GLSL (los `kg_*` son built-ins de KilaGraph; `IViewRotMat`/`TextureMat`/`GlintAlpha` son opcionales vía `safeGetUniform`); claves NBT escritas por los mappers de Photon 1 vs campos persistidos (`@Persisted`/`@Configurable`) del destino; `Enum.valueOf` desde NBT (los otros tres sitios ya estaban protegidos); `parseInt/parseFloat` de producción (validados o envueltos). El escaneo inverso «clave lang → código» da ~635 falsos positivos (claves resueltas por anotaciones y nodos KilaGraph) y no sirve como detector. Observación sin acción: la familia `photon.gui.editor.fx.particle_panel.*` (4 claves) no tiene consumidor en el código desde que entró en el PR #11 (huérfana de nacimiento, inocua).

13. **Tercera ronda de auditoría estática (`3f29d4a`, run `44650140269` ✅) — texto numérico dependiente del locale**: `"%.2f".formatted(x)` / `String.format("%.2f", x)` usan `Locale.getDefault()`, así que en un Windows en español, alemán o francés escriben **`1,50`**. Dos sitios re-parseaban ese texto con `Double.parseDouble`/`Float.parseFloat` (que solo aceptan punto): (a) el **cuadro de tiempo del timeline** (`FXTimelineView.createTimeField`) reflejaba el playhead como `1,50`, su validador `\d*\.?\d*` lo marcaba en error y el salto de playhead al escribir un tiempo no hacía nada, en silencio; (b) el **editor de rango min/max de curvas** (`AnimationTrackEditor.openRangeEditor`/`applyRangeEdit`) pre-rellenaba `1,500` y rechazaba cualquier edición que conservara la coma. El repo ya fijaba `Locale.ROOT` en `PhotonConfigScreen`, `SceneBloomIntensityControl` y `LocalFxResourceUpload`, así que se extendió esa convención: los **15 formateos `%f` visibles por el jugador** (cuadro de tiempo, editor de rango, coordenadas del gráfico de curvas, lecturas de info del FX, feedback de `/photonlight`) pasan a `String.format(Locale.ROOT, …)` y ambos campos aceptan además la coma tecleada (`replace(',', '.')`; el validador del cuadro de tiempo pasa a `\d*[.,]?\d*`). **Regresión** `.github/scripts/verify_locale_safe_formatting.py` (paso «Verify floating-point formatting pins an explicit Locale»): falla ante cualquier `%f`/`%e`/`%g` en `.formatted(` o `String.format(` sin `Locale` explícito, también cuando `.formatted(` va en la línea siguiente al literal (`LightCommands` tenía dos así que un grep de una línea no veía); `%d`/`%s` no se comprueban. Alcance: todo `src/main/java` **salvo `uitest/`** (≈55 formateos en escenarios de diagnóstico que solo corren en el runner de CI con locale fijo y cuyo texto nunca se re-parsea) — si algún día un escenario produce texto que se lea de vuelta, quitar la exclusión y convertirlo. Comprobado además que ningún sitio usa `DecimalFormat`/`NumberFormat.getInstance()` (que tendrían el mismo problema).

14. **Etiquetas crudas por `@Configurable` sin `name`** (misma tanda, commit siguiente a `3f29d4a`): LDLib2 (`ConfiguratorParser`) usa **el nombre del campo Java** como clave de etiqueta cuando la anotación no trae `name = …`, y `TextElement.setText(String)` lo pasa por `Component.translatable`, así que sin clave de idioma el inspector enseña el identificador tal cual. Doce filas estaban así: `cull`, `depthTest`, `depthMask` (grupo Material del renderer), `remapCurve` (Noise › Remap), `cullBox` (Renderer › Cull), `bits` (Texture Material › Pixel Art) y `x`/`y`/`z`/`speedX`/`speedY`/`speedZ` (forma Function). Todas llevan ahora `name = "<Clase>.<campo>"` (`MaterialSetting.depthTest`, `RendererSetting.cull.cullBox`, `TextureMaterial.pixelArt.bits`, `Function.speedX`, …) con claves en los tres idiomas (2111 por idioma, paridad ✅), reutilizando la terminología ya existente («Depth Test»/«Depth Write», «Caja de culling», 剔除/深度写入). `Configurable.name()` es solo la etiqueta — la clave NBT sale de `key()`/nombre del campo — así que los `.fx` guardados no cambian. Efecto colateral bueno: el diálogo de overrides de instancias (`FXInstanceOverridesDialog`) construye la miga de pan solo con campos que tienen `name`, así que esos overrides ahora enseñan su hoja («… › Depth Test») en vez de cortarse. **Regresión**: `verify_lang_key_usage.py` comprueba también los `@Configurable` **sin** `name` (la clave efectiva = nombre del campo debe existir en `en_us.json`; `showName = false` exento; las menciones en comentarios/javadoc se ignoran) — cazó exactamente las 12 filas pre-fix. Commit `0259546`, run `44651680224` ✅.
15. **Cuarta ronda de auditoría estática: cerrada sin hallazgos reproducibles** (commit siguiente a `0259546`). Barridos limpios: referencias a assets (0 faltantes; `models/missing.*` son centinelas intencionados), argumentos Brigadier de los comandos, encode/decode de los 13 paquetes de `PhotonNetworking`, simetría NBT put/get (las asimetrías son lectores legacy), `PhotonConfig.define*`, `MemoryUtil` alloc/free, cierre de `NativeImage`/`DynamicTexture` en `LooseTextures.uploadLoose`, placeholders `%`/códigos `§`/saltos de línea coherentes entre los tres idiomas, `photon.mixins.json` ↔ clases, parejas undo/redo del timeline (`pushApplied`/`pushEdit`, 72 sitios), 12 keymaps ↔ lang, normalización `\\`→`/` en rutas relativas (`FXPacks.listFxEntries` recorre un `ZipFileSystem`, separador `/`), `new ResourceLocation(v)` en `SubEmittersSetting` (LDLib2 `SearchComponent` solo notifica al **clicar** un candidato, nunca texto libre) y `AudioAssetImporter.baseName` (sanitiza a `[a-z0-9/._-]`). Retoque menor: `AnimationTrackEditor.applyRangeEdit` rechaza `NaN`/`Infinity` tecleados en el rango de una lane (`Float.parseFloat` los acepta y `valueToCurveY` propagaría NaN a todos los keyframes).
16. **«Insertar tiempo» aceptaba cualquier número y su deshacer no era exacto** (misma tanda). El diálogo del menú del ruler (`FXTimelineView.openInsertTimeDialog`) validaba solo con `Double.parseDouble`, así que `-1`, `NaN`, `Infinity` o `1e999` pasaban; y el deshacer de un insert es «la misma ondulación con `-delta`» (`insertTime(atTick, -delta)`), que únicamente es la inversa exacta para un delta **positivo** (todo lo que está en o después del punto se mueve a la derecha y vuelve igual). Con un delta negativo, clips, keyframes, marcadores y señales cruzaban hacia atrás el punto de inserción y la inversa ya no los alcanzaba (un clip que empezaba en 12 con inserción en 10 y `-5` quedaba en 7; al deshacer, en vez de volver a 12 **crecía** 5 ticks por «solapar» el punto); con NaN/∞ todos los tiempos posteriores quedaban envenenados sin vuelta atrás (y se guardaban así en el `.fxproj`). Además, `40,5` se rechazaba en «Mover sección» e «Insertar tiempo» aunque el resto del editor ya tolera la coma desde `3f29d4a`. Fix: `insertTicksFromDialog` (solo segundos finitos, > 0 y ≤ 3600 = `MAX_INSERT_SECONDS`, coma aceptada) alimenta validador y aplicación del diálogo; `parseDecimal` compartido con «Mover sección» (que ya exigía finito y dentro de la duración); y `Timeline.insertTime` ignora punto o delta no finitos. Los deltas negativos siguen permitidos en el modelo porque son la inversa que usa el deshacer. **Regresión** `TimelineInsertTimeRegression` (`[timeline-insert-time-smoke]`, gate duro): parser del diálogo (acepta `1,5`, rechaza `-1`/`0`/`NaN`/`Infinity`/`1e999`/`abc`/`3601`), insert +20 en 10 sobre clips (antes/en/cruzando el punto), señales, marcadores, secciones y keyframes → estado esperado y `insertTime(10, -20)` devuelve exactamente el inicial; inserts no finitos no cambian nada. **Ayuda de auditoría nueva en CI** (no bloquea): el paso «Summarize Photon warnings from the smoke logs» publica como anotación `notice` del check-run las líneas `WARN`/`ERROR` distintas que mencionan Photon/LDLib/Kila en los logs del cliente y del servidor (prefijo de log quitado, números normalizados a `N`, ruido authlib excluido) — así una auditoría posterior lee los avisos reales vía `gh api .../check-runs/<id>/annotations` aunque la descarga del archivo de logs falle (ver §8).

La sección 12 contiene el prompt general reutilizable para la siguiente ronda.

---

## 12. Prompt general para continuar el proyecto

```text
Eres un agente de desarrollo trabajando en el repositorio Santi-PdR/Photon-2, un port de Photon 2 a Minecraft 1.20.1 / Forge 47.x / Java 17. Antes de tocar nada, lee completo `docs/Photon2-Contexto-Completo.md`, las guías enlazadas y las instrucciones de la sesión Arena. Inspecciona `git status`, rama actual, historial reciente, PR/CI y workflows; nunca cambies de la rama que Arena indique.

Contexto funcional que no debes perder:
- PR #9 y PR #10 están mergeados en main. El repo ya incluye envío privado de todo `<gameDir>/ldlib2/assets` con `/photon fx send <jugador>`, exportación con `/photon_client export_ldlib2_assets`, consentimiento explícito confirm/cancel antes de leer archivos, cuotas grandes (512 MiB comprimidos / 2 GiB expandidos por pack), y snapshots/replay en memoria de FX en loop para entidades/jugadores y bloques/ubicaciones. Los snapshots se eliminan con los comandos existentes; los FX del jugador se reemiten al cambiar de dimensión. No afirmes que esto está probado manualmente: la prueba multicliente real sigue pendiente.
- El JAR CI está enlazado en la sección 9. Python checks y GitHub Actions previos pasaron; usa CI para compilar si el sandbox no tiene Java.
- Mantén Forge 1.20.1, Java 17 y compatibilidad con LDLib2/KilaGraph del repo. No cambies versiones/base upstream sin necesidad demostrada.

Tu misión ahora es completar una revisión integral y luego trabajar, no limitarte a entregar una lista:
1. Recorre el código, recursos, shaders, pruebas y documentación por áreas. Busca bugs y fallos reales: null/errores de estado, lifecycle/login/logout/dimensión/chunk, replay/removal, permisos, networking/concurrencia, límites/Zip Slip/rutas, recursos ausentes, regresiones de UI, memoria/rendimiento, serialización y compatibilidad Forge. Para cada hallazgo, identifica archivo/línea, impacto y forma de reproducirlo; distingue hechos de sospechas.
2. Después del primer inventario, corrige primero los problemas de mayor impacto con cambios pequeños y enfocados; agrega/ajusta regresiones de test/smoke. No te detengas en el informe: continúa con las correcciones seguras y verificables.
3. Audita texturas y materiales en conjunto con sus usos. Comprueba referencias e IDs, namespace/casing, archivos faltantes o huérfanos, dimensiones/formato/color/alpha, animaciones/UV, filtros/atlases, shader inputs y uniform bindings, presets/serialización y aspecto visual en el editor/juego. Localiza estilos o materiales inconsistentes y propone/mejora assets con intención, comprobando el resultado real; no inventes referencias ni sustituyas muchas texturas a ciegas. Conserva originales o explica cambios de forma reversible.
4. Ejecuta verificadores Python, `git diff --check`, tests/build disponible; si no hay Java local, anótalo y usa GitHub Actions. Prueba servidor dedicado y cliente/smokes; cuando sea posible genera capturas comparativas para las mejoras visuales. Actualiza este handoff y documentación con resultados reales, fallos pendientes y enlaces a CI/JAR.
5. Resume los cambios, pruebas, limitaciones y siguientes pasos. Trabaja solo en la rama actual de Arena; publica por PR si corresponde y mergea únicamente cuando el usuario lo haya pedido y los checks estén verdes.

Empieza ahora leyendo todo el repo/contexto relevante; luego inicia la auditoría priorizada y aplica las primeras correcciones reproducibles. No solicites credenciales ni inventes resultados.
```

---

## 13. Referencias rápidas

| Documento | Uso |
| --- | --- |
| [`Photon2-FXPack-Guia-Servidor.md`](Photon2-FXPack-Guia-Servidor.md) | Guía llana de `.fxpack` y del flujo de servidor |
| [`Photon2-FX-Assets-and-Bloom.md`](Photon2-FX-Assets-and-Bloom.md) | Layout de assets, recursos ausentes, controles de bloom |
| `docs/Photon2-Port-Context.md` | Contexto original del port (se mantiene) |
| `.github/workflows/forge-1.20.1.yml` | Definición real de la verificación |
| `ci-fixtures/` | Proyectos reales usados por el smoke (`scan`, `tornado`, `shield-trail`) |
