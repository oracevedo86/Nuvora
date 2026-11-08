# Nuvora — Recetas y Wallpaper Studio

Esta rama prepara las funciones de fotografía previstas para Nuvora 1.0.2.

## Recetas
- **Luz cálida:** brillo suave, sombras levantadas y temperatura cálida ajustable.
- **Vívido:** saturación y contraste moderadamente reforzados.
- **Blanco y negro:** conversión monocromática con contraste y viñeta suaves.
- Las recetas se pueden seguir ajustando y no sobrescriben la fotografía original.

## Wallpaper Studio
- Disponible desde el visor y desde el editor.
- Al abrirlo desde el editor, utiliza los ajustes actuales, incluso si todavía no se ha guardado la fotografía.
- Permite preparar fondos en orientación vertical u horizontal.
- Ofrece los encuadres **Rellenar** y **Ajustar**, además de zoom y desplazamiento mediante gestos.
- Permite aplicar el fondo a la pantalla de inicio, bloqueo o ambas.
- Utiliza Android WallpaperManager y genera una imagen al tamaño de pantalla, con límite de 4096 píxeles en el lado mayor para controlar el uso de memoria.

## Compatibilidad
- Se conserva el identificador de aplicación `com.minipicasa` para que la actualización mantenga la identidad de la app.
- Versión prevista: **1.0.2** (versionCode 3).
- Los ajustes de las recetas anteriores siguen siendo compatibles; la temperatura ausente se interpreta como neutra.

## Validación pendiente
- Ejecutar GitHub Actions en la rama `feature/recipes-1.0.2`.
- Probar Wallpaper Studio en el teléfono: ambas orientaciones, Rellenar/Ajustar y los tres destinos.
- Confirmar visualmente que la posición y el zoom del encuadre coincidan con el fondo aplicado.
