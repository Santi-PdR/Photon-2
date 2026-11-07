# FX, modelos, texturas y bloom en Photon 2 (Forge 1.20.1)

## Compartir un efecto con sus recursos

El formato recomendado es un paquete `.fxpack`. Photon lo carga como un paquete de recursos, sin tener que modificar el JAR instalado:

1. En el editor, usa **File → Export FXPack...** y guarda el archivo.
2. Photon exporta el efecto y los recursos que pueda resolver: texturas, modelos OBJ/glTF/JSON, materiales, graphs de shader y shaders core con sus imports.
3. El archivo debe quedar directamente en `<carpeta de la instancia>/photon/fxpacks/`. El exportador normalmente ya lo guarda ahí.
4. Si el paquete se copió después de abrir el juego, recarga los recursos con **F3+T** o con **Manage FXPack → Reload Resources**.
5. Para usarlo, conserva el namespace e ID del efecto: `namespace:nombre`.

### Sonido propio en un FX

En el editor, agrega una pista **Audio**, crea un clip en la línea de tiempo y pulsa **Import .ogg** en
su inspector. Photon copia el audio a `photon/assets/photon/sounds/user/`, lo registra en el formato
`sounds.json` de Minecraft y lo asigna al clip. El clip permite activar **Loop sound**; si se desactiva,
el audio suena una vez. Los controles de volumen, pitch, categoría y atenuación espacial siguen siendo
editables. Al exportar el efecto, Photon incluye el `.ogg` y su registro junto al FX en el `.fxpack`.

### Sincronización de FX desde un servidor

Con Photon instalado tanto en el servidor como en el cliente, el administrador coloca los `.fxpack` en
`<carpeta del servidor>/photon/fxpacks/`. Al entrar, Photon compara hashes y descarga solo los packs que
el cliente aún no tiene. Verifica el contenido, lo guarda en la caché de la instancia y actualiza los
recursos automáticamente; el jugador no tiene que instalar otra versión del mod ni habilitar un pack.
Los jugadores que ya están conectados reciben los packs al volver a entrar.

También se puede instalar un paquete descomprimido en una carpeta bajo `photon/fxpacks/`:

```text
photon/fxpacks/alex_effects/
├── pack.mcmeta                  # opcional; Photon registra el paquete directamente
└── assets/
    ├── alexfx/
    │   ├── fx/escudo.fx
    │   ├── textures/particle/escudo.png
    │   ├── models/particle/escudo.json
    │   └── shaders/core/escudo.json
    └── photon_fx/               # recursos internos que genera Export FXPack
```

La estructura siempre respeta el namespace que aparece en cada referencia. Por ejemplo,
`alexfx:textures/particle/escudo.png` corresponde a
`assets/alexfx/textures/particle/escudo.png`. Los modelos JSON arrastran también sus padres
y texturas; glTF arrastra sus buffers. Los shaders core incluyen sus archivos de etapas e imports.

### Incluir el paquete dentro de un mod/JAR

Para distribuirlo como parte de un mod, coloca la carpeta en
`src/main/resources/fxpacks/<nombre-del-paquete>/assets/...`. Photon detecta ese directorio dentro
del JAR del mod al iniciar la recarga de recursos. También se puede colocar el árbol de recursos
directamente en `src/main/resources/assets/...` de ese mod. Para compartirlo sin compilar un mod,
usa `.fxpack`; no hace falta modificar el JAR de Photon.

## Recursos ausentes de efectos viejos

El exportador copia cualquier recurso no vanilla que pueda resolver al exportar, incluidos recursos
de mods, resource packs y las carpetas de assets de Photon 1/2. Si un PNG/modelo/shader original ya
no está disponible en la instancia, el log de Photon avisa su ruta y no puede inventar el archivo
faltante. Abre el `.fxpack` como ZIP y comprueba la ruta bajo `assets/<namespace>/...`; añade ahí
el archivo original y vuelve a exportar/instalar. Antes de compartir recursos tomados de otro mod,
asegúrate de tener permiso para redistribuirlos. La carpeta Photon 1 `<instancia>/ldlib/assets`
también se monta automáticamente como recurso legado.

## Añadir brillo (Bloom)

- **Vista previa del editor:** en la barra superior de Scene, pulsa el icono de bloom/brillo; la tecla
  `B` alterna la vista previa.
- **En juego:** abre **Mods → Photon → Config**, activa **Enable Bloom** y ajusta el umbral.
  Este ajuste es global para los FX de Photon; no hay una casilla de bloom por emisor en este port.
- **Colores normales:** Bloom Threshold empieza en `0.5`; el pase mide el canal RGB más brillante,
  así que los rojos y azules saturados también pueden generar halo sin tratar el gris de la escena como luz.
  Súbelo si quieres que sólo brillen luces HDR.
- **Sólo FX:** el pase de brillo lleva una máscara de escena. Compara el frame con el que se empezó a
  dibujar contra el frame más los FX y sólo deja florecer los píxeles que añadió Photon, así que el cielo,
  el sol, las nubes y el terreno iluminado no generan halo ni lavan la imagen. En las rutas que trabajan
  sobre una capa de FX suelta (composición tardía, shader packs) la máscara queda neutra porque ahí sólo
  hay FX.
