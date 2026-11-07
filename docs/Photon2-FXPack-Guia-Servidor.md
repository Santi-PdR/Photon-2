# Cómo funciona el `.fxpack` (guía para niños de manzanitas)

Esta guía explica, sin dar nada por sabido, qué es un `.fxpack`, cómo se hace, cómo se instala y —
lo importante para un servidor — cómo hacer que **todos** los jugadores reciban los FX actualizados
solos, con sus texturas, materiales y modelos incluidos.

Si sólo quieres el resumen: **un `.fxpack` es un ZIP con los efectos y todo lo que usan; para que se
actualice solo en un servidor, el archivo va en `photon/fxpacks/` del servidor y Photon lo manda a
cada jugador al entrar.**

---

## 1. Qué es un `.fxpack`

Un `.fxpack` es **un archivo ZIP normal** (puedes abrirlo con 7-Zip, WinRAR o cambiarle el nombre a
`.zip`) que contiene:

- uno o varios efectos (`assets/<namespace>/fx/<nombre>.fx`),
- y **todos** los archivos que esos efectos necesitan: texturas `.png`, modelos `.obj` / `.gltf` /
  `.json`, shaders (`shaders/core/...`), sonidos `.ogg`, y una carpeta interna `assets/photon_fx/`
  con los materiales y graphs del editor.

No es un mod: no hay que compilar nada ni tocar el JAR de Photon. Photon lo monta como si fuera un
paquete de recursos de Minecraft y lee los efectos de ahí.

La correspondencia es directa:

```text
photon/fxpacks/escudo.fxpack        ← el archivo que compartes
        └── assets/
            └── escudo/             ← el "namespace" (el prefijo del ID)
                ├── fx/
                │   └── escudo.fx   ← el efecto; su ID es  escudo:escudo
                ├── textures/particle/escudo.png
                ├── models/particle/escudo.json
                └── shaders/core/escudo.json
            └── photon_fx/          ← material/ graph internos que generó el exportador
```

El **ID** con el que se usa el efecto es `namespace:nombre`. Por eso un paquete llamado
`escudo.fxpack` con `assets/escudo/fx/escudo.fx` se reproduce con `/photon play escudo:escudo`.

---

## 2. Cómo se hace un `.fxpack` (el autor)

1. Abre el editor de Photon y carga tu proyecto.
2. **File → Export FXPack...**
3. Elige dónde guardarlo. Por defecto Photon abre `<instancia>/photon/fxpacks/`, que es justo la
   carpeta donde se instalan.
4. Pon el nombre del efecto cuando te lo pida. Ese nombre, junto con el nombre del archivo, forma el ID.
5. Photon escribe el `.fx` y arrastra automáticamente lo que el efecto referencia:
   - texturas PNG (incluidas las de otros mods, si existen en tu instancia),
   - modelos OBJ/glTF/JSON con sus padres, texturas y buffers,
   - materiales y graphs del editor (van dentro de `assets/photon_fx/`),
   - shaders core con sus etapas e `#include`,
   - sonidos `.ogg` con su línea en `sounds.json`.
6. Si el log avisa de un recurso que **no pudo** encontrar, ese archivo no existe en tu instancia y
   el exportador no puede inventarlo: consíguelo, ponlo en `photon/assets/...` y vuelve a exportar.
   El mensaje dice la ruta exacta que falta.

Al exportar, el propio editor te dice si el paquete ya está montado ("live"), si otro paquete de más
prioridad lo tapa ("shadowed") o si hay que recargar recursos ("not mounted" → acepta y recarga).

---

## 3. Cómo se instala (un jugador cualquiera)

1. Copia el `.fxpack` dentro de `<instancia>/photon/fxpacks/`.
   - Si el archivo lo hizo el mismo juego, seguramente ya está ahí.
2. Espera un segundo. Photon vigila esa carpeta y **recarga solo** cuando ve un paquete nuevo o
   reemplazado (avisa en el log: `fxpacks: detected new pack ...` y luego `reloading resources
   automatically`).
3. Si querés forzarlo, `/photon_client reload` hace exactamente lo mismo que **F3+T**, sin salir del
   juego ni buscar la tecla.

No se toca `mods/`, no se toca `config/`.

Notas útiles:

- También se acepta una **carpeta descomprimida** directamente bajo `photon/fxpacks/`
  (`photon/fxpacks/mis_fx/assets/...`). Útil para editar a mano.
- Los paquetes de `photon/fxpacks/` se montan con la **prioridad más baja**: si un resource pack
  normal o tus assets del editor definen la misma ruta, gana el otro. Así puedes re-skinear un FX
  empaquetado sin tocar el paquete.
- Los paquetes que un servidor te manda se guardan como `server-<hash>.fxpack` en la misma carpeta y
  sólo se montan mientras estás conectado a ese servidor (al salir se retiran).

---

## 4. Cómo debería funcionar en el servidor (lo que preguntabas)

Objetivo: **el dueño del servidor sube un `.fxpack` nuevo y todos los jugadores lo ven sin instalar
nada, con materiales y texturas.**

Photon ya trae ese circuito hecho. Funciona así:

### 4.1 En el servidor

1. Instala Photon también en el servidor (el mismo JAR).
2. Crea la carpeta y deja los paquetes dentro:

   ```text
   <carpeta del servidor>/photon/fxpacks/
   ├── escudo.fxpack
   ├── tornado.fxpack
   └── ...
   ```

   Sólo se leen archivos que terminen en `.fxpack` y que estén directamente en esa carpeta
   (no en subcarpetas del servidor). El servidor **no** necesita recargar nada a mano: el hash se
   calcula al leer el archivo, y cada jugador que entra recibe el anuncio.

