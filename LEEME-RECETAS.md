# Nuvora — Biblioteca inicial de Recetas

Este paquete contiene los dos archivos Kotlin modificados para incorporar la primera biblioteca de recetas al editor.

## Contenido

- `app/src/main/java/com/minipicasa/Editor.kt`
  - Añade el ajuste de temperatura de color.
  - Convierte el botón «Explorar Recetas» en una biblioteca con tres estilos predefinidos.
  - Las recetas se pueden seguir ajustando desde los controles habituales del editor.
- `app/src/main/java/com/minipicasa/Pager.kt`
  - Guarda y recupera el nuevo ajuste de temperatura.
  - Mantiene compatibilidad con las recetas guardadas por versiones anteriores (si no existe el nuevo dato, se usa temperatura neutra).

## Cómo incorporarlo

1. Haz una copia de seguridad o confirma que estás trabajando en una rama nueva.
2. Extrae el contenido de este ZIP directamente en la carpeta raíz del repositorio `Nuvora`.
3. Acepta reemplazar `Editor.kt` y `Pager.kt`.
4. Revisa los cambios y ejecuta la compilación mediante GitHub Actions antes de fusionarlos a `main`.

Este paquete no cambia el `applicationId`, el `versionCode` ni el `versionName`.

## Recetas incluidas

- **Luz cálida:** brillo suave, sombras levantadas y temperatura cálida.
- **Vívido:** saturación y contraste moderadamente reforzados.
- **Blanco y negro:** conversión monocromática con contraste y viñeta suaves.

Las recetas solo cambian los ajustes guardados de la foto; no sobrescriben el archivo original.