- **Intensidad:** el control global va de `0` a `2` y empieza en `0.18` para conservar el detalle de la escena.
  Se encuentra en **Mods → Photon → Config**, y también en la barra superior de la vista
  **Scene**: junto al icono de bloom hay un deslizador con su valor (`0.00`–`2.00`) que escribe el
  mismo `bloom_intensity`. El valor por defecto no cambia y los dos controles se mantienen sincronizados.
- **Ajuste del halo:** modifica **Bloom Intensity** y **Bloom mip level** en la configuración.

La vista previa del editor y el bloom de juego son controles distintos. Activar sólo el icono del
editor no cambia la configuración global de renderizado del juego.

## Texturas del editor y archivos de Photon 1

Photon guarda el contenido editable en su propio espacio dentro de la instancia:

```text
<instancia>/photon/
├── assets/<namespace>/...     # FX exportados, texturas, modelos y shaders
├── projects/*.fxproj          # proyectos del editor
├── resources/global/          # materiales, mallas, curvas y graphs del editor
└── fxpacks/                   # paquetes compartibles exportados
```

En **File → Save Project in Photon** se guarda una copia `.fxproj` dentro de `photon/projects`.
**Export FX** guarda el `.fx` en `photon/assets/photon/fx`; el selector de recursos permite elegir
archivos desde el directorio `photon/`. Estos archivos quedan en la carpeta de datos de la instancia,
no se escriben dentro del JAR comprimido en caliente. Para distribuirlos integrados en un mod, se
empaquetan bajo `src/main/resources/assets/` o `src/main/resources/fxpacks/` al compilar ese mod;
para compartirlos sin compilar, usa `.fxpack`.

La pestaña **Resources** guarda definiciones de materiales y otros recursos del editor (archivos .nbt/JSON). No almacena los píxeles de una imagen. Las texturas son archivos PNG dentro de una ruta de pack de Minecraft.

Por ejemplo, para una textura con ID myfx:textures/hollow/aura.png, coloca el archivo en:

```
<carpeta-de-la-instancia>/photon/assets/myfx/textures/hollow/aura.png
```

El campo **Texture** del material debe apuntar exactamente a myfx:textures/hollow/aura.png. Tras copiar archivos mientras el juego está abierto, recarga recursos con **F3+T**. Photon monta `photon/assets` como el pack del espacio de trabajo, y el exportador puede incluir sus recursos en `.fxpack`.

Los efectos antiguos conservan sus IDs de textura. Por ejemplo, ldlib:textures/enraged.png requiere:

```
<carpeta-de-la-instancia>/ldlib/assets/ldlib/textures/enraged.png
```

Los FX/proyectos por sí solos no contienen necesariamente esos PNG. Si el archivo referenciado no existe en un mod, pack de recursos, ldlib2/assets o ldlib/assets, Photon no puede reconstruir la imagen. Los modelos y shaders también deben existir en sus rutas de assets correspondientes.

### Referencias de los archivos Photon 1 compartidos

La inspección de `enraged.fx`, `conqclash.fxproj`, `domainfire.fxproj` y `electricburst.fxproj` encontró estas texturas externas:

| ID de textura | Ruta dentro de los assets |
| --- | --- |
| `ldlib:textures/enraged.png` | `assets/ldlib/textures/enraged.png` |
| `trueprimepiecetwo:textures/particle/hakilightningtest.png` | `assets/trueprimepiecetwo/textures/particle/hakilightningtest.png` |
| `trueprimepiecetwo:textures/particle/huraganconq.png` | `assets/trueprimepiecetwo/textures/particle/huraganconq.png` |
| `trueprimepiecetwo:textures/particle/lightning1.png` | `assets/trueprimepiecetwo/textures/particle/lightning1.png` |
| `trueprimepiecetwo:textures/particle/nothing.png` | `assets/trueprimepiecetwo/textures/particle/nothing.png` |
| `trueprimepiecetwo:textures/particle/pixxxii.png` | `assets/trueprimepiecetwo/textures/particle/pixxxii.png` |
| `cursed_fate_sky_manipulation:textures/particle/fire.png` | `assets/cursed_fate_sky_manipulation/textures/particle/fire.png` |

Pon esos archivos en `<instancia>/photon/assets/<namespace>/...`; por ejemplo `ldlib:textures/enraged.png` va en `photon/assets/ldlib/textures/enraged.png`. Photon y Minecraft suministran sus propios IDs de textura y no hay que copiarlos. También se pueden leer los árboles antiguos `<instancia>/ldlib/assets` y `<instancia>/ldlib2/assets` para compatibilidad. El log de Alex del 4 de noviembre también reportó `cursedfate:textures/particle/cursedeneryaura2.png` y el shader `photon:slash`; esos archivos fuente no estaban en los proyectos compartidos, así que siguen haciendo falta sus originales para renderizar esas referencias.