3. Cambia el archivo y pide a los jugadores que **vuelvan a entrar**. Photon anuncia los paquetes al
   jugador en el momento del login; no hay comando que "empuje" un paquete a quien ya está dentro.
   El cliente tampoco necesita F3+T: cuando la descarga termina, Photon recarga los recursos solo
   (`Server Photon FX packs are ready`).

### 4.2 Qué pasa cuando un jugador entra

1. El servidor calcula un **SHA-256** de cada `.fxpack` y manda al cliente una lista con nombre,
   tamaño y hash.
2. El cliente mira su caché local:
   - si ya tiene ese hash exacto → no descarga nada;
   - si no lo tiene o cambió → pide el archivo por trozos de 24 KB.
3. Al terminar, verifica el hash y el contenido (tiene que traer al menos un `assets/*/fx/*.fx`),
   lo guarda como `photon/fxpacks/server-<hash>.fxpack` y **recarga los recursos solo**.
4. El efecto ya se puede reproducir con `/photon play namespace:nombre`, y con las texturas y
   materiales que venían dentro del paquete.

Al desconectarse, los paquetes del servidor se desmontan; los que el jugador instaló a mano se
quedan.

### 4.3 Límites a tener en cuenta (están puestos en el código)

| Límite | Valor |
|---|---|
| Tamaño máximo por paquete | 64 MB |
| Suma de todos los paquetes | 128 MB |
| Paquetes por servidor | 32 |
| Tamaño descomprimido máximo | 256 MB |
| Entradas del ZIP | 20 000 |

Si un paquete se pasa, el servidor lo omite con un aviso en su log
(`Photon FX sync skipped ... (size ... exceeds limits)`), así que **no se puede actualizar nada de
lo que no llegue al cliente**: revisa el log del servidor cuando algo "no aparece".

### 4.4 Lo que hay que evitar

- **No** repartas los `.fx` sueltos: sin sus texturas/materiales llegarán "vacíos" o con textura
  rosa/negra.
- **No** cambies el nombre del archivo esperando que sea una "actualización": el cliente compara
  **hash**, no nombre, así que un contenido nuevo con el mismo nombre sí se actualiza… pero el
  ID (`namespace:nombre`) es el que usa el comando. Mantén namespace y nombre estables y cambia
  sólo su interior.
- **No** dejes el `.fxpack` en el servidor "para que lo vean los jugadores" sin Photon en el
  servidor: sin el mod, el servidor no anuncia nada y nada se descarga.
- Los jugadores **conectados** no reciben en caliente un paquete nuevo: el anuncio es en el login.
  Cambia el archivo y pídeles reconectar (o reinicia el servidor).

### 4.5 Comprobaciones rápidas

- Servidor: el log debe decir `Offered N Photon FX pack(s) to <jugador>` al entrar.
- Cliente: `Downloading Photon FX pack ...` y luego `Server Photon FX packs are ready`.
- En el cliente, `photon/fxpacks/server-<hash>.fxpack` existe y pesa lo mismo que el original.
- Si algo falla, el cliente registra `Photon FX pack transfer failed ...` o
  `Downloaded Photon FX pack failed integrity or format validation`.

---

## 5. Elegir el efecto en el editor

Todo lo que el juego puede cargar aparece en los buscadores del editor: efectos de mods, de resource
packs y de `.fxpack` montados. Dos sitios típicos:

- **Sub-emisores** (Emitter → sub emitters): el campo del efecto se busca por texto; vacío = sin
  efecto.
- **Instancias de FX** (clic derecho en la jerarquía → *Add FX Instance...*).

Estas listas se leen **en vivo** en cada búsqueda: si exportas un efecto o montas un paquete con el
editor abierto, aparece sin reiniciar. Un efecto inexistente nunca se lista, aunque esté escrito en
un proyecto viejo.

---

## 6. Bloom: el botón nuevo de la barra de escena

- En la barra superior de la vista **Scene** hay, junto al icono de bloom, un **control de
  intensidad**: un deslizador de `0.00` a `2.00` con su valor al lado.
- Escribe directamente `bloom_intensity` de la configuración: es el mismo valor que
  **Mods → Photon → Config**, y el deslizador se sincroniza si lo cambias ahí.
- El valor por defecto (`0.18`) **no cambia**: sólo se modifica lo que muevas tú.
- El icono de bloom de al lado sigue siendo el encendido/apagado de la vista previa (tecla `B`).

---

## 7. Preguntas cortas

**¿Necesito el mod en el cliente para ver los FX del servidor?** Sí. El `.fxpack` lleva contenido,
no código; Photon en el cliente es quien lo dibuja.

**¿Los jugadores pueden usar sus propios FX?** Sí: sus paquetes en su `photon/fxpacks/` conviven con
los del servidor.

**¿Puedo actualizar un paquete mientras el servidor está encendido?** Sí, el siguiente jugador que
entre recibe la versión nueva; los que ya están dentro, al reconectar.

**¿Cuándo hay que apretar F3+T entonces?** Casi nunca. Photon recarga solo en estos cuatro casos:
un `.fxpack` que aparece o cambia en `photon/fxpacks/`, un paquete que llega del servidor, una
exportación desde el editor, y una conversión de Photon 1 a 2 (`/photon_client convert`). El atajo
manual sigue existiendo (`/photon_client reload` o F3+T) por si editás archivos fuera del juego
—por ejemplo a mano dentro de `photon/assets/`— y querés verlo al instante.

**¿Y si mi servidor usa un proxy (BungeeCord/Velocity)?** Photon anuncia desde el servidor de Forge;
coloca los paquetes en el `photon/fxpacks/` de **cada** servidor que los use.

**¿Dónde veo el detalle técnico?** En [`Photon2-FX-Assets-and-Bloom.md`](Photon2-FX-Assets-and-Bloom.md).
