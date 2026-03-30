# RoboCar Platform

Guía de instalación y ejecución para una plataforma de control remoto de un robot con ESP32, panel web de administración y app web de cámara para teléfono.

Repositorio oficial:

- GitHub: `https://github.com/outage-lost/Robocar-plataform`
- Clonar: `git clone https://github.com/outage-lost/Robocar-plataform.git`

## 1. Descripción general

El proyecto está compuesto por tres piezas:

- un backend Node.js + Express + Socket.IO
- un panel administrativo web en `/`
- una app de cámara web en `/camera`

El backend se encarga de:

- servir el frontend
- autenticar tokens
- mantener el estado del robot en memoria
- coordinar la señalización WebRTC
- exponer la API que consulta el ESP32

## 2. Requisitos

Necesitas:

- Node.js 20 o superior
- npm 10 o superior
- un navegador moderno
- un teléfono con cámara y micrófono
- una forma de exponer el proyecto por HTTPS si la cámara se usará desde otro dispositivo

## 3. Requisito crítico: HTTPS o túnel inverso

La app `/camera` usa `getUserMedia`, así que el navegador solo permitirá permisos de cámara y micrófono en estos casos:

- `https://`
- `http://localhost`

Eso significa que si abres la cámara desde un teléfono apuntando a una IP local con `http://192.168.x.x:3000`, lo normal es que el navegador bloquee los permisos.

Para que el proyecto funcione correctamente en un flujo real, debes usar una de estas opciones:

- servir la app por HTTPS
- montar un túnel inverso o túnel público HTTPS
- probar la cámara únicamente en `localhost`

En este repositorio ya queda una plantilla de túnel en [backend/cloudflared-robocar.yml](/home/joel/Escritorio/another-joel/work-in-b06labs/Projects/MicrocontrollerProjects/robocar-platform/backend/cloudflared-robocar.yml), pero debes reemplazar tus propios valores.

## 4. Clonar el repositorio

```bash
git clone https://github.com/outage-lost/Robocar-plataform.git
cd Robocar-plataform
```

## 5. Estructura del proyecto

```text
Robocar-plataform/
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

Archivos clave:

- backend principal: [backend/src/server.js](/home/joel/Escritorio/another-joel/work-in-b06labs/Projects/MicrocontrollerProjects/robocar-platform/backend/src/server.js)
- estado global: [backend/src/config.js](/home/joel/Escritorio/another-joel/work-in-b06labs/Projects/MicrocontrollerProjects/robocar-platform/backend/src/config.js)
- panel admin: [frontend/admin/admin.js](/home/joel/Escritorio/another-joel/work-in-b06labs/Projects/MicrocontrollerProjects/robocar-platform/frontend/admin/admin.js)
- app cámara: [frontend/camera/camera.js](/home/joel/Escritorio/another-joel/work-in-b06labs/Projects/MicrocontrollerProjects/robocar-platform/frontend/camera/camera.js)
- referencia ESP32: [ESP32_REFERENCE.ino](/home/joel/Escritorio/another-joel/work-in-b06labs/Projects/MicrocontrollerProjects/robocar-platform/ESP32_REFERENCE.ino)

## 6. Instalación

### 6.1 Instalar dependencias

```bash
cd backend
npm ci
```

### 6.2 Crear el archivo de entorno

```bash
cp .env.example .env
```

Edita `backend/.env` con tus propios valores:

```env
PORT=3000
NODE_ENV=development
TOKEN_ADMIN=define_un_token_seguro_para_admin
TOKEN_CAMERA=define_un_token_seguro_para_camera
TOKEN_ESP32=define_un_token_seguro_para_esp32
STUN_SERVERS=stun.l.google.com:19302,stun1.l.google.com:19302
```

Variables:

| Variable | Requerida | Uso |
|---|---|---|
| `PORT` | No | Puerto HTTP del backend |
| `NODE_ENV` | No | Entorno de ejecución |
| `TOKEN_ADMIN` | Sí | Acceso al panel `/` y namespace `/admin` |
| `TOKEN_CAMERA` | Sí | Acceso a `/camera` y namespace `/camera` |
| `TOKEN_ESP32` | Sí | Autenticación para `GET /api/commands` |
| `STUN_SERVERS` | No | Servidores STUN separados por coma |

No publiques ni despliegues con los tokens por defecto del backend.

## 7. Ejecución local

Desde `backend/`:

```bash
npm run dev
```

O en modo simple:

```bash
npm start
```

URLs de desarrollo:

- panel: `http://localhost:3000/`
- cámara: `http://localhost:3000/camera/`
- API ESP32: `http://localhost:3000/api/commands`

