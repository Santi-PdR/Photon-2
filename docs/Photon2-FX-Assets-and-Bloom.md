# FX, modelos, texturas y bloom en Photon 2 (Forge 1.20.1)

## Compartir un efecto con sus recursos

El formato recomendado es un paquete `.fxpack`. Photon lo carga como un paquete de recursos, sin tener que modificar el JAR instalado:

1. En el editor, usa **File → Export FXPack...** y guarda el archivo.
2. Photon exporta el efecto y los recursos que pueda resolver: texturas, modelos OBJ/glTF/JSON, materiales, graphs de shader y shaders core con sus imports.
3. El archivo debe quedar directamente en `<carpeta de la instancia>/photon/fxpacks/`. El exportador normalmente ya lo guarda ahí.
4. Si el paquete se copió después de abrir el juego, recarga los recursos con **F3+T** o con **Manage FXPack → Reload Resources**.
5. Para usarlo, conserva el namespace e ID del efecto: `namespace:nombre`.

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
- **Colores normales:** baja **Bloom Threshold**; prueba `0.3`. Con el umbral alto, el juego sólo
  genera halo para luces HDR realmente mayores que 1.
- **Ajuste del halo:** modifica **Bloom Intensity** y **Bloom mip level** en la configuración.

La vista previa del editor y el bloom de juego son controles distintos. Activar sólo el icono del
editor no cambia la configuración global de renderizado del juego.

## Texturas del editor y archivos de Photon 1

La pestaña **Resources** guarda definiciones de materiales y otros recursos del editor (archivos .nbt/JSON). No almacena los píxeles de una imagen. Las texturas son archivos PNG dentro de una ruta de pack de Minecraft.

Por ejemplo, para una textura con ID myfx:textures/hollow/aura.png, coloca el archivo en:

```
<carpeta-de-la-instancia>/ldlib2/assets/myfx/textures/hollow/aura.png
```

El campo **Texture** del material debe apuntar exactamente a myfx:textures/hollow/aura.png. Tras copiar archivos mientras el juego está abierto, recarga recursos con **F3+T**. Photon monta ldlib2 como un pack externo de baja prioridad; así los recursos de usuario también se pueden incluir al exportar el efecto a .fxpack.

Los efectos antiguos conservan sus IDs de textura. Por ejemplo, ldlib:textures/enraged.png requiere:

```
<carpeta-de-la-instancia>/ldlib/assets/ldlib/textures/enraged.png
```

Los FX/proyectos por sí solos no contienen necesariamente esos PNG. Si el archivo referenciado no existe en un mod, pack de recursos, ldlib2/assets o ldlib/assets, Photon no puede reconstruir la imagen. Los modelos y shaders también deben existir en sus rutas de assets correspondientes.
