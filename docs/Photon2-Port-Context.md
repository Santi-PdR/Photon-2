# Contexto del port de Photon 2 a Forge 1.20.1

Este documento resume el estado del mod y las decisiones del port para que Santi, Alex u otra persona puedan retomarlo sin reconstruir el historial del chat. Para instrucciones de uso, consulta también [Photon 2: FX, assets y Bloom](Photon2-FX-Assets-and-Bloom.md).

## Proyecto y objetivo

- **Mod:** Photon, toolkit de efectos visuales (VFX) para Minecraft.
- **Repositorio:** [Santi-PdR/Photon-2](https://github.com/Santi-PdR/Photon-2).
- **Base del port:** Photon upstream `v26.2.2.9` (lógica), con comportamiento de `26.3.2.11` portado pieza por pieza.
  Ver [Photon2-Upstream-26.3-Analisis.md](Photon2-Upstream-26.3-Analisis.md) para el diff y qué es portable.

## Aclaración de versiones (por qué no se "actualiza" el número)

Comprobado contra el código fuente de upstream:

| Versión | Rama | Minecraft | Loader | LDLib2 |
| --- | --- | --- | --- | --- |
| `26.3.2.11` | `26.3` | **26.3** | NeoForge | `26.3.2.45.a` |
| `2.2.11` | `1.21` | 1.21.1 | NeoForge | `2.2.45` / `2.2.46.a` |
| `26.2.2.9` (base de este port) | `26.2` | 26.2 | NeoForge | `26.2.x` |
| **este port** | — | **1.20.1** | **Forge 47.x** | `2.2.39.a+forge.1.20.1` |

`ldlib2 2.2.46.a` es de Minecraft **1.21.1**, no de 1.20.1, y `26.3.2.11` es de Minecraft **26.3**. Para Forge 1.20.1
no existe ninguna de las dos. Por eso "actualizar a 26.3.2.11 + ldlib 2.2.46.a" no es un cambio de número en
`gradle.properties`: es cambiar de Minecraft y de loader. Lo que sí se puede, y es lo que se hace aquí, es portar el
**comportamiento** de 26.3 al código que ya corre en 1.20.1.

Los JAR de referencia de ambas líneas están en la rama `Origin` del repo (`photon-neoforge-26.3-26.3.2.11.jar`,
`ldlib2-neoforge-1.21.1-2.2.46.a-all.jar`); se usan para leer qué hizo upstream, no como dependencia de compilación.
- **Destino:** Minecraft `1.20.1`, Forge `47.x` (compilación actual con `47.4.28`; metadata declara compatibilidad desde `47.4.10`), Java 17.
- **ID de mod:** `photon`.
- **Licencia:** CC BY-NC-SA 4.0.
- **Artefacto distributivo:** `photon-forge-1.20.1-26.2.2.9-forge.1.20.1-all.jar`. El `-all.jar` incorpora LDLib2 y KilaGraph; LDLib 1 sigue siendo una dependencia externa requerida.

El objetivo ha sido conservar las funciones de Photon 2 y adaptar el código que dependía de APIs de Minecraft/NeoForge o de versiones recientes de LDLib2 a las APIs concretas de Forge 1.20.1. No es un mod independiente: las herramientas y los recursos de LDLib forman parte del entorno del editor.

## Qué ofrece el mod

- Editor visual para crear y previsualizar efectos (`.fx`) y proyectos de editor (`.fxproj`).
- Emisores y partículas con materiales, formas, renderers, modelos, texturas, shaders, trails, iluminación y animación/timeline.
- Carga de efectos por namespace y compatibilidad para convertir/abrir proyectos de Photon 1 y formatos anteriores de Photon 2.
- Comandos y sincronización de red para reproducir efectos en entidades y posiciones del mundo.
- Paquetes `.fxpack` para transportar un efecto junto con sus dependencias de assets.
- Audio `.ogg` desde la pista Audio del editor, con clip, opción **Loop sound**, volumen, pitch, categoría y atenuación espacial.
- Bloom global y su vista previa independiente en el editor.

## Dependencias y compatibilidad

| Componente | Versión/configuración del port | Papel |
| --- | --- | --- |
| Minecraft | 1.20.1 | Versión objetivo |
| Forge | build `47.4.28`; mínimo declarado `47.4.10` | Loader y runtime |
| Java | 17 | Toolchain y runtime esperado |
| LDLib2 | `2.2.39.a+forge.1.20.1` | Editor y APIs usadas por Photon; empaquetado dentro del JAR distributivo |
| KilaGraph | `20.1.0.15` | Grafo/materiales Kila; empaquetado dentro del JAR distributivo |
| LDLib | `1.0.52.a` | Dependencia externa del entorno; no queda sustituida por LDLib2 |
| Oculus/Iris | Opcional | Compatibilidad de shaders; Bloom con shaderpack tiene un ajuste aparte |

Las restricciones definitivas están en `src/main/resources/META-INF/mods.toml`; las versiones de compilación están en `gradle.properties` y `build.gradle`.

## Archivos del usuario y recursos

El workspace del editor y los paquetes se guardan en la carpeta de datos de la instancia, no se inyectan en caliente dentro del JAR de Photon:

```text
<instancia>/photon/
├── assets/<namespace>/...     # efectos, PNG, modelos, shaders y otros assets
├── projects/*.fxproj          # proyectos editables
├── resources/global/          # recursos del editor: materiales, mallas, curvas, graphs...
└── fxpacks/                   # paquetes .fxpack instalados/exportados
```

- **`.fx`** contiene el efecto que el juego carga por su ID (por ejemplo `alexfx:escudo`).
- **`.fxproj`** es un proyecto de trabajo del editor. Puede contener el efecto y datos editables; no garantiza que todos los archivos externos referenciados estén presentes.
- **`.fxpack`** es el paquete ZIP que se recomienda distribuir. Debe incluir `assets/<namespace>/fx/...fx` y los recursos que el FX referencia.
- **Resources** en el editor almacena definiciones de recursos, no los píxeles de PNG. Las imágenes deben estar en rutas de assets y con el namespace que indique el FX/material.
- Los assets empaquetados dentro de un mod/JAR deben compilarse bajo `src/main/resources/assets/...` o `src/main/resources/fxpacks/<paquete>/assets/...`. Esto requiere publicar un JAR nuevo. Para subir FX con frecuencia sin actualizar Photon, usa FX packs en el servidor.

Photon monta `photon/assets` como workspace de recursos. También se conserva acceso a assets legados de `ldlib/assets` y `ldlib2/assets` para proyectos antiguos. Las texturas de otros mods se identifican con el ID completo `namespace:textures/...`; el selector de texturas incluye Photon y namespaces de mods registrados en el Resource Manager. El selector de materiales incluye Kila y sus presets integrados.

### Migración de Photon 1

El conversor puede leer layouts antiguos de `.fx`/`.fxproj`, pero no puede reconstruir un PNG, modelo o shader que no exista en los assets cargados. Para los proyectos compartidos durante esta tarea se encontraron, entre otros, IDs `ldlib:textures/enraged.png`, varios `trueprimepiecetwo:textures/particle/...` y `cursed_fate_sky_manipulation:textures/particle/fire.png`. Si el recurso fuente falta en el cliente, mod, pack o árbol antiguo, hay que recuperar el original y ponerlo bajo `photon/assets/<namespace>/...` o incluirlo al exportar el `.fxpack`.

## FX packs y actualización al entrar a un servidor

Este mecanismo resuelve el objetivo de Alex de añadir efectos en el servidor sin mandar una actualización de Photon a cada jugador:

1. El administrador deja los archivos `.fxpack` directamente en `<carpeta del servidor>/photon/fxpacks/`.
2. Photon en servidor y cliente intercambian el manifiesto al iniciar sesión. El servidor anuncia nombre, tamaño y SHA-256.
3. El cliente pide únicamente packs cuyo hash todavía no tiene; el servidor los transmite en fragmentos de 24 KiB.
4. El cliente valida/guarda el paquete en su caché y actualiza los recursos de Photon automáticamente, sin que el jugador use F3+T ni reinstale el mod.
5. El efecto se ejecuta usando el mismo namespace e ID incluido en el pack.

Los clientes que ya están conectados reciben la actualización al volver a entrar; el flujo documentado no promete una descarga instantánea a mitad de sesión. Servidor y cliente necesitan tener Photon instalado, pero cuando sólo cambian FX/assets no hace falta reconstruir ni sustituir el JAR de Photon. Para cambiar código/funciones del mod sí se necesita una versión nueva del mod.

Límites de validación del lado del servidor en `FxPackSync`: hasta 32 packs por manifiesto, 64 MiB por pack, 128 MiB de transferencia total, 256 MiB expandidos por ZIP y 20 000 entradas. El código rechaza rutas ZIP sospechosas y verifica los hashes.

## Port de comportamiento de 26.3

Estado: primera tanda mergeada/abierta en PR #9, con candado de CI propio.

### Portado: modos de vida Loop y PingPong

Antes una partícula sólo podía vivir una vez: su edad normalizada `T` subía de 0 a 1 y moría. Ahora
`ParticleConfig.lifetimeMode` elige:

- **Once** — lo de siempre; un archivo sin modo lee `Once`, así nada viejo cambia.
- **Loop** — `T` vuelve a 0 al cumplir cada vida; la partícula no muere de edad.
- **PingPong** — `T` va de 0 a 1 y vuelve; sirve para curvas cuyos extremos no coinciden.

Una partícula cíclica muere cuando **su emisor se apaga**: termina el ciclo en el que está y sostiene
el último valor. `ParticleLifetime` calcula todo desde la edad, sin estado guardado, así que una
partícula recargada cae en la misma fase.

### Portado: salida de clip `Drain`

`Clip.exitMode` (`Cut` / `Drain`). Con `Cut` (lo de siempre, y el valor por defecto) el objeto se detiene
en el instante en que su clip termina. Con `Drain` deja de hacer trabajo nuevo y lo que ya está en
pantalla termina solo: las partículas en vuelo envejecen y los emisores cierran su ciclo. Es la
diferencia entre un efecto que **termina** y un efecto que **desaparece**.

Se elige en el editor: clic derecho en el clip → **Exit**, o en el configurador del clip. Sólo se
guarda en el NBT cuando vale `Drain`, así un proyecto sin tocar se guarda igual que antes.

### Portado: secciones de timeline

Una timeline puede ahora partirse en trozos con nombre (`Timeline.sections()`), cada uno con:

- **inicio** — el trozo va desde su inicio hasta el inicio del siguiente (el último, hasta el final del
  contenido);
- **repeticiones** — `1` por defecto; `0` significa "siempre" (hasta que algo lo mueva);
- **qué pasa después** (`then`) — `LINEAR` sigue al siguiente trozo, `END` termina la timeline, `GOTO`
  salta al trozo cuyo nombre dice `target`.

Además `Timeline.releaseSection()` marca un trozo de **despedida** y `releaseNow()` decide si se entra
en él al instante o al terminar la vuelta actual. Cuando el efecto se destruye sin forzar, en vez de
cortarse en seco entra en ese trozo: lo que estaba en pantalla termina, y un trozo marcado "siempre"
se juega **una vez** (por eso la despedida no puede quedarse girando para siempre).

Una timeline **sin** secciones se comporta exactamente como antes: el reloj avanza `tiempo + velocidad`
y no se guarda ninguna clave nueva (ni `sections` ni `release`), así que todos los proyectos ya hechos
se leen y se escriben igual.

Detalles que se portaron porque cambian lo que se ve:

- señales en los bordes — el trozo que queda detrás de un salto se dispara igual, si no una señal
  después del último tick evaluado de un trozo repetido no sonaría nunca;
- los fotogramas entre dos ticks no interpolan a través de un salto: un `PingPong`/bucle pequeño se
  vería congelado en el borde en vez de dar el salto de golpe;
- `Clip.seamMode` (`Auto` / `Restart`) decide qué pasa con un objeto que sigue dentro de su clip cuando
  la timeline salta o se repite: `Auto` deja seguir lo que ya está en marcha y reinicia sólo lo que
  terminó; `Restart` lo reinicia siempre.

El editor sólo obedece las secciones durante la reproducción en vivo; al arrastrar el cabezal
(replay desde 0) se recorre la timeline en orden, sin bucles ni saltos, que es lo que uno espera al
buscar un momento concreto.

### Arreglado: el buscador de efectos sólo mostraba lo ya elegido

Segunda vez que Alex reporta lo mismo ("lo de las partículas y la lista de fx siguen igual"). La lista de
candidatos ya estaba bien — se lee en vivo y tiene todos los ids. El fallo estaba en el **widget** de
búsqueda que Photon lleva dentro (LDLib 2.2.39): al abrirse hacía la búsqueda con el texto que el campo
todavía tenía, que es el nombre del valor **ya elegido**. Resultado: un desplegable de una sola línea (o
vacío la primera vez), y al teclear se **añadía** al nombre viejo, así que la búsqueda quedaba en
`nombreElegidoX` y no encontraba nada. Parecía que el buscador "no hacía nada".

LDLib lo arregló en su 2.2.39.8, que aquí no se puede usar porque la que se empaqueta va fijada al port.
`SearchComponentMixin` trae ese mismo arreglo: al abrir, repite la búsqueda en vacío y deja el nombre viejo
**seleccionado**, de modo que la primera tecla lo reemplaza. Con eso quedan arreglados a la vez todos los
selectores que usan ese widget: id del sub-emisor, id de la instancia FX, entradas de material, bloques y
entidades de las opciones de colisión.

Además salió un bug propio al probarlo: la migración de los defaults antiguos de bloom comparaba **por
valor** en trabajo diferido, así que arrastrar el slider y pasar exactamente por `1.0` devolvía la
intensidad a `0.18`. Ahora esa migración corre una sola vez, al arrancar.

### No portado (y por qué)

- **Transparencia order-independent / dos puntos de render libres** (`PhotonOit`, `PhotonFrameSeams`):
  dependen de `RenderPipeline` y `GpuDevice` de Minecraft 26.3. No existen en 1.20.1.
- **`parallel_bake` / `PhotonBakeWork`**: trabajo en hilos sobre la API de render nueva.
- **Instancing por GPU por defecto, adaptación automática de GLSL, Iris 1.11.7**: atados a versiones de
  loader/shader distintas a las de este port.

### Límites

CI verifica comportamiento (`[lifetime-mode-smoke]`, `[drain-exit-smoke]`, `[sections-smoke]`,
`[release-section-smoke]`, `[sections-save-smoke]`, `[search-box-smoke]`), no apariencia. Que un `PingPong` se vea bien en un
efecto concreto, que `Drain` dé la sensación buscada y que una despedida dure lo que tiene que durar
sigue necesitando la prueba de Alex en juego.

Lo que **todavía no hay** de las secciones, dicho claro:

- no hay interfaz en el editor para crearlas ni para moverlas en la línea de tiempo: se definen por
  código (`Timeline.sections()`) y se guardan y se leen bien;
- la despedida se reproduce en la timeline del efecto, no en las timelines internas de un efecto
  anidado (un `.fx` dentro de otro): esas se siguen cortando como antes.

El reloj, el guardado y la reproducción de la despedida están completos; el editor de secciones es el
paso siguiente.

## Audio

El audio se agrega dentro del editor: pista **Audio** → crear clip en timeline → importar un archivo `.ogg` en el inspector. Photon copia el archivo al workspace, registra el evento en `sounds.json` y lo asigna al clip. **Loop sound** controla si repite; el clip conserva sus opciones de volumen, pitch, categoría y atenuación. La exportación a `.fxpack` debe llevarse el `.ogg` y el registro de sonido que necesita.

## Bloom y los cuatro problemas reportados por Alex

Las capturas más recientes señalaban:

1. **Bloom ausente o pantalla lavada.** El umbral anterior se evaluaba por luminancia: los rojos/azules saturados podían no superar el corte. Además, la composición anterior reemplazaba parte del color de la escena, produciendo sobreexposición.
2. **Materiales built-in no visibles.** Kila y sus presets no estaban registrados en el `MaterialResource` que alimenta el selector del editor.
3. **Assets/texturas de mods difíciles de seleccionar.** El selector se limitaba a los recursos conocidos del editor; no enumeraba assets activos de Photon y otros namespaces.
4. **Trails entrecortados al moverse rápido y color.** Una actualización por tick podía dejar huecos cuando el emisor avanzaba mucho, y había que asegurar que el gradiente del trail alcanzara los puntos de GPU/CPU.

En el estado actual del port, Bloom mide el canal RGB más alto, aplica una composición aditiva conservadora y preserva el color de la escena. Los defaults actuales son **Bloom Threshold `0.5`** y **Bloom Intensity `0.18`**. El código migra los defaults antiguos persistidos (`1.001`, `0.1`, `0.15`, `0.65` para umbral y `0.7`/`1.0` para intensidad) a esos valores. Bloom sigue siendo una opción global de Photon; la vista previa del editor (icono o tecla `B`) es independiente de **Mods → Photon → Config → Enable Bloom**. Con Iris/Oculus existe también `Enable Bloom with Iris shader`.

Los trails insertan muestras espaciales intermedias con un límite de 64 muestras por tick, para reducir huecos sin crear cantidades ilimitadas de puntos ante teletransportes. El humo de CI incluye comprobaciones para movimiento rápido y un gradiente rojo-azul.

## Último cambio publicado y validación

- **PR:** [#3 — Fix bloom, material assets, and fast trails for Forge 1.20.1](https://github.com/Santi-PdR/Photon-2/pull/3), integrado en `port/forge-1.20.1-2.2.9`.
- **Commit de merge:** `9a70f02578ccb5002dea90311852534af076077b`.
- **Respaldo previo:** rama `backup/port-forge-before-alex-captures-24d4306`.
- **CI:** GitHub Actions run `42256077211`, commit de cambios `c0e4dd95ffcd7cd1164ead67f4f1dbd112f028fa`; compilación y pruebas de inicio servidor/cliente headless finalizaron en verde. Los smoke checks cubren Bloom, selector Kila, movimiento/color de trails, sincronización automática de FX packs y comprobaciones históricas de conversión/proyectos/render.
- **JAR instalado para probar:** `D:\Minecraft\SKLauncher\sklauncher-data-v4\instances\test-1\mods\photon-forge-1.20.1-26.2.2.9-forge.1.20.1-all.jar`.
- **SHA-256 del JAR instalado:** `09fd2085faa54b4d5a51e2d445a7e94394ccaa8bc9f5d14557199d3002ada791`.
- **Verificación local:** el archivo declara `modId="photon"` y hay un solo JAR Photon en `test-1/mods`.
- **Límite de la verificación:** no se abrió Minecraft en la PC del usuario. CI headless comprueba arranque y condiciones de código, pero la apariencia final y el uso real en un mundo/servidor deben confirmarse en la próxima prueba manual del usuario.

## Historial relevante de reportes

- **Carga de `.fx`/`.fxproj` viejos:** aparecieron cuadros “Loading Failed” en el editor. El port ha recibido adaptadores para layouts legacy de Photon 1 y proyectos manejados de Photon 2; los smoke tests abren ejemplos de esos formatos. Aun así, si el proyecto refiere a recursos externos que no se encuentran, la migración no los puede inventar.
- **Efectos incompletos o invisibles:** se reportaron tornado parcialmente visible y problemas anteriores con slash, scan, arrow trail, ice step, shield y crystal. Después el usuario confirmó que ice step funcionaba y más tarde que la mayoría de los efectos ya se veían bien; el tornado seguía bajo revisión. Los smoke tests ejercitan varias rutas de shaders, modelos y render, pero no certifican cada FX de usuario ni sustituyen su comparación visual en juego.
- **Editor de FX:** también se reportó que Export FX no abría un diálogo. Se añadieron rutas de exportación y el flujo de Export FXPack/Manage FXPack; para una actualización de servidor, el formato importante es `.fxpack`.
- **VFX combinados y cámara:** Alex informó que efectos hechos con varios meshes/texturas/luz podían volverse invisibles en primera persona. La integración de materiales y render se ha ido ajustando, pero ese caso compuesto no se marca como resuelto en este documento sin una reproducción/confirmación suya tras el último JAR.
- **Lentitud o artefactos:** hubo fallos por memoria de Java en un arranque local, crashes de render con mod listings que no identificaban la causa, partículas negras y trails entrecortados. Un listado de mods en estado `DONE` no es diagnóstico suficiente; para una caída concreta se necesita el `latest.log`/crash report de esa misma sesión y jugador.

## Límites y criterios de diagnóstico

- Un archivo `.fx` puede cargar pero no mostrar su textura si el namespace/archivo no existe o el shader/modelo apunta a una ubicación equivocada. Revisar el ID, la ruta ZIP `assets/<namespace>/...` y el Resource Manager activo.
- El selector puede mostrar recursos registrados; no descarga por sí solo un asset que falta en el cliente. Para recursos privados o personalizados, distribuirlos dentro del `.fxpack`.
- El JAR fijo no cambia cuando Alex modifica un efecto en el disco del servidor: tiene que exportar un `.fxpack` nuevo y colocarlo en `photon/fxpacks`. Si cambia código del mod, sí corresponde actualizar JAR.
- Los smoke tests pueden comprobar composición de shader, carga y datos de partículas sin validar cómo luce cada efecto artístico en todas las GPUs, shaderpacks, cámaras y combinaciones de mods.
- Para reproducir una falla, identificar primero si es carga, asset faltante, configuración Bloom, interacción con Oculus/Embeddium/Flywheel u otro crash. Comparar el log del jugador afectado y registrar el nombre/ID del FX y si ocurre en primera/tercera persona.

## Compilar y diagnosticar

Desde la raíz del proyecto, con JDK 17:

```powershell
.\gradlew.bat clean build
```

El artefacto distributivo sale en `build/libs/photon-forge-1.20.1-26.2.2.9-forge.1.20.1-all.jar`. Instala el `-all.jar`; no pongas el JAR slim al lado. El workflow de CI vive en `.github/workflows/forge-1.20.1.yml`.

Al investigar un fallo visual, guardar el `latest.log` del jugador que realmente lo reproduce y revisar crash report si el cliente se cae. Un listado `DONE` de mods sólo demuestra que Forge los enumeró, no que el efecto/material haya renderizado. Para assets faltantes se necesita la ruta/namespace del recurso y verificar si el archivo existe dentro del `.fxpack` o de los packs activos.

## Preferencias de trabajo para este proyecto

- No abrir ni controlar la PC/Minecraft del usuario; el usuario prueba el JAR en `test-1` cuando quiera.
- Priorizar cambios útiles mientras corre CI; el usuario pidió evitar verificaciones repetidas sin progreso.
- Mantener el JAR de Photon actualizado en `test-1/mods` cuando haya un build nuevo que haya pasado las comprobaciones y reemplazar el JAR viejo en vez de acumular versiones duplicadas.
- Las capturas y mensajes de Alex describen solicitudes/reporte, no garantizan que la configuración del usuario contenga los assets necesarios ni que el efecto exacto esté reproducido por CI.

