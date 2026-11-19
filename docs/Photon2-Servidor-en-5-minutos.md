# Photon en tu servidor: explicación fácil

## La idea en una frase

El **servidor guarda y reparte los paquetes**. Cada jugador necesita tener Photon instalado para poder **dibujar** esos efectos. El paquete lleva los FX y sus recursos; no reemplaza el mod.

## Si administras el servidor

1. Instala el mismo JAR de Photon en el servidor y en los clientes. Usa la versión de Minecraft/Forge compatible con ese JAR.
2. En el editor de Photon, crea o abre un FX y elige **File → Export FXPack...**. El paquete incluye el efecto y los recursos que encontró (texturas, modelos, materiales y shaders).
3. Copia el archivo `.fxpack` a esta carpeta, junto al servidor:

   ```text
   <carpeta-del-servidor>/photon/fxpacks/mi_paquete.fxpack
   ```

   Pon el archivo directamente ahí, no dentro de otra subcarpeta. No lo pongas en `mods/`.
4. Reinicia el servidor la primera vez que instales Photon. Después, al entrar, los jugadores reciben los packs automáticamente.
5. Si subes o reemplazas un pack y quieres enviarlo ahora a quienes ya están conectados, ejecuta en consola o como operador:

   ```text
   /photon fxpacks
   ```

   No hace falta reiniciar ni echar a los jugadores.

## Si eres jugador

1. Instala Photon en tu cliente y entra al servidor.
2. Espera a que termine la descarga. Photon monta el pack y recarga los recursos por ti. No hace falta copiar el `.fxpack`, activar un resource pack ni pulsar F3+T.
3. Si el administrador acaba de actualizar un pack mientras ya estabas conectado, pide la lista otra vez desde el chat:

   ```text
   /photon_client fxpacks
   ```

   El comando vuelve a consultar al servidor y descarga solo lo que falta o cambió.

Al salir del servidor se desmontan los packs públicos y privados que ese servidor te prestó. Tus packs personales, guardados en la carpeta `photon/fxpacks/` de tu instancia, se quedan.

## Compartir todos tus recursos LDLib2

Si eres operador y quieres mandar tu carpeta local `<instancia>/ldlib2/assets` a un jugador conectado (por ejemplo Oz), usa:

```text
/photon fx send Oz
```

El cliente del operador primero muestra **Confirm upload / Cancel**; no se lee ningún archivo hasta que confirmes. Después el servidor entrega el pack solo a Oz, lo conserva para sus próximas conexiones y su cliente recarga los recursos automáticamente.

Para crear un archivo y compartirlo por Discord u otro servicio, ejecuta en tu cliente:

```text
/photon_client export_ldlib2_assets
```

Photon primero te pide confirmar; sólo entonces lee y empaqueta el contenido en `<instancia>/photon/fxpacks/`. Tu amigo lo instala copiándolo a su propia carpeta `photon/fxpacks/`. El límite es 512 MiB comprimidos / 2 GiB descomprimidos por pack; si no entra como adjunto en Discord, usa un servicio de almacenamiento.

## ¿Cómo se dispara un efecto?

Descargar un FX **no lo dispara automáticamente**. Normalmente el autor lo conecta a un emisor dentro de otro FX. Un operador también puede probarlo por comando:

```text
/photon fx mi_paquete:mi_efecto entity @p
```

Eso reproduce el efecto sobre el jugador más cercano. Para ponerlo en un bloque:

```text
/photon fx mi_paquete:mi_efecto block ~ ~ ~
```

Estos comandos son de servidor y requieren permiso de operador (nivel 2). Un jugador normal recibe y ve los FX que el servidor active, pero no puede ejecutar esos comandos si no tiene permiso.

El ID se forma con el namespace y el nombre del archivo: `assets/mi_paquete/fx/mi_efecto.fx` corresponde a `mi_paquete:mi_efecto`.

## ¿Y los presets nuevos?

La galería de Kila ofrece **materiales listos para aplicar** (por ejemplo, la nova de escarcha, el rayo ramificado y el eclipse lunar). Un preset de material es una apariencia, no un FX completo que se ejecute solo. Aplícalo a un emisor/partícula en el editor y luego exporta el proyecto como `.fxpack` para repartirlo.

## Si algo no aparece

- Comprueba que Photon esté instalado en **servidor y cliente**.
- Revisa que el pack esté en `photon/fxpacks/` del servidor y termine en `.fxpack`.
- Comprueba el ID: `namespace:nombre`, no el nombre del ZIP.
- Para actualizar a jugadores conectados, usa `/photon fxpacks`; para que un jugador vuelva a preguntar, `/photon_client fxpacks`.
- Los FX personalizados necesitan todos sus recursos. Exporta con **Export FXPack...** para que Photon incluya lo que el proyecto referencia.

Para la explicación técnica, tamaños máximos y diagnóstico de logs, consulta la [guía completa de FX packs](Photon2-FXPack-Guia-Servidor.md).
