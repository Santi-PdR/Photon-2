# Cómo funciona el `.fxpack` (guía para niños de manzanitas)

Esta guía explica, sin dar nada por sabido, qué es un `.fxpack`, cómo se hace, cómo se instala y —
lo importante para un servidor — cómo hacer que **todos** los jugadores reciban los FX actualizados
solos, con sus texturas, materiales y modelos incluidos.

Si sólo quieres el resumen: **un `.fxpack` es un ZIP con los efectos y recursos; los packs públicos
van en `<servidor>/photon/fxpacks/` y se ofrecen a todos los jugadores. También puedes enviar tu
carpeta local completa `ldlib2/assets` a un jugador concreto, o exportarla como archivo para compartir.**

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

El **ID** del efecto es `namespace:nombre`. Por ejemplo, `assets/escudo/fx/escudo.fx` tiene el ID
`escudo:escudo`. Un operador puede probarlo en una entidad con `/photon fx escudo:escudo entity @p`.
Para la guía resumida, consulta [Photon en tu servidor: explicación fácil](Photon2-Servidor-en-5-minutos.md).

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

Para meter **muchos efectos en un solo pack**: **File → Export → Export All Effects to FXPack...**
(también sin proyecto abierto). Lista todos los proyectos guardados de `photon/projects/` **y** todos los
`.fx` sueltos de `photon/assets/<namespace>/fx/` (los convertidos de Photon 1, los exportados con Export FX,
los editados in situ; también los antiguos de `ldlib2/assets/`). Cada uno entra como `<pack>:<ruta>`;
desmarca los que no quieras. Un `.fx` que ya representa un proyecto (`editfx/`) o que se llama igual que
uno no se lista dos veces.
   El mensaje dice la ruta exacta que falta.

Al exportar, el propio editor te dice si el paquete ya está montado ("live"), si otro paquete de más
prioridad lo tapa ("shadowed") o si hay que recargar recursos ("not mounted" → acepta y recarga).

---

## 3. Cómo se instala (un jugador cualquiera)

1. Copia el `.fxpack` dentro de `<instancia>/photon/fxpacks/`.
   - Si el archivo lo hizo el mismo juego, seguramente ya está ahí.
2. Espera un par de segundos. Photon vigila esa carpeta y **recarga solo** cuando ve un paquete
   nuevo, reemplazado o borrado, en cuanto el archivo deja de crecer (avisa en el log: `fxpacks:
   detected new pack ...` y luego `reloading resources automatically`). Vale copiarlo en el menú
   principal (recarga ahí mismo) o mientras carga un mundo (recarga al entrar); con el juego
   cerrado, se monta solo al arrancar. Las carpetas descomprimidas no se vigilan: para esas, F3+T.
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
- Si ya tienes instalado un paquete **idéntico** (mismo contenido, con tu propio nombre de archivo), el
  servidor no te lo vuelve a mandar: se usa el tuyo. Por eso en **un solo jugador**, donde el servidor
  integrado ofrece los paquetes de esta misma carpeta, no aparecen copias `server-<hash>.fxpack` de tus
  exportaciones (las que dejó una versión anterior se borran solas al entrar al mundo).

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

3. Los que **ya están conectados** lo reciben solos: el servidor vigila esa carpeta una vez por
   segundo y, cuando un archivo aparece, se reemplaza o se borra y lleva 3 segundos sin cambiar
   (es decir, cuando la copia por SFTP terminó), vuelve a escanear y reenvía la lista a todos los
   jugadores online (en el log del servidor: `Photon FX packs changed on the server (1 replaced);
   offering the current list to N online player(s)`). Cada cliente compara hashes, descarga sólo lo
   que le falta y **recarga los recursos solo** al terminar (`Server Photon FX packs are ready`).
   No hay que reiniciar el servidor, ni pedirles que salgan, ni que recarguen nada a mano.

   Para subir una actualización, copia el archivo nuevo **encima** del viejo (mismo nombre): el
   cliente monta la versión nueva, desmonta la vieja y borra su copia `server-<hash viejo>.fxpack`.
   Si copias varios paquetes seguidos, cuentan como un solo cambio mientras no pasen más de 3
   segundos entre uno y otro; si una carpeta no para de cambiar, el servidor reenvía la lista como
   mucho 3 veces por minuto y el cambio espera (no se pierde).

   `/photon fxpacks` (consola u operador, permiso 2) sigue existiendo para forzar el reenvío a mano
   (el vigilante compara tamaño y fecha de modificación; un reemplazo con los dos iguales es el
   único que no nota).

   Ten en cuenta que la recarga de recursos en el cliente es la misma que F3+T: durante unos
   segundos (más en modpacks grandes) el jugador ve la pantalla de carga mientras el mundo sigue
   corriendo. Mejor publicar las actualizaciones en momentos tranquilos o avisar antes.

### 4.2 Qué pasa cuando un jugador entra