## 8. Ejecución con HTTPS o túnel

Si vas a abrir la app de cámara desde el teléfono, usa HTTPS. Tienes varias opciones:

### Opción A: túnel inverso / público HTTPS

Puedes usar Cloudflare Tunnel u otra solución equivalente. Este repo incluye una plantilla:

- [backend/cloudflared-robocar.yml](/home/joel/Escritorio/another-joel/work-in-b06labs/Projects/MicrocontrollerProjects/robocar-platform/backend/cloudflared-robocar.yml)

Debes reemplazar:

- `YOUR_TUNNEL_ID`
- `robocar.example.com`
- la ruta local del archivo de credenciales

### Opción B: reverse proxy con HTTPS

También puedes exponer el backend detrás de:

- Nginx con TLS
- Caddy
- Traefik

Mientras el teléfono abra la app por `https://`, los permisos de cámara y micrófono funcionarán.

## 9. Flujo de uso

### 9.1 Panel administrativo

1. Abre `http://localhost:3000/` o tu dominio HTTPS.
2. Ingresa `TOKEN_ADMIN`.
3. Usa el panel para mover el robot, ajustar velocidad, controlar LED y gestionar cámara/micrófono.

También puedes entrar con token en la URL:

```text
http://localhost:3000/?token=TOKEN_ADMIN
```

### 9.2 App de cámara

1. Abre `/camera/` desde el teléfono.
2. Asegúrate de estar en `https://` o `localhost`.
3. Ingresa `TOKEN_CAMERA`.
4. Acepta permisos de cámara y micrófono.
5. Pulsa `Transmitir`.

También puedes abrirla con token:

```text
https://tu-dominio/camera/?token=TOKEN_CAMERA
```

### 9.3 ESP32

El ESP32 debe consultar periódicamente:

```http
GET /api/commands
X-Robot-Token: TOKEN_ESP32
```

Respuesta esperada:

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

La referencia del firmware está en [ESP32_REFERENCE.ino](/home/joel/Escritorio/another-joel/work-in-b06labs/Projects/MicrocontrollerProjects/robocar-platform/ESP32_REFERENCE.ino). Debes configurar:

- `ssid`
- `password`
- `serverURL`
- `robotToken`

Ejemplo:

```cpp
const char* serverURL = "http://192.168.1.50:3000/api/commands";
const char* robotToken = "TU_TOKEN_ESP32";
```

## 10. API disponible

### `GET /api/commands`

Requiere `TOKEN_ESP32`.

Autenticación aceptada:

- header `X-Robot-Token`
- query string `?token=...`

Ejemplo:

```bash
curl -H "X-Robot-Token: $TOKEN_ESP32" http://localhost:3000/api/commands
```

### `GET /api/stun`

Requiere cualquier token válido del sistema.

Ejemplo:

```bash
curl -H "X-Robot-Token: $TOKEN_ADMIN" http://localhost:3000/api/stun
```

## 11. Eventos Socket.IO

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

## 12. Docker

El proyecto incluye [Dockerfile](/home/joel/Escritorio/another-joel/work-in-b06labs/Projects/MicrocontrollerProjects/robocar-platform/Dockerfile).

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

Si vas a usar la cámara desde un teléfono, recuerda que Docker por sí solo no resuelve el requisito de HTTPS. Necesitas además un túnel o un proxy TLS.

## 13. Prueba rápida

Con el backend corriendo, desde `backend/`:

```bash
node test-socket.js
```

También puedes pasar host, puerto y tokens:

```bash
TEST_HOST=http://localhost:3100 PORT=3100 TOKEN_ADMIN=... TOKEN_CAMERA=... node test-socket.js
```

## 14. Consideraciones de publicación

Este repo ya está preparado para subirse sin incluir:

- `backend/.env`
- `backend/node_modules/`

Antes de trabajar en producción:

- usa tokens propios
- usa HTTPS real o un túnel inverso
- ajusta el firmware ESP32 a tus pines reales

## 15. Limitaciones actuales

- el estado es efímero y se pierde al reiniciar el backend
- no hay base de datos
- no hay suite formal de tests en `package.json`
- el firmware ESP32 es una referencia y puede requerir ajustes

## 16. Licencia

Si vas a distribuir públicamente el proyecto, añade una licencia explícita al repositorio.
