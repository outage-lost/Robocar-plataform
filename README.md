# RoboCar Platform

Plataforma web para controlar un carro robot con ESP32, panel administrativo en tiempo real y una app de cámara para teléfono. El backend centraliza autenticación, estado, señalización WebRTC y la API de polling que consume el microcontrolador.

El proyecto quedó saneado para publicarlo:

- `backend/.env` no se sube
- `backend/node_modules/` no se sube
- no quedan dominios privados ni tokens reales en la documentación
- los archivos de ejemplo usan placeholders

## Qué incluye

- panel de administración en `/`
- app de cámara en `/camera`
- backend Node.js + Express + Socket.IO
- streaming WebRTC de video y audio entre teléfono y panel
- intercom desde el panel hacia el teléfono
- API `GET /api/commands` para polling del ESP32
- referencia de firmware en `ESP32_REFERENCE.ino`
- plantilla de Cloudflare Tunnel en `backend/cloudflared-robocar.yml`

## Arquitectura

### Backend

Código principal:

- [backend/src/server.js](/home/joel/Escritorio/another-joel/work-in-b06labs/Projects/MicrocontrollerProjects/robocar-platform/backend/src/server.js)
- [backend/src/config.js](/home/joel/Escritorio/another-joel/work-in-b06labs/Projects/MicrocontrollerProjects/robocar-platform/backend/src/config.js)

Responsabilidades:

- servir el panel y la app de cámara
- autenticar tokens por `X-Robot-Token`, `auth.token` o `?token=...`
- exponer `GET /api/commands` para el ESP32
- exponer `GET /api/stun` para clientes autenticados
- mantener estado global en memoria
- retransmitir señalización WebRTC entre `/admin` y `/camera`
- aplicar fail-safe con `STOP` si el panel se desconecta o el ESP32 deja de hacer polling

### Frontend admin

Archivos:

- [frontend/admin/index.html](/home/joel/Escritorio/another-joel/work-in-b06labs/Projects/MicrocontrollerProjects/robocar-platform/frontend/admin/index.html)
- [frontend/admin/admin.js](/home/joel/Escritorio/another-joel/work-in-b06labs/Projects/MicrocontrollerProjects/robocar-platform/frontend/admin/admin.js)

Funciones:

- autenticación con `TOKEN_ADMIN`
- joystick y control por teclado
- ajuste de velocidad PWM
- control de LED
- control remoto de cámara, flash y micrófono
- recepción de video y audio desde el teléfono
- intercom del panel hacia el teléfono
- log del sistema en tiempo real

### Frontend cámara

Archivos:

- [frontend/camera/index.html](/home/joel/Escritorio/another-joel/work-in-b06labs/Projects/MicrocontrollerProjects/robocar-platform/frontend/camera/index.html)
- [frontend/camera/camera.js](/home/joel/Escritorio/another-joel/work-in-b06labs/Projects/MicrocontrollerProjects/robocar-platform/frontend/camera/camera.js)

Funciones:

- autenticación con `TOKEN_CAMERA`
- vista previa local antes de transmitir
- captura de video y audio con `getUserMedia`
- streaming WebRTC hacia el panel
- reproducción de audio remoto del panel
- cambio de cámara y control de flash
- renegociación cuando el panel se reconecta

### ESP32

Referencia:

- [ESP32_REFERENCE.ino](/home/joel/Escritorio/another-joel/work-in-b06labs/Projects/MicrocontrollerProjects/robocar-platform/ESP32_REFERENCE.ino)

El ESP32 consulta:

```http
GET /api/commands
X-Robot-Token: TOKEN_ESP32
```

Respuesta típica:

```json
{
  "command": "forward",
  "speed": 128,
  "led": false,
  "timestamp": 1711111111111
}
```

Comandos válidos:

- `stop`
- `forward`
- `backward`
- `left`
- `right`

El backend marca al ESP32 como offline tras 2000 ms sin polling. La referencia usa 100 ms, que es coherente con esa ventana.

## Estructura del proyecto

```text
robocar-platform/
├── .dockerignore
├── .gitignore
├── Dockerfile
├── ESP32_REFERENCE.ino
├── README.md
├── backend/
│   ├── .env.example
│   ├── cloudflared-robocar.yml
│   ├── package-lock.json
│   ├── package.json
│   ├── src/
│   │   ├── config.js
│   │   └── server.js
│   └── test-socket.js
└── frontend/
    ├── admin/
    │   ├── admin.js
    │   ├── index.html
    │   └── styles.css
    └── camera/
        ├── camera.js
        ├── index.html
        └── styles.css
```

## Requisitos

- Node.js 20 o superior
- npm 10 o superior
- navegador moderno
- HTTPS o `localhost` para usar cámara y micrófono en `/camera`

## Instalación local

```bash
git clone <TU_REPO_GITHUB>
cd robocar-platform
cd backend
npm ci
cp .env.example .env
```

Edita `backend/.env`:

```env
PORT=3000
NODE_ENV=development
TOKEN_ADMIN=define_un_token_seguro_para_admin
TOKEN_CAMERA=define_un_token_seguro_para_camera
TOKEN_ESP32=define_un_token_seguro_para_esp32
STUN_SERVERS=stun.l.google.com:19302,stun1.l.google.com:19302
```

## Ejecución

Desde `backend/`:

```bash
npm run dev
```

O:

```bash
npm start
```

URLs locales:

- panel: `http://localhost:3000/`
- cámara: `http://localhost:3000/camera/`
- API ESP32: `http://localhost:3000/api/commands`

