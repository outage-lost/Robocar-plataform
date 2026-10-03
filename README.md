# RoboCar Bluetooth

RoboCar es un sistema local y directo: un ESP32 DevKit recibe comandos por Bluetooth clásico y una aplicación Android nativa controla el vehículo. Se eliminaron backend, frontend web, API HTTP, WebRTC y Docker.

## Arquitectura

```text
Android 7+  ── Bluetooth SPP ──>  ESP32 DevKit  ──>  driver de motores
```

El firmware usa exactamente esta asignación física del L298N:

| Señal | GPIO | Responsabilidad |
|---|---:|---|
| LED | D13 | LED, exclusivamente |
| ENA | D12 | PWM/velocidad motor A |
| IN1 | D14 | Dirección motor A |
| IN2 | D27 | Dirección motor A |
| IN3 | D26 | Dirección motor B |
| IN4 | D25 | Dirección motor B |
| ENB | D33 | PWM/velocidad motor B |

## Firmware

Abre `ESP32_REFERENCE.ino` en Arduino IDE, selecciona **ESP32 Dev Module** y carga el programa. No requiere Wi‑Fi, servidor ni librerías externas al core ESP32. El dispositivo Bluetooth se anuncia como `RoboCar-ESP32`.

Protocolo:

- `D:<throttle>:<steering>:<speed>` — throttle y steering entre `-100` y `100`, velocidad PWM entre `0` y `255`.
- `L:0` / `L:1` — apaga o enciende el LED.
- `S` — parada inmediata.

Si no llega un comando de conducción durante 500 ms, el ESP32 detiene los motores automáticamente.

## Aplicación Android

El proyecto está en `android/` y tiene mínimo Android 7 (API 24). La aplicación está bloqueada en horizontal y separa responsabilidades en dos pantallas:

- **Dispositivos Bluetooth**: lista dispositivos vinculados/encontrados, selecciona `RoboCar-ESP32`, conecta, desconecta y permite repetir la búsqueda.
- **Control**: dos joysticks grandes; el izquierdo controla solo avance/retroceso, el derecho solo giro, y el botón central controla únicamente el LED D13.

El firmware mezcla ambos ejes para permitir avanzar mientras se gira. Al soltar un joystick vuelve al centro y se envía inmediatamente `D:0:0:250`. La app usa PWM máximo por defecto `250`; el firmware aplica un PWM mínimo de arranque para evitar que los motores solo zumben con valores bajos y calcula PWM separado para ENA y ENB.

Se necesita Android SDK API 35 y Gradle 8.7 o posterior:

```bash
gradle wrapper --gradle-version 8.7
./gradlew :app:assembleDebug
```

El APK se genera en `android/app/build/outputs/apk/debug/app-debug.apk`. En Android 11 o inferior la primera búsqueda requiere permiso de ubicación; en Android 12 o posterior requiere permisos de Bluetooth cercano.

## Uso

1. Carga el firmware al ESP32 y reinícialo.
2. Vincula `RoboCar-ESP32` desde los ajustes Bluetooth del teléfono.
3. Abre la app, pulsa **BUSCAR DISPOSITIVOS**, selecciona el ESP32 y pulsa **CONECTAR AL AUTO**.
4. En la pantalla de control, usa los joysticks; al soltarlos, el control vuelve al centro.

No se usan credenciales de backend ni servicios externos para controlar el auto.
