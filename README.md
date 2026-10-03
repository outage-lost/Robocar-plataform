# RoboCar Bluetooth

RoboCar es un sistema local y directo: un ESP32 DevKit recibe comandos por Bluetooth clásico y una aplicación Android nativa controla el vehículo. Se eliminaron backend, frontend web, API HTTP, WebRTC y Docker.

## Arquitectura

```text
Android 7+  ── Bluetooth SPP ──>  ESP32 DevKit  ──>  driver de motores
```

El firmware conserva los pines existentes: motores `12, 13, 14, 15`, LED `23` y PWM `25`.

## Firmware

Abre `ESP32_REFERENCE.ino` en Arduino IDE, selecciona **ESP32 Dev Module** y carga el programa. No requiere Wi‑Fi, servidor ni librerías externas al core ESP32. El dispositivo Bluetooth se anuncia como `RoboCar-ESP32`.

Protocolo:

- `D:<throttle>:<steering>:<speed>` — throttle y steering entre `-100` y `100`, velocidad PWM entre `0` y `255`.
- `L:0` / `L:1` — apaga o enciende el LED.
- `S` — parada inmediata.

Si no llega un comando de conducción durante 500 ms, el ESP32 detiene los motores automáticamente.

## Aplicación Android

El proyecto está en `android/` y tiene mínimo Android 7 (API 24). La interfaz está bloqueada en horizontal e incluye escaneo Bluetooth, conexión SPP, joystick izquierdo para avance/retroceso, joystick derecho para giro, botón de LED, envío a 20 Hz y parada al soltar o desconectar.

Se necesita Android SDK API 35 y Gradle 8.7 o posterior:

```bash
gradle wrapper --gradle-version 8.7
./gradlew :app:assembleDebug
```

El APK se genera en `android/app/build/outputs/apk/debug/app-debug.apk`. En Android 11 o inferior la primera búsqueda requiere permiso de ubicación; en Android 12 o posterior requiere permisos de Bluetooth cercano.

## Uso

1. Carga el firmware al ESP32 y reinícialo.
2. Activa Bluetooth en Android y acepta el emparejamiento si se solicita.
3. Abre la app, pulsa **ESCANEAR**, selecciona `RoboCar-ESP32` y pulsa **CONECTAR**.
4. Usa los joysticks; al soltarlos, el control vuelve al centro.

No se usan credenciales de backend ni servicios externos para controlar el auto.
