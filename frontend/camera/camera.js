const S = {
  token: '',
  socket: null,
  pc: null,
  videoSender: null,
  audioSender: null,
  stream: null,
  primaryStream: null,
  secondaryStream: null,
  previewStream: null,
  videoTrack: null,
  audioStream: null,
  audioTrack: null,
  remoteAdminStream: null,
  facingMode: 'environment',
  streaming: false,
  micEnabled: true,
  restartPending: false,
  flashOn: false,
  dualMode: false,
  pendingCandidates: [],
  composeCanvas: null,
  composeContext: null,
  composeVideoA: null,
  composeVideoB: null,
  composeFrame: null,
  iceServers: [
    { urls: 'stun:stun.l.google.com:19302' },
    { urls: 'stun:stun1.l.google.com:19302' }
  ]
};

const KEY_TOKEN = 'rc_camera_token';
const VIDEO_CONSTRAINTS = {
  width: { ideal: 1920, min: 640, max: 1920 },
  height: { ideal: 1080, min: 480, max: 1080 },
  frameRate: { ideal: 30, min: 20, max: 30 }
};

const AUDIO_CONSTRAINTS = {
  echoCancellation: true,
  noiseSuppression: true,
  autoGainControl: true
};

const MAX_STREAM_RETRIES = 3;
const $ = (id) => document.getElementById(id);

document.addEventListener('DOMContentLoaded', async () => {
  bindUI();
  $('remoteAdminAudio').muted = false;
  $('remoteAdminAudio').volume = 1;

  const urlToken = new URLSearchParams(window.location.search).get('token') || localStorage.getItem(KEY_TOKEN) || '';
  if (urlToken) {
    $('tokenInput').value = urlToken;
    S.token = urlToken;
    localStorage.setItem(KEY_TOKEN, urlToken);
    await boot();
  }
});

function bindUI() {
  $('loginForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const token = $('tokenInput').value.trim();
    if (!token) {
      showError('Ingresa TOKEN_CAMERA');
      return;
    }

    S.token = token;
    localStorage.setItem(KEY_TOKEN, token);
    await boot();
  });

  $('btnFlip').addEventListener('click', async () => {
    try {
      await flipCamera();
    } catch (error) {
      showInfo(`Cambio de cámara: ${error.message}`);
    }
  });

  $('btnStream').addEventListener('click', async () => {
    if (S.streaming) {
      stopStream(true);
      return;
    }

    await startStream();
  });

  $('btnFlash').addEventListener('click', async () => {
    try {
      await toggleFlash();
    } catch (error) {
      showInfo(`Flash: ${error.message}`);
    }
  });

  $('btnMic').addEventListener('click', async () => {
    await toggleMic();
  });

  document.addEventListener('visibilitychange', () => {
    if (S.videoTrack) {
      S.videoTrack.enabled = !document.hidden;
    }
  });

  applyMicUi();
  updateFlashUi();
}

async function boot() {
  try {
    ensureSecureContext();
    await startPreview();
    connectSocket();
    $('loginScreen').classList.remove('screen-active');
    $('cameraApp').classList.add('app-active');
    showError('');
    applyMicUi();
    showInfo('Vista previa lista');
  } catch (error) {
    showError(error.message || 'No se pudo iniciar la cámara');
  }
}

function ensureSecureContext() {
  if (!window.isSecureContext) {
    throw new Error('Requiere HTTPS o localhost');
  }

  if (!navigator.mediaDevices?.getUserMedia) {
    throw new Error('getUserMedia no disponible');
  }
}

async function startPreview() {
  await stopPreview();

  if (S.dualMode) {
    await startDualPreview();
    return;
  }

  await startSinglePreview();
}

async function startSinglePreview() {
  const stream = await navigator.mediaDevices.getUserMedia({
    video: {
      facingMode: { ideal: S.facingMode },
      ...VIDEO_CONSTRAINTS
    },
    audio: false
  });

  const track = stream.getVideoTracks()[0];
  if (!track) {
    throw new Error('No se pudo obtener video');
  }

  track.contentHint = 'detail';
  S.primaryStream = stream;
  S.stream = stream;
  S.previewStream = stream;
  S.videoTrack = track;
  S.flashOn = false;
  updateFlashUi();
  emit('flash:status', { active: false });

  $('cameraPreview').srcObject = stream;
  await $('cameraPreview').play().catch(() => {});
}

