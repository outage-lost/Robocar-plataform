require('dotenv').config();

const cors = require('cors');
const express = require('express');
const http = require('http');
const path = require('path');
const { Server } = require('socket.io');
const config = require('./config');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: '*', methods: ['GET', 'POST'] },
  pingTimeout: 60000,
  pingInterval: 25000
});

const TOKENS = {
  admin: process.env.TOKEN_ADMIN || 'admin123',
  camera: process.env.TOKEN_CAMERA || 'camera123',
  esp32: process.env.TOKEN_ESP32 || 'esp32123'
};

const STUN_SERVERS = (process.env.STUN_SERVERS || 'stun.l.google.com:19302,stun1.l.google.com:19302')
  .split(',').map(u => ({ urls: `stun:${u.trim()}` }));

const FRONTEND_ROOT = path.join(__dirname, '../../frontend');
const adminNamespace = io.of('/admin');
const cameraNamespace = io.of('/camera');

let adminSocketId = null;
let cameraSocketId = null;

app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// ─── Token helpers ────────────────────────────────────────────────────────────

function getReqToken(req) {
  return req.headers['x-robot-token'] || req.query.token;
}

function getSockToken(socket) {
  return socket.handshake.headers['x-robot-token']
    || socket.handshake.auth?.token
    || socket.handshake.query?.token;
}

function resolveType(token) {
  if (token === TOKENS.admin) return 'admin';
  if (token === TOKENS.camera) return 'camera';
  if (token === TOKENS.esp32) return 'esp32';
  return null;
}

function requireToken(expected) {
  return (req, res, next) => {
    const t = resolveType(getReqToken(req));
    if (!t) return res.status(401).json({ error: 'Token requerido o invalido' });
    if (expected && t !== expected) return res.status(403).json({ error: 'Token no autorizado' });
    req.tokenType = t;
    return next();
  };
}

function nsAuth(expected) {
  return (socket, next) => {
    const t = resolveType(getSockToken(socket));
    if (!t) return next(new Error('Token requerido o invalido'));
    if (t !== expected) return next(new Error('Token no autorizado'));
    socket.tokenType = t;
    return next();
  };
}

// ─── Broadcast helpers ────────────────────────────────────────────────────────

function emitState() {
  const st = config.getState();
  adminNamespace.emit('state:update', st);
  cameraNamespace.emit('state:update', {
    cameraConnected: st.cameraConnected,
    streamActive: st.streamActive,
    microphoneActive: st.microphoneActive,
    flashEnabled: st.flashEnabled,
    adminConnected: st.adminConnected
  });
}

function appendLog(msg) {
  const entry = config.addLog(msg);
  adminNamespace.emit('system:log:append', entry);
}

// ─── REST ─────────────────────────────────────────────────────────────────────

app.get('/api/commands', requireToken('esp32'), (req, res) => {
  const st = config.markEsp32Poll();
  emitState();
  res.json({ command: st.command, speed: st.speed, led: st.led, timestamp: Date.now() });
});

app.get('/api/stun', requireToken(), (req, res) => {
  res.json({ iceServers: STUN_SERVERS });
});

// ─── Static ───────────────────────────────────────────────────────────────────

app.get('/', (req, res) => res.sendFile(path.join(FRONTEND_ROOT, 'admin/index.html')));

// /camera redirect (keeps query params like ?token=xxx)
app.get(/^\/camera$/, (req, res) => {
  const qs = new URLSearchParams(req.query).toString();
  res.redirect('/camera/' + (qs ? '?' + qs : ''));
});

app.use(express.static(path.join(FRONTEND_ROOT, 'admin')));
app.use('/camera', express.static(path.join(FRONTEND_ROOT, 'camera')));

// ─── Namespace auth ───────────────────────────────────────────────────────────

adminNamespace.use(nsAuth('admin'));
cameraNamespace.use(nsAuth('camera'));

// ─── Admin namespace ──────────────────────────────────────────────────────────

