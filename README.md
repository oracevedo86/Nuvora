<div align="center">

# Nuvora

### Tu fotografía, tu estilo.

**Una galería fotográfica para Android con herramientas de visualización y edición, diseñada para adaptarse a teléfonos y tablets.**

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![Platform](https://img.shields.io/badge/Platform-Android-black?logo=android)

</div>

---

## ✨ Acerca de Nuvora

Nuvora es una aplicación de galería fotográfica para Android. Reúne la exploración de imágenes, la visualización a pantalla completa y herramientas de edición en una interfaz de estilo oscuro con acentos violetas.

El proyecto contempla una experiencia adaptable: la interfaz reorganiza sus elementos para aprovechar tanto la pantalla de un teléfono como el espacio disponible en una tablet.

## 📱 Vistazo a la aplicación

> Las siguientes imágenes son **mockups de presentación** basados en capturas de la aplicación. Se incluyen para mostrar cómo se ve Nuvora en distintos tamaños de pantalla.

### Teléfono

<p align="center">
  <img src="docs/screenshots/phone-gallery.jpg" alt="Nuvora en teléfono: galería" width="32%">
  <img src="docs/screenshots/phone-crop.jpg" alt="Nuvora en teléfono: herramienta de recorte" width="32%">
  <img src="docs/screenshots/phone-editor.jpg" alt="Nuvora en teléfono: editor de fotografías" width="32%">
</p>

### Tablet

<p align="center">
  <img src="docs/screenshots/tablet-viewer.jpg" alt="Nuvora en tablet: visor de fotografías" width="80%">
</p>
<p align="center">
  <img src="docs/screenshots/tablet-crop.jpg" alt="Nuvora en tablet: recorte" width="49%">
  <img src="docs/screenshots/tablet-editor.jpg" alt="Nuvora en tablet: editor" width="49%">
</p>

## 🧩 Funciones

- **Biblioteca de imágenes:** explora fotografías organizadas por carpetas.
- **Vistas flexibles:** cambia entre presentación en cuadrícula y lista.
- **Visor de fotografías:** visualización paginada, con información de fecha y posición dentro de la colección.
- **Selección múltiple:** selecciona varias imágenes para compartirlas o eliminarlas.
- **Recorte:** formato libre y relaciones de aspecto predefinidas.
- **Transformaciones:** rotación e inversión horizontal.
- **Ajustes de imagen:** brillo, sombras, contraste y saturación.
- **Efectos:** sepia, blanco y negro y viñeta.
- **Retoque automático:** ajuste automático de la imagen.
- **Diseño adaptable:** distribución específica para teléfonos y tablets.
- **Guardado no destructivo:** los cambios se exportan como una copia en `Pictures/Nuvora`, sin sobrescribir la fotografía original.

## 🛠️ Tecnologías

| Tecnología | Uso |
|---|---|
| Kotlin | Lenguaje principal |
| Jetpack Compose | Interfaz declarativa |
| Material 3 | Componentes y sistema visual |
| Coil | Carga de imágenes |
| DataStore Preferences | Preferencias de la aplicación |
| AndroidX | Componentes y compatibilidad Android |
| Gradle | Construcción del proyecto |

## 📋 Requisitos

- Android 8.0 (API 26) o superior.
- JDK 17 para compilar.
- Android SDK con Android 14 / API 34 para compilar esta versión del proyecto.

## 🚀 Compilar desde el código fuente

1. Clona el repositorio:

   ```bash
   git clone <URL_DEL_REPOSITORIO>
   cd Nuvora
   ```

2. Abre el proyecto en Android Studio y espera la sincronización de Gradle.

3. Ejecuta la aplicación desde Android Studio o compila el APK de depuración:

   ```bash
   gradle assembleDebug
   ```

El APK se genera en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## ⚙️ Integración continua

El repositorio incluye un workflow de GitHub Actions (`.github/workflows/build.yml`) que permite iniciar manualmente una compilación de depuración y publicar el APK como artefacto de la ejecución.

## 🗺️ Estado del proyecto

Nuvora está en desarrollo. Las funciones descritas corresponden a la implementación presente en esta versión del código; la interfaz y las capacidades pueden evolucionar.

## 🤝 Créditos

Nuvora es un proyecto independiente. Diseño, dirección del proyecto e implementación evolucionan mediante trabajo iterativo, pruebas en dispositivos y colaboración asistida por herramientas de IA.

---

<div align="center">

**Nuvora — Tu fotografía, tu estilo.**

</div>