async function startDualPreview() {
  const rear = await navigator.mediaDevices.getUserMedia({
    video: {
      facingMode: { exact: 'environment' },
      ...VIDEO_CONSTRAINTS
    },
    audio: false
  });

  let front;
  try {
    front = await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: { exact: 'user' },
        ...VIDEO_CONSTRAINTS
      },
      audio: false
    });
  } catch (error) {
    rear.getTracks().forEach((track) => track.stop());
    throw new Error('Este teléfono no permite dos cámaras simultáneas');
  }

  const rearTrack = rear.getVideoTracks()[0];
  if (!rearTrack) {
    rear.getTracks().forEach((track) => track.stop());
    front.getTracks().forEach((track) => track.stop());
    throw new Error('No se pudo obtener video de ambas cámaras');
  }

  S.primaryStream = rear;
  S.secondaryStream = front;
  S.facingMode = 'environment';
  S.flashOn = false;
  updateFlashUi();
  emit('flash:status', { active: false });

  const composedTrack = await buildCompositeTrack(rear, front);
  S.stream = new MediaStream([composedTrack]);
  S.previewStream = S.stream;
  S.videoTrack = composedTrack;
  S.videoTrack.contentHint = 'detail';

  $('cameraPreview').srcObject = S.stream;
  await $('cameraPreview').play().catch(() => {});
}

async function buildCompositeTrack(rearStream, frontStream) {
  const rearVideo = document.createElement('video');
  const frontVideo = document.createElement('video');
  rearVideo.srcObject = rearStream;
  frontVideo.srcObject = frontStream;
  rearVideo.muted = true;
  frontVideo.muted = true;
  rearVideo.playsInline = true;
  frontVideo.playsInline = true;

  await Promise.all([
    rearVideo.play().catch(() => {}),
    frontVideo.play().catch(() => {})
  ]);

  const canvas = document.createElement('canvas');
  canvas.width = 720;
  canvas.height = 1280;

  const context = canvas.getContext('2d', { alpha: false });
  if (!context) {
    throw new Error('No se pudo inicializar la composición de video');
  }

  S.composeCanvas = canvas;
  S.composeContext = context;
  S.composeVideoA = rearVideo;
  S.composeVideoB = frontVideo;

  const draw = () => {
    if (!S.composeContext || !S.composeCanvas || !S.composeVideoA || !S.composeVideoB) {
      return;
    }

    const halfHeight = S.composeCanvas.height / 2;
    S.composeContext.fillStyle = '#000';
    S.composeContext.fillRect(0, 0, S.composeCanvas.width, S.composeCanvas.height);
    drawFitted(S.composeContext, S.composeVideoA, 0, 0, S.composeCanvas.width, halfHeight);
    drawFitted(S.composeContext, S.composeVideoB, 0, halfHeight, S.composeCanvas.width, halfHeight);
    S.composeFrame = window.requestAnimationFrame(draw);
  };

  draw();

  const stream = canvas.captureStream(24);
  const track = stream.getVideoTracks()[0];
  if (!track) {
    throw new Error('No se pudo capturar la vista doble');
  }

  return track;
}

function drawFitted(context, video, x, y, width, height) {
  const sourceWidth = video.videoWidth || width;
  const sourceHeight = video.videoHeight || height;
  const scale = Math.min(width / sourceWidth, height / sourceHeight);
  const drawWidth = sourceWidth * scale;
  const drawHeight = sourceHeight * scale;
  const offsetX = x + (width - drawWidth) / 2;
  const offsetY = y + (height - drawHeight) / 2;
  context.drawImage(video, offsetX, offsetY, drawWidth, drawHeight);
}