1. El servidor calcula un **SHA-256** de cada `.fxpack` y manda al cliente una lista con nombre,
   tamaño y hash.
2. El cliente mira lo que ya tiene:
   - si un `.fxpack` instalado en `photon/fxpacks/` tiene ese tamaño y ese hash → usa ese archivo
     (no descarga ni monta nada más);
   - si ya tiene ese hash exacto en su caché `server-<hash>.fxpack` → no descarga nada;
   - si no lo tiene o cambió → pide el archivo por trozos de 24 KB.
3. Al terminar, verifica el hash y el contenido (tiene que traer al menos un `assets/*/fx/*.fx`),
   lo guarda como `photon/fxpacks/server-<hash>.fxpack` y **recarga los recursos solo**. Tras esa
   recarga borra las copias `server-<hash>.fxpack` que el servidor dejó de servir (las versiones
   anteriores del mismo paquete) y, de paso, las que ningún servidor le ha listado en 7 días; las
   de otros servidores que visita se conservan.
4. El efecto ya está cargado con sus texturas y materiales. Un operador puede probarlo en una entidad
   con `/photon fx namespace:nombre entity @p`, o en un bloque con
   `/photon fx namespace:nombre block ~ ~ ~`.

Al desconectarse, los paquetes del servidor se desmontan; los que el jugador instaló a mano se
quedan.

### 4.3 Enviar tu carpeta LDLib2 a un jugador específico

Si los FX y materiales ya están en tu cliente dentro de `<instancia>/ldlib2/assets`, puedes pedir al
servidor que los empaquete y se los entregue a un jugador conectado:

```text
/photon fx send Oz
```

Reemplaza `Oz` por el nombre del jugador. Quien ejecuta el comando debe ser operador (permiso 2),
y tanto quien envía como quien recibe deben tener Photon y estar conectados para iniciar el envío.
Antes de leer archivos, el cliente muestra botones **[Confirmar subida]** y **[Cancelar]** (en el idioma del juego; en inglés, *Confirm upload* / *Cancel*); sólo al confirmar
comienza a empaquetar y subir. La exportación local también pide confirmación antes de leer archivos.
Photon empaqueta **todo** `ldlib2/assets`, lo sube al servidor y entrega el pack solo a ese jugador;
no lo añade al conjunto público que reciben los demás. El servidor guarda el pack por UUID bajo
`<servidor>/photon/private-fxpacks/`, así que vuelve a ofrecérselo al destinatario cuando se conecte
en otra ocasión. El cliente lo descarga por bloques y recarga sus recursos automáticamente.

### 4.4 Crear un archivo local para Discord u otro servicio

Para crear un `.fxpack` compartible sin enviarlo a un servidor o jugador, ejecuta en tu cliente:

```text
/photon_client export_ldlib2_assets
```

Photon primero pide confirmación en el cliente; sólo después lee toda la carpeta
`<instancia>/ldlib2/assets` y deja el resultado en
`<instancia>/photon/fxpacks/ldlib2-assets-<hash>.fxpack`. Puedes adjuntar ese archivo en Discord o
compartirlo con otro servicio de archivos. Quien lo reciba lo instala copiándolo a su propia carpeta
`<instancia>/photon/fxpacks/` y recargando recursos si Photon no lo detecta automáticamente.

Ambos métodos aceptan como máximo **512 MiB comprimidos** y **2 GiB descomprimidos** por pack.
El pack tiene que contener al menos un FX válido (`assets/<namespace>/fx/*.fx`); se omiten enlaces
simbólicos y se rechazan rutas ZIP inseguras. El envío directo tiene además una cuota de **2 GiB y 16
packs privados por destinatario**. Si el archivo supera el límite de adjuntos de Discord, usa otro
servicio de almacenamiento/transferencia.

### 4.5 Límites de la distribución pública (están puestos en el código)

| Límite | Valor |
|---|---|
| Tamaño máximo de cada `.fxpack` (público, privado o local) | 512 MiB comprimidos |
| Tamaño descomprimido máximo por pack | 2 GiB |
| Packs públicos en `<servidor>/photon/fxpacks/` | 32; hasta 512 MiB en total |
| Packs privados por destinatario | 16; hasta 2 GiB en total |
| Manifiesto que recibe un jugador (públicos + privados) | hasta 3 GiB |
| Descompresión total validada por manifiesto | 8 GiB |
| Entradas del ZIP | 20 000 |

Si un paquete se pasa, el servidor lo omite con un aviso en su log
(`Photon FX sync skipped ... (size ... exceeds limits)`), así que **no se puede actualizar nada de
lo que no llegue al cliente**: revisa el log del servidor cuando algo "no aparece".

### 4.6 Lo que hay que evitar

- **No** repartas los `.fx` sueltos: sin sus texturas/materiales llegarán "vacíos" o con textura
  rosa/negra.
- **No** cambies el nombre del archivo esperando que sea una "actualización": el cliente compara
  **hash**, no nombre, así que un contenido nuevo con el mismo nombre sí se actualiza… pero el
  ID (`namespace:nombre`) es el que usa el comando. Mantén namespace y nombre estables y cambia
  sólo su interior.
