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

Los nombres y descripciones del selector están traducidos al inglés, español y chino simplificado.

## Elegir el renderer adecuado

- **Partícula:** `soul_flame`, `lava_burst` y `frost_burst` se aplican a un material de partícula estándar.
- **Trail:** `aurora_trail` debe asignarse en el renderer de `Trails`; activar trails no cambia automáticamente un material aplicado al renderer normal de partículas.
- **Suelo/proyección:** los presets de suelo necesitan la configuración de proyección y un emisor orientado hacia la superficie.
- **Superficie/mesh:** los presets de superficie están diseñados para renderizar un modelo; aplicarlos a un billboard no reproduce el resultado previsto.

La galería de presets integrada muestra cada material según su destino. El smoke test `KilaGalleryScenario` comprueba que los shaders compilan para los contextos de dibujo correspondientes y que los presets de tipo trail usan el renderer de trails.

## Archivos de implementación

- Catálogo y configuración: `src/main/java/com/lowdragmc/photon/client/gameobject/emitter/data/material/kila/KilaPresets.java`.
- Selector de recursos incorporados: `src/main/java/com/lowdragmc/photon/gui/editor/resource/MaterialResource.java`.
- Galería y verificación visual: `src/main/java/com/lowdragmc/photon/uitest/KilaGalleryScenario.java`.
- Traducciones: `src/main/resources/assets/photon/lang/{en_us,es_es,zh_cn}.json`.
