# PocketDither v1

## Objetivo

Entregar una app Android nativa de prueba con preview en tiempo real y captura procesada, priorizando que la foto guardada se parezca mucho a lo que ve el usuario en pantalla.

## Stack

- Kotlin
- Jetpack Compose
- CameraX
- Guardado en MediaStore

## Alcance cerrado de v1

- Preview de camara trasera con efecto dither aplicado en color.
- Captura de foto procesada con el mismo algoritmo y parametros que la preview.
- Guardado de la foto procesada en `Pictures/DitherCamera`.
- Controles:
  - `pixel size` del efecto.
  - patron Bayer `2x2`, `4x4` y `8x8`.
  - contraste del dither.
  - exposicion `- / +` usando compensacion de exposicion de camara.
  - autofocus continuo.
  - tap to focus y auto exposure metering sobre el punto tocado.

## Decisiones tecnicas

- Algoritmo inicial: ordered dithering con matrices Bayer y cuantizacion RGB por canal.
- La preview visible se genera desde `ImageAnalysis`; `PreviewView` queda activo como soporte de pipeline, enfoque y metering.
- La foto final se procesa desde `ImageCapture` con la misma funcion de procesado para maximizar fidelidad visual.
- `minSdk` 29 para simplificar guardado moderno en galeria.

## Riesgos aceptados en v1

- El rendimiento dependera del dispositivo porque el procesado es CPU-bound.
- El look es deliberadamente pixelado; la resolucion interna de preview no intenta ser foto-realista.
- En moviles muy lentos habra menos FPS en preview.
