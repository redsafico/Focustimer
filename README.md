# Focus Timer v2

Aplicación Android de temporizador flotante para aplicaciones seleccionadas.

## Flujo

1. Selecciona las aplicaciones desde Focus Timer.
2. Concede "Mostrar sobre otras aplicaciones".
3. Activa Focus Timer en Accesibilidad.
4. Entra a una aplicación seleccionada.
5. Escribe el motivo y los minutos.
6. Aparece un temporizador flotante arrastrable.
7. El motivo permanece visible.
8. Al llegar a cero vibra y vuelve a vibrar cada 10 segundos mientras sigas en la aplicación.
9. Al salir de la aplicación la sesión termina.

## Compilar en GitHub

El workflow `.github/workflows/build.yml` genera un APK debug.

El proyecto usa:
- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21
- compileSdk 35
- targetSdk 35
- Java 17

## Importante

La detección de aplicaciones utiliza AccessibilityService para recibir eventos de cambio de ventana. Android y algunos fabricantes pueden restringir servicios en segundo plano; si el teléfono tiene optimización agresiva de batería, Focus Timer debe configurarse como "sin restricciones" cuando esa opción exista.

## Instalación

Instala el APK debug generado por GitHub Actions y, al abrir la app:
- concede superposición;
- activa el servicio de accesibilidad;
- selecciona las aplicaciones.


## V3: detección corregida

La detección ahora ignora ventanas temporales como teclados y System UI. Solo se consideran aplicaciones reales aquellas que exponen una actividad `ACTION_MAIN` + `CATEGORY_LAUNCHER`.

Esto evita que el teclado cierre inmediatamente el temporizador después de escribir el motivo.


## V4: salida de aplicaciones corregida

La V4 mantiene el último paquete que corresponde a una aplicación realmente lanzable.
Los eventos repetidos de la misma app se ignoran y las ventanas temporales no cambian
el estado. Cuando Android informa una aplicación lanzable diferente, se considera
un cambio real de aplicación y se cierra la sesión activa.
