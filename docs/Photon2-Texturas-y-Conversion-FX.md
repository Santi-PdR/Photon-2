# Texturas sueltas y conversión de FX de Photon 1

Guía corta para quien prueba con un pack real (por ejemplo `cursedfate`) y ve que las partículas
salen con el cuadriculado violeta o que faltan efectos en la lista.

## 1. «Convierto los FX del Photon 1 y no aparece todo»

```
/photon_client convert
/photon_client convert status
```

- `convert` lee **todas** las carpetas legacy que Photon conoce (el workspace, `ldlib2` y `ldlib`) y
  escribe cada efecto como `assets/photon/fx/<nombre>.fx`.
- Después imprime un informe: cuántos convirtió, cuáles **omitió y por qué** (normalmente «ya
  convertido») y cuáles fallaron con el error.
- `convert status` no convierte nada: lista cada `.fx` que encuentra y si ya tiene gemelo convertido.
  Sirve para saber si un efecto que «falta» es que no se convirtió o es que no está en la carpeta.

**Por qué antes no aparecían:** la conversión escribía `assets/photon/fx_old/<nombre>.fx` *dentro* de
`assets/photon/fx/`, así que el id del efecto salía `photon:fx_old/<nombre>`. El selector pide
`photon:<nombre>`, sin el segmento `fx_old/`, y por eso nunca lo encontraba. Ahora el destino es plano.
Los resultados anidados que queden de partidas anteriores se reportan en el log, no se mueven solos.

Después de convertir hay que recargar recursos (**F3+T**) para que la lista se reconstruya.

## 2. «Las partículas no cargan las imágenes»

```
/photon_client textures
```

Imprime, en este orden:

1. Las carpetas que Photon está mirando (y si cada una tiene `assets/`).
2. Los namespaces que encontró en disco y a qué carpeta apuntan.
3. Cada id de textura que nombran los efectos **ya cargados**, con su estado: `pack`, `loose folder`
   o `MISSING`.

Si tu pack no aparece en la lista de carpetas, añadilo a la config `photon-client.toml`:

```toml
[assets]
    extra_roots = ["C:/ruta/a/cursedfate", "D:/otro/lado/assets"]
```

Cada entrada se lee como `<carpeta>/assets/<namespace>/...`, pero la búsqueda es tolerante:

- el id tal cual (`textures/particle/glow.png`) dentro de su namespace;
- el mismo id bajo **cualquier otro** namespace que exista en disco;
- y, como último recurso, el **nombre del archivo** en cualquier parte de las carpetas buscadas.

Eso cubre el caso real: el efecto se llama `cursedfate:textures/particle/glow.png` pero el archivo
está en otra carpeta, en otro namespace, o suelto junto al `.fx`. Cuando lo encuentra, lo copia al
workspace (`photon/assets/<namespace>/...`) para que el id siga resolviendo al exportar el pack.

El selector de imágenes del editor ya no se queda callado: si rechaza un archivo, dice por qué en el
chat y en el log.

## 3. Qué falta de tu lado

- Recargá recursos después de convertir o de copiar archivos nuevos.
- Si el pack viene de Photon 1, corré `convert` **una vez** y mirá el informe antes de dar por hecho
  que faltan efectos.
- Si un id sale `MISSING`, pasame la salida de `/photon_client textures`: ahí se ve si el archivo no
  existe, si está en otro namespace o si el efecto nombra un id distinto del que creés.