async function stopPreview() {
  stopComposite();

  [S.primaryStream, S.secondaryStream, S.stream].forEach((stream) => {
    stream?.getTracks().forEach((track) => track.stop());
  });

  S.primaryStream = null;
  S.secondaryStream = null;
  S.stream = null;
  S.previewStream = null;
  S.videoTrack = null;
  $('cameraPreview').srcObject = null;
}

async function ensureAudioCapture() {
  if (S.audioTrack && S.audioTrack.readyState === 'live') {
    return;
  }

  S.audioStream?.getTracks().forEach((track) => track.stop());
  S.audioStream = null;
  S.audioTrack = null;

  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      video: false,
      audio: AUDIO_CONSTRAINTS
    });
    S.audioStream = stream;
    S.audioTrack = stream.getAudioTracks()[0] || null;
    if (S.audioTrack) {
      S.audioTrack.enabled = S.micEnabled;
    }
  } catch (error) {
    showInfo('Micrófono no disponible; transmitiendo solo video');
  }
}

function stopComposite() {
  if (S.composeFrame) {
    cancelAnimationFrame(S.composeFrame);
    S.composeFrame = null;
  }

  [S.composeVideoA, S.composeVideoB].forEach((video) => {
    if (video) {
      video.pause();
      video.srcObject = null;
    }
  });

  S.composeCanvas = null;
  S.composeContext = null;
  S.composeVideoA = null;
  S.composeVideoB = null;
}

function connectSocket() {
  if (S.socket) {
    S.socket.disconnect();
  }

  const socket = io('/camera', {
    auth: { token: S.token },
    query: { token: S.token },
    transports: ['websocket', 'polling']
  });

  socket.on('connect', () => {
    S.socket = socket;
    setSocketStatus(true);
    emit('mic:status', { active: S.micEnabled });
    emit('flash:status', { active: S.flashOn });
    showInfo('Conectado al servidor');
  });

  socket.on('disconnect', () => {
    setSocketStatus(false);
    stopStream(false);
    showInfo('Desconectado');
  });

  socket.on('connect_error', (error) => showError(error.message || 'No se pudo conectar'));
  socket.on('stun:servers', (servers) => {
    if (Array.isArray(servers) && servers.length) {
      S.iceServers = servers;
    }
  });
  socket.on('webrtc:answer', ({ answer }) => handleAnswer(answer));
  socket.on('webrtc:ice-candidate', ({ candidate }) => handleIceCandidate(candidate));
  socket.on('webrtc:restart', () => {
    if (S.streaming) {
      restartStream();
    }
  });
  socket.on('camera:command', ({ command }) => handleRemoteCommand(command));
  socket.on('camera:mic-toggle', async ({ enabled }) => {
    await setMicState(Boolean(enabled));
  });
}

function emit(eventName, payload) {
  if (S.socket?.connected) {
    S.socket.emit(eventName, payload);
  }
}