## Uso

### Panel administrativo

1. Abre `http://localhost:3000/`
2. Ingresa `TOKEN_ADMIN`
3. Usa los controles de movimiento, velocidad, LED y cámara

También puedes abrirlo con token:

```text
http://localhost:3000/?token=TOKEN_ADMIN
```

### App de cámara

1. Abre `http://localhost:3000/camera/` desde el teléfono
2. Usa HTTPS o `localhost`
3. Ingresa `TOKEN_CAMERA`
4. Acepta permisos de cámara y micrófono
5. Pulsa `Transmitir`

También puedes abrirla con token:

```text
http://localhost:3000/camera/?token=TOKEN_CAMERA
```

### ESP32

Configura en [ESP32_REFERENCE.ino](/home/joel/Escritorio/another-joel/work-in-b06labs/Projects/MicrocontrollerProjects/robocar-platform/ESP32_REFERENCE.ino):

- `ssid`
- `password`
- `serverURL`
- `robotToken`

Ejemplo:

```cpp
const char* serverURL = "http://192.168.1.50:3000/api/commands";
const char* robotToken = "TU_TOKEN_ESP32";
```

## Variables de entorno

Archivo base: [backend/.env.example](/home/joel/Escritorio/another-joel/work-in-b06labs/Projects/MicrocontrollerProjects/robocar-platform/backend/.env.example)

| Variable | Requerida | Descripción |
|---|---|---|
| `PORT` | No | Puerto HTTP del backend |
| `NODE_ENV` | No | Entorno de ejecución |
| `TOKEN_ADMIN` | Sí | Token del panel y namespace `/admin` |
| `TOKEN_CAMERA` | Sí | Token de la app y namespace `/camera` |
| `TOKEN_ESP32` | Sí | Token para `GET /api/commands` |
| `STUN_SERVERS` | No | Lista separada por comas de servidores STUN |

Si no defines tokens, el backend usa defaults inseguros:

- `admin123`
- `camera123`
- `esp32123`

No publiques ni despliegues con esos valores.

## API HTTP

### `GET /api/commands`

Requiere `TOKEN_ESP32`.

Autenticación aceptada:

- `X-Robot-Token`
- `?token=...`

Ejemplo:

```bash
curl -H "X-Robot-Token: $TOKEN_ESP32" http://localhost:3000/api/commands
```

### `GET /api/stun`

Requiere cualquier token válido del sistema.

```bash
curl -H "X-Robot-Token: $TOKEN_ADMIN" http://localhost:3000/api/stun
```

## Eventos Socket.IO

### Namespace `/admin`

Emite:

- `command:move`
- `command:speed`
- `command:led`
- `camera:command`
- `camera:mic-toggle`
- `webrtc:answer`
- `webrtc:ice-candidate`

Recibe:

- `state:update`
- `system:log`
- `system:log:append`
- `mic:status`
- `flash:status`
- `webrtc:offer`
- `webrtc:ice-candidate`
- `stun:servers`

### Namespace `/camera`

Emite:

- `stream:status`
- `mic:status`
- `flash:status`
- `webrtc:offer`
- `webrtc:ice-candidate`

Recibe:

- `camera:command`
- `camera:mic-toggle`
- `webrtc:answer`
- `webrtc:ice-candidate`
- `webrtc:restart`
- `stun:servers`

## Docker

Imagen incluida en [Dockerfile](/home/joel/Escritorio/another-joel/work-in-b06labs/Projects/MicrocontrollerProjects/robocar-platform/Dockerfile).

Build:

```bash
docker build -t robocar-platform .
```

Run:

```bash
docker run --rm -p 3100:3100 \
  -e PORT=3100 \
  -e TOKEN_ADMIN=define_admin \
  -e TOKEN_CAMERA=define_camera \
  -e TOKEN_ESP32=define_esp32 \
  robocar-platform
```

## Cloudflare Tunnel

La plantilla está en [backend/cloudflared-robocar.yml](/home/joel/Escritorio/another-joel/work-in-b06labs/Projects/MicrocontrollerProjects/robocar-platform/backend/cloudflared-robocar.yml). Debes reemplazar:

- `YOUR_TUNNEL_ID`
- `/home/USER/.cloudflared/...`
- `robocar.example.com`

## Publicación en GitHub

Flujo sugerido:

```bash
git init
git add .
git status
git commit -m "Initial commit"
git branch -M main
git remote add origin <TU_URL_GITHUB>
git push -u origin main
```

Antes de hacer `git add .`, verifica:

- `backend/.env` contiene tus secretos locales y no debe subirse
- `backend/node_modules/` no debe subirse
- si generaste otros archivos de credenciales, agrégalos al `.gitignore`

## Prueba rápida

Con el backend corriendo, desde `backend/`:

```bash
node test-socket.js
```

También puedes sobrescribir host, puerto y tokens:

```bash
TEST_HOST=http://localhost:3100 PORT=3100 TOKEN_ADMIN=... TOKEN_CAMERA=... node test-socket.js
```

## Limitaciones actuales

- el estado es efímero y se pierde al reiniciar
- no hay base de datos
- no hay suite formal de tests en `package.json`
- el firmware ESP32 es una referencia y puede requerir ajuste de pines o librerías
- fuera de `localhost`, la app de cámara necesita HTTPS

## Licencia

Si vas a publicar el repositorio, añade una licencia antes del push si quieres dejar claras las condiciones de uso.
