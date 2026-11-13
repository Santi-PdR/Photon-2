# Presets de materiales Kila

Photon incluye materiales Kila listos para usar desde el selector de materiales del editor y el explorador de presets integrado. Son puntos de partida editables: después de elegir uno se pueden cambiar sus módulos y propiedades sin modificar el preset original.

## Presets recientes

| ID | Nombre en español | Uso previsto | Qué aporta |
| --- | --- | --- | --- |
| `frosted_glass` | Cristal esmerilado | Superficie / mesh | Transparencia fría con borde y relieve suave. |
| `slime_puddle` | Charco de limo tóxico | Suelo / proyección | Celdas fluidas, distorsión lenta, borde de contacto y disolución. |
| `ember_rock` | Roca incandescente | Superficie / mesh | Roca iluminada con grietas incandescentes. |
| `soul_flame` | Llama de alma | Partícula | Llama cian espectral con animación y degradado. |
| `aurora_trail` | Estela de aurora | Trail | Cinta verde-cian-violeta con dos capas de flujo lento y bordes suaves. |
| `lava_burst` | Estallido de lava | Partícula | Flipbook de humo con mezcla de movimiento, desde rojo ceniza hasta un núcleo blanco caliente. |
| `frost_burst` | Estallido glacial | Partícula | Flipbook de humo azul y cian con núcleo blanco y distorsión sutil. |
| `healing_aura` | Aura curativa | Partícula | Pulso verde menta con nubes lentas y degradado cálido. |
| `poison_mist` | Niebla tóxica | Partícula | Humo animado con textura celular y distorsión de flujo sutil. |
| `water_burst` | Estallido de agua | Partícula | Onda translúcida deformada por flujo y desvanecida por vida. |
| `comet_trail` | Estela de cometa | Trail | Cinta aditiva con cabeza incandescente y gradiente cálido. |
| `void_gate` | Portal del vacío | Mesh | Abertura violeta giratoria con celdas animadas y borde Fresnel. |
| `solar_beam` | Rayo solar | Partícula | Haz dorado con suavizado de profundidad y degradado cálido. |
| `rune_floor` | Runas del suelo | Suelo / proyección | Sello rúnico giratorio que se proyecta y desvanece sobre el suelo. |
| `prismatic_shield` | Escudo prismático | Superficie / mesh | Escudo translúcido con borde cian y gradiente espectral. |
| `crystal_shard` | Fragmento de cristal | Superficie / mesh | Cristal translúcido con matcap de vidrio y borde frío. |
| `dust_devil` | Remolino de polvo | Partícula | Vórtice con silueta ruidosa, onda UV y desvanecimiento. |
| `ember_burst` | Estallido de brasas | Partícula | Humo animado con gradiente rojo-dorado y disolución. |
| `frost_ring` | Anillo glacial | Partícula | Onda expansiva azul que se vuelve blanca y se desvanece. |
| `chain_lightning` | Relámpago ramificado | Partícula | Rayo ramificado violeta-blanco con chisporroteo UV. |
| `plasma_trail` | Estela de plasma | Trail | Cinta azul-blanca con flujo animado y bordes suaves. |
| `meteor_strike` | Impacto de meteorito | Suelo / proyección | Marca radial proyectada y consumida por ruido. |
| `obsidian` | Obsidiana | Superficie / mesh | Vidrio volcánico opaco con iluminación y relieve. |
| `shadow_cloak` | Manto de sombras | Partícula | Velo translúcido con nubes móviles y desvanecimiento. |
| `star_gate` | Portal estelar | Mesh | Portal con runas giratorias y borde luminoso. |
| `steam_vent` | Vaho ascendente | Pantalla | Calor que asciende y distorsiona la escena de fondo. |
| `sand_blast` | Estallido de arena | Partícula | Polvo granular cálido con capas de ruido Perlin. |
| `tide_surge` | Marejada | Partícula | Onda turquesa con capas de ondas y deformación de flujo. |
| `mushroom_cloud` | Nube de hongo | Partícula | Flipbook de humo voluminoso con gradiente cálido y profundidad suave. |
| `faerie_dust` | Polvo de hadas | Partícula | Destellos finos con brillo animado y tinte violeta-menta. |
| `rune_trail` | Estela rúnica | Trail | Cinta rúnica turquesa con reflejos dorados. |
| `shadow_rift` | Grieta de sombras | Mesh | Vórtice oscuro con borde Fresnel violeta. |
| `lava_flow` | Río de lava | Suelo / proyección | Canales de lava sobre el suelo con coordenadas de mundo. |
| `crystal_aura` | Aura cristalina | Superficie / mesh | Esfera translúcida con matcap, celdas y borde frío. |
| `force_pulse` | Pulso de fuerza | Partícula | Anillo expansivo con distorsión radial y desvanecimiento. |
| `dust_storm` | Tormenta de polvo | Partícula | Frente de polvo denso con nubes y degradado arena. |
| `sun_lance` | Lanza solar | Partícula | Haz dorado concentrado, blanco caliente en el centro. |

Los nombres y descripciones del selector están traducidos al inglés, español y chino simplificado. Los treinta presets añadidos en estas tres rondas reutilizan texturas Kila que ya vienen en el mod: no agregan PNG ni amplían el conjunto de texturas que debe cargar el cliente.

## Elegir el renderer adecuado

- **Partícula:** `soul_flame`, `lava_burst` y `frost_burst` se aplican a un material de partícula estándar.
- **Trail:** `aurora_trail` debe asignarse en el renderer de `Trails`; activar trails no cambia automáticamente un material aplicado al renderer normal de partículas.
- **Suelo/proyección:** los presets de suelo necesitan la configuración de proyección y un emisor orientado hacia la superficie.
- **Superficie/mesh:** los presets de superficie están diseñados para renderizar un modelo; aplicarlos a un billboard no reproduce el resultado previsto.

La galería de presets integrada muestra cada material según su destino. El smoke test `KilaGalleryScenario` comprueba que los shaders compilan para los contextos de dibujo correspondientes y que los presets de tipo trail usan el renderer de trails.

## Archivos de implementación

- Catálogo y configuración: `src/main/java/com/lowdragmc/photon/client/gameobject/emitter/data/material/kila/KilaPresets.java`.
- El explorador aplica el mismo material que ya muestra su miniatura, evitando construir otra instancia al seleccionar: `KilaDialogs.presets`.
- La galería de pruebas coloca las categorías grandes en una cuadrícula compacta para mantenerlas dentro del fondo visible: `KilaGalleryScenario`.
- Selector de recursos incorporados: `src/main/java/com/lowdragmc/photon/gui/editor/resource/MaterialResource.java`.
- Galería y verificación visual: `src/main/java/com/lowdragmc/photon/uitest/KilaGalleryScenario.java`.
- Búsqueda rápida de módulos descartados por presupuesto de samplers: `KilaLayout.isDropped`, usada desde el recorrido de módulos del material.
- Traducciones: `src/main/resources/assets/photon/lang/{en_us,es_es,zh_cn}.json`.