- **No** dejes el `.fxpack` en el servidor "para que lo vean los jugadores" sin Photon en el
  servidor: sin el mod, el servidor no anuncia nada y nada se descarga.
- **No** llames a un paquete `server-<64 caracteres hexadecimales>.fxpack`: así nombra el cliente
  sus copias descargadas, y el servidor ignora ese nombre (lo dice una vez en el log:
  `Photon FX sync ignores ...: a file named like a client cache copy is not published`). Renómbralo.
- Si un jugador no recibe un cambio, `/photon fxpacks` (servidor) o `/photon_client fxpacks`
  (cliente) fuerzan el reenvío. El comando `/photon fx send <jugador>` inicia una entrega privada
  inmediata a ese destinatario.

### 4.7 Comprobaciones rápidas

- Servidor: el log debe decir `Offered N Photon FX pack(s) to <jugador>` al entrar, y
  `Photon FX packs changed on the server (...)` seguido de un `Offered` por jugador unos 3 segundos
  después de copiar un paquete.
- Cliente, tras una actualización: `Downloaded Photon FX pack <nombre>`, `Server Photon FX packs are
  ready` y `Removed server-<hash viejo>.fxpack, a Photon FX pack copy the server no longer serves`.
- Cliente: `Downloading Photon FX pack ...` y luego `Server Photon FX packs are ready`.
- En el cliente, `photon/fxpacks/server-<hash>.fxpack` existe y pesa lo mismo que el original (salvo que
  el jugador ya tuviera instalado un paquete idéntico: entonces el log dice
  `Photon FX pack <nombre> offered by the server is already installed as <archivo>; using that copy`).
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
- Escribe la intensidad **del FX que tienes abierto** (`bloomIntensity` dentro del efecto), no un
  ajuste global: dos efectos pueden brillar distinto y el valor viaja con el FX al exportarlo.
- El valor por defecto (`0.18`) **no cambia**: sólo se modifica lo que muevas tú. Si varios FX
  brillan a la vez, el pase usa la intensidad más alta.
- El icono de bloom de al lado activa/desactiva el bloom **de ese FX** (tecla `B`); el interruptor
  global sigue siendo **Enable Bloom** en **Mods → Photon → Config**.

---

## 7. Preguntas cortas

**¿Necesito el mod en el cliente para ver los FX del servidor?** Sí. El `.fxpack` lleva contenido,
no código; Photon en el cliente es quien lo dibuja.

**¿Los jugadores pueden usar sus propios FX?** Sí: sus paquetes en su `photon/fxpacks/` conviven con
los del servidor.

**¿Puedo actualizar un paquete mientras el servidor está encendido?** Sí. El siguiente jugador que
entre recibe la versión nueva solo; para los que ya están dentro, `/photon fxpacks` desde la consola
(o que el jugador escriba `/photon_client fxpacks`) les vuelve a ofrecer la lista sin reconectar.

**¿Y si el jugador no quiere esperar al comando del admin?** `/photon_client fxpacks` en su cliente
pide la lista al servidor en ese momento. Es el mismo camino que usa el login, así que sólo baja lo
que le falta.

**¿Cuándo hay que apretar F3+T entonces?** Casi nunca. Photon recarga solo en estos cuatro casos:
un `.fxpack` que aparece o cambia en `photon/fxpacks/`, un paquete que llega del servidor, una
exportación desde el editor, y una conversión de Photon 1 a 2 (`/photon_client convert`). El atajo
manual sigue existiendo (`/photon_client reload` o F3+T) por si editás archivos fuera del juego
—por ejemplo a mano dentro de `photon/assets/`— y querés verlo al instante.

**¿Cómo le mando mis recursos de LDLib2 solo a Oz?** Como operador, ejecuta `/photon fx send Oz` con Oz conectado. Photon empaqueta toda tu carpeta local `ldlib2/assets`, se la sube al servidor y el servidor guarda el pack privado para ofrecérselo a Oz también en conexiones futuras.

**¿Cómo saco un archivo para compartir por Discord?** Ejecuta `/photon_client export_ldlib2_assets`. El `.fxpack` queda en `photon/fxpacks/` dentro de tu instancia. Puedes enviarlo como archivo; Oz lo instala poniéndolo en su propia carpeta `photon/fxpacks/`. El envío/exportación admite 512 MiB comprimidos y 2 GiB descomprimidos; para archivos mayores que el límite del servicio de chat usa almacenamiento externo.

**¿Y si mi servidor usa un proxy (BungeeCord/Velocity)?** Photon anuncia desde el servidor de Forge;
coloca los paquetes en el `photon/fxpacks/` de **cada** servidor que los use.

**¿Dónde veo el detalle técnico?** En [`Photon2-FX-Assets-and-Bloom.md`](Photon2-FX-Assets-and-Bloom.md).