async function startStream(retry = 0) {
  try {
    if (!S.videoTrack || S.videoTrack.readyState === 'ended') {
      await startPreview();
    }

    await ensureAudioCapture();

    closePeer();

    const pc = new RTCPeerConnection({
      iceServers: S.iceServers,
      bundlePolicy: 'max-bundle',
      rtcpMuxPolicy: 'require'
    });

    S.pc = pc;
    S.pendingCandidates = [];
    S.restartPending = false;
    S.videoSender = pc.addTransceiver('video', { direction: 'sendonly' }).sender;
    await S.videoSender.replaceTrack(S.videoTrack);
    S.audioSender = pc.addTransceiver('audio', { direction: 'sendrecv' }).sender;
    await S.audioSender.replaceTrack(S.audioTrack || null).catch(() => {});
    await tuneVideoSender();
    S.remoteAdminStream = new MediaStream();

    pc.ontrack = (event) => {
      if (event.track.kind !== 'audio') {
        return;
      }

      if (!S.remoteAdminStream.getTracks().find((track) => track.id === event.track.id)) {
        S.remoteAdminStream.addTrack(event.track);
      }
      $('remoteAdminAudio').srcObject = S.remoteAdminStream;
      $('remoteAdminAudio').muted = false;
      $('remoteAdminAudio').volume = 1;
      $('remoteAdminAudio').play().catch(() => {});
    };

    pc.onicecandidate = ({ candidate }) => {
      if (candidate) {
        emit('webrtc:ice-candidate', { candidate });
      }
    };

    pc.onconnectionstatechange = () => {
      if (pc.connectionState === 'connected') {
        showInfo('Transmisión activa');
      }

      if (pc.connectionState === 'failed') {
        showInfo('Reconectando transmisión');
        restartStream();
      }
    };

    const offer = await pc.createOffer({
      offerToReceiveVideo: false,
      offerToReceiveAudio: true
    });
    await pc.setLocalDescription(offer);
    emit('webrtc:offer', { offer: pc.localDescription });
    emit('stream:status', { active: true });

    S.streaming = true;
    $('btnStream').classList.add('is-active');
    $('btnStream').querySelector('.btn-label').textContent = 'Detener';
    showInfo('Iniciando transmisión');
  } catch (error) {
    closePeer();

    if (retry < MAX_STREAM_RETRIES) {
      await wait(500 + retry * 250);
      return startStream(retry + 1);
    }

    S.streaming = false;
    $('btnStream').classList.remove('is-active');
    $('btnStream').querySelector('.btn-label').textContent = 'Transmitir';
    showInfo(`No se pudo transmitir: ${error.message}`);
  }
}

async function handleAnswer(answer) {
  if (!S.pc || !answer) {
    return;
  }

  try {
    await S.pc.setRemoteDescription(new RTCSessionDescription(answer));
    await flushPendingCandidates();
    showInfo('Panel enlazado');
  } catch (error) {
    showInfo(`Respuesta WebRTC: ${error.message}`);
  }
}

async function handleIceCandidate(candidate) {
  if (!candidate) {
    return;
  }

  if (!S.pc || !S.pc.remoteDescription) {
    S.pendingCandidates.push(candidate);
    return;
  }

  try {
    await S.pc.addIceCandidate(new RTCIceCandidate(candidate));
  } catch {}
}

async function flushPendingCandidates() {
  if (!S.pc || !S.pc.remoteDescription) {
    return;
  }

  while (S.pendingCandidates.length) {
    const candidate = S.pendingCandidates.shift();
    try {
      await S.pc.addIceCandidate(new RTCIceCandidate(candidate));
    } catch {}
  }
}

function stopStream(notify) {
  if (!S.streaming && !S.pc) {
    return;
  }

  closePeer(notify);
  stopAudioCapture();
  S.streaming = false;
  S.restartPending = false;
  $('btnStream').classList.remove('is-active');
  $('btnStream').querySelector('.btn-label').textContent = 'Transmitir';
  showInfo('Transmisión detenida');
}

function closePeer(notify = false) {
  if (S.pc) {
    S.pc.onicecandidate = null;
    S.pc.onconnectionstatechange = null;
    S.pc.close();
    S.pc = null;
  }

  S.videoSender = null;
  S.audioSender = null;
  S.remoteAdminStream = null;
  S.pendingCandidates = [];
  $('remoteAdminAudio').srcObject = null;

  if (notify) {
    emit('stream:status', { active: false });
  }
}

function stopAudioCapture() {
  S.audioStream?.getTracks().forEach((track) => track.stop());
  S.audioStream = null;
  S.audioTrack = null;
}

async function toggleMic() {
  await setMicState(!S.micEnabled);
}

function applyMicUi() {
  const button = $('btnMic');
  if (!button) {
    return;
  }

  button.classList.toggle('is-active', S.micEnabled);
  button.querySelector('.btn-label').textContent = S.micEnabled
    ? 'Micrófono encendido'
    : 'Micrófono apagado';
}

function updateFlashUi() {
  const button = $('btnFlash');
  if (!button) {
    return;
  }

  button.classList.toggle('is-active', S.flashOn);
  button.querySelector('.btn-label').textContent = S.flashOn ? 'Flash encendido' : 'Flash apagado';
}