adminNamespace.on('connection', socket => {
  adminSocketId = socket.id;
  config.setState('adminConnected', true);
  appendLog('Panel conectado');
  emitState();

  socket.emit('state:update', config.getState());
  socket.emit('system:log', config.getState().systemLog);
  socket.emit('stun:servers', STUN_SERVERS);

  // If camera is already streaming, ask it to resend its offer immediately
  // so the admin receives the video without having to wait for a new stream
  if (cameraSocketId && config.getState().streamActive) {
    cameraNamespace.to(cameraSocketId).emit('webrtc:restart');
    appendLog('Solicitando renegociación WebRTC a la cámara');
  }

  socket.on('command:move', ({ direction, speed }) => {
    const ok = new Set(['stop', 'forward', 'backward', 'left', 'right']);
    config.setCommand(ok.has(direction) ? direction : 'stop', speed);
    emitState();
  });

  socket.on('command:speed', ({ speed }) => { config.setSpeed(speed); emitState(); });

  socket.on('command:led', ({ state }) => {
    config.setState('led', Boolean(state));
    appendLog(`LED D13 ${state ? 'encendido' : 'apagado'}`);
    emitState();
  });

  socket.on('camera:command', ({ command }) => {
    if (!command || !cameraSocketId) return;
    cameraNamespace.to(cameraSocketId).emit('camera:command', { command });
    appendLog(`Comando cámara: ${command}`);
  });

  socket.on('camera:mic-toggle', ({ enabled }) => {
    if (!cameraSocketId) return;
    cameraNamespace.to(cameraSocketId).emit('camera:mic-toggle', { enabled: Boolean(enabled) });
    appendLog(`Micrófono remoto ${enabled ? 'encendido' : 'apagado'}`);
  });

  // WebRTC relay admin → camera
  socket.on('webrtc:answer', ({ answer }) => { if (cameraSocketId && answer) cameraNamespace.to(cameraSocketId).emit('webrtc:answer', { answer }); });
  socket.on('webrtc:ice-candidate', ({ candidate }) => { if (cameraSocketId && candidate) cameraNamespace.to(cameraSocketId).emit('webrtc:ice-candidate', { candidate }); });

  socket.on('disconnect', () => {
    if (adminSocketId !== socket.id) return;
    adminSocketId = null;
    config.setState('adminConnected', false);
    config.resetCommand('Panel desconectado — STOP');
    emitState();
  });
});

// ─── Camera namespace ─────────────────────────────────────────────────────────

cameraNamespace.on('connection', socket => {
  cameraSocketId = socket.id;
  config.setState('cameraConnected', true);
  appendLog('App de cámara conectada');
  emitState();

  socket.emit('state:update', config.getState());
  socket.emit('stun:servers', STUN_SERVERS);

  socket.on('stream:status', ({ active }) => {
    config.setState('streamActive', Boolean(active));
    if (active) config.setState('lastOfferAt', Date.now());
    appendLog(`Stream ${active ? 'iniciado' : 'detenido'}`);
    emitState();
  });

  socket.on('mic:status', ({ active }) => {
    config.setState('microphoneActive', Boolean(active));
    adminNamespace.emit('mic:status', { active: Boolean(active) });
    emitState();
  });

  socket.on('flash:status', ({ active }) => {
    config.setState('flashEnabled', Boolean(active));
    adminNamespace.emit('flash:status', { active: Boolean(active) });
    emitState();
  });

  // WebRTC relay camera → admin
  socket.on('webrtc:offer', ({ offer }) => {
    if (!adminSocketId || !offer) return;
    config.setState('streamActive', true);
    config.setState('lastOfferAt', Date.now());
    adminNamespace.to(adminSocketId).emit('webrtc:offer', { offer });
    emitState();
  });

  socket.on('webrtc:answer', ({ answer }) => { if (adminSocketId && answer) adminNamespace.to(adminSocketId).emit('webrtc:answer', { answer }); });
  socket.on('webrtc:ice-candidate', ({ candidate }) => { if (adminSocketId && candidate) adminNamespace.to(adminSocketId).emit('webrtc:ice-candidate', { candidate }); });

  socket.on('disconnect', () => {
    if (cameraSocketId !== socket.id) return;
    cameraSocketId = null;
    config.setState('cameraConnected', false);
    config.setState('streamActive', false);
    config.setState('microphoneActive', false);
    config.setState('flashEnabled', false);
    appendLog('App de cámara desconectada');
    adminNamespace.emit('mic:status', { active: false });
    adminNamespace.emit('flash:status', { active: false });
    emitState();
  });
});

// ─── ESP32 heartbeat ──────────────────────────────────────────────────────────

setInterval(() => {
  const was = config.getState().esp32Online;
  const now = config.updateESP32Status();
  if (was && !now) config.resetCommand('ESP32 offline — STOP');
  emitState();
}, 1000);

// ─── Start ────────────────────────────────────────────────────────────────────

const PORT = Number(process.env.PORT) || 3000;
server.listen(PORT, () => {
  console.log(`\n🚗  RoboCar → http://0.0.0.0:${PORT}`);
  console.log(`   Admin:  http://localhost:${PORT}/`);
  console.log(`   Cámara: http://localhost:${PORT}/camera\n`);
});

module.exports = { app, server, io, TOKENS, STUN_SERVERS };