async function setMicState(enabled) {
  S.micEnabled = Boolean(enabled);

  if (S.micEnabled) {
    await ensureAudioCapture();
    if (!S.audioTrack) {
      S.micEnabled = false;
      applyMicUi();
      emit('mic:status', { active: false });
      showInfo('No se pudo activar el micrófono');
      return;
    }

    S.audioTrack.enabled = true;
    if (S.audioSender) {
      await S.audioSender.replaceTrack(S.audioTrack).catch(() => {});
    }
    showInfo('Micrófono encendido');
  } else {
    if (S.audioTrack) {
      S.audioTrack.enabled = false;
    }
    showInfo('Micrófono apagado');
  }

  applyMicUi();
  emit('mic:status', { active: S.micEnabled });
}

async function tuneVideoSender() {
  if (!S.videoSender) {
    return;
  }

  const params = S.videoSender.getParameters();
  if (!params.encodings?.length) {
    params.encodings = [{}];
  }

  params.encodings[0].maxBitrate = 8_000_000;
  params.encodings[0].maxFramerate = 30;
  params.degradationPreference = 'maintain-resolution';

  await S.videoSender.setParameters(params).catch(() => {});
}

async function flipCamera() {
  const wasStreaming = S.streaming;
  if (S.dualMode) {
    showInfo('Desactiva vista doble para cambiar de cámara');
    return;
  }

  if (wasStreaming) {
    stopStream(false);
  }

  S.facingMode = S.facingMode === 'environment' ? 'user' : 'environment';
  await startPreview();

  if (wasStreaming) {
    await startStream();
  }

  showInfo(S.facingMode === 'environment' ? 'Cámara trasera' : 'Cámara frontal');
}

async function toggleFlash() {
  if (S.dualMode) {
    showInfo('Flash no disponible en vista doble');
    return;
  }

  const track = S.primaryStream?.getVideoTracks?.()[0];
  if (!track) {
    showInfo('No hay cámara activa');
    return;
  }

  const capabilities = track.getCapabilities?.();
  if (!capabilities?.torch) {
    showInfo('Torch no soportado en este teléfono');
    return;
  }

  S.flashOn = !S.flashOn;
  try {
    await track.applyConstraints({ advanced: [{ torch: S.flashOn }] });
  } catch (primaryError) {
    try {
      await track.applyConstraints({ advanced: [{ fillLightMode: S.flashOn ? 'flash' : 'off' }] });
    } catch {
      S.flashOn = !S.flashOn;
      showInfo(primaryError.message || 'No se pudo cambiar el flash');
      return;
    }
  }

  updateFlashUi();
  emit('flash:status', { active: S.flashOn });
  showInfo(S.flashOn ? 'Flash encendido' : 'Flash apagado');
}

async function handleRemoteCommand(command) {
  if (command === 'flip') {
    await flipCamera();
    return;
  }

  if (command === 'flash') {
    await toggleFlash();
    return;
  }

  if (command === 'dual') {
    await toggleDualMode();
  }
}

async function toggleDualMode() {
  const wasStreaming = S.streaming;
  if (wasStreaming) {
    stopStream(false);
  }

  S.dualMode = !S.dualMode;

  try {
    await startPreview();
    if (wasStreaming) {
      await startStream();
    }
    showInfo(S.dualMode ? 'Vista doble activada' : 'Vista doble desactivada');
  } catch (error) {
    S.dualMode = false;
    await startPreview();
    if (wasStreaming) {
      await startStream();
    }
    showInfo(error.message);
  }
}

async function restartStream() {
  if (!S.streaming || S.restartPending) {
    return;
  }

  S.restartPending = true;
  closePeer(false);
  await wait(300);
  await startStream();
}

function setSocketStatus(online) {
  $('socketStatus').classList.toggle('status-online', online);
  $('socketStatus').classList.toggle('status-offline', !online);
}

function showInfo(text) {
  $('infoText').textContent = text;
}

function showError(text) {
  $('loginError').textContent = text;
}

function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
