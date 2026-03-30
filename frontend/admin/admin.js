const S = {
  token: '',
  socket: null,
  pc: null,
  remoteStream: null,
  pendingCandidates: [],
  phoneMicEnabled: false,
  flashEnabled: false,
  rotation: 0,
  expanded: false,
  talkbackEnabled: false,
  talkbackStream: null,
  talkbackTrack: null,
  talkbackSender: null,
  moveTimers: {},
  activeDirs: new Set(),
  iceServers: [
    { urls: 'stun:stun.l.google.com:19302' },
    { urls: 'stun:stun1.l.google.com:19302' }
  ]
};

const KEY_TOKEN = 'rc_admin_token';
const KEY_SIZE = 'rc_viewer_size';
const KEY_ROT = 'rc_viewer_rotation';
const REPEAT = 80;
const $ = (id) => document.getElementById(id);
const qs = (sel) => document.querySelector(sel);

document.addEventListener('DOMContentLoaded', () => {
  $('loginForm').addEventListener('submit', (event) => {
    event.preventDefault();
    login();
  });
  $('logoutBtn').addEventListener('click', logout);
  $('btnExitFullscreen').addEventListener('click', () => setExpanded(false));
  $('remoteVideo').muted = false;
  $('remoteVideo').volume = 1;
  $('remoteVideo').addEventListener('loadedmetadata', updateResolutionBadge);
  $('remoteVideo').addEventListener('resize', updateResolutionBadge);
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && S.expanded) {
      setExpanded(false);
    }
  });

  const token = new URLSearchParams(location.search).get('token') || localStorage.getItem(KEY_TOKEN) || '';
  if (token) {
    $('tokenInput').value = token;
    S.token = token;
    localStorage.setItem(KEY_TOKEN, token);
    connectSocket();
  }
});

function login() {
  const token = $('tokenInput').value.trim();
  if (!token) {
    showLoginError('Ingresa TOKEN_ADMIN');
    return;
  }

  S.token = token;
  localStorage.setItem(KEY_TOKEN, token);
  connectSocket();
}

function logout() {
  releaseAll();
  stopTalkbackCapture();
  closePeer();
  if (S.socket) {
    S.socket.disconnect();
    S.socket = null;
  }

  dot('serverStatus', false);
  dot('esp32Status', false);
  dot('cameraStatus', false);
  noSignal();
  $('loginScreen').classList.add('screen-active');
  $('appShell').classList.remove('app-active');
  $('tokenInput').value = '';
  showLoginError('');
  setExpanded(false);
  localStorage.removeItem(KEY_TOKEN);
}

function connectSocket() {
  if (S.socket) {
    S.socket.disconnect();
  }

  const socket = io('/admin', {
    auth: { token: S.token },
    query: { token: S.token },
    transports: ['websocket', 'polling']
  });

  socket.on('connect', () => {
    S.socket = socket;
    $('loginScreen').classList.remove('screen-active');
    $('appShell').classList.add('app-active');
    dot('serverStatus', true);
    addLog('Panel conectado');
    restorePrefs();
    bindControls();
  });

  socket.on('disconnect', () => {
    dot('serverStatus', false);
    dot('cameraStatus', false);
    closePeer();
    noSignal();
    addLog('Desconectado del servidor');
  });

  socket.on('connect_error', (error) => showLoginError(error.message || 'No se pudo conectar'));
  socket.on('stun:servers', (servers) => {
    if (Array.isArray(servers) && servers.length) {
      S.iceServers = servers;
    }
  });
  socket.on('state:update', renderState);
  socket.on('system:log', (entries) => {
    $('systemLog').innerHTML = '';
    entries.forEach((entry) => appendEntry(entry));
    $('systemLog').scrollTop = $('systemLog').scrollHeight;
  });
  socket.on('system:log:append', appendEntry);
  socket.on('mic:status', ({ active }) => {
    S.phoneMicEnabled = Boolean(active);
    updatePhoneMicButton();
  });
  socket.on('flash:status', ({ active }) => {
    S.flashEnabled = Boolean(active);
    updateFlashButton();
  });
  socket.on('webrtc:offer', ({ offer }) => handleOffer(offer));
  socket.on('webrtc:ice-candidate', ({ candidate }) => handleIceCandidate(candidate));
}

function emit(eventName, payload) {
  if (S.socket?.connected) {
    S.socket.emit(eventName, payload);
  }
}

function bindControls() {
  bindJoystick();
  bindSimple('speedSlider', 'input', (event) => {
    $('speedValue').textContent = event.target.value;
    emit('command:speed', { speed: Number(event.target.value) });
  });
  bindSimple('ledToggle', 'change', (event) => {
    $('ledText').textContent = event.target.checked ? 'encendido' : 'apagado';
    emit('command:led', { state: event.target.checked });
  });
  bindSimple('btnFlip', 'click', () => emit('camera:command', { command: 'flip' }));
  bindSimple('btnFlash', 'click', () => emit('camera:command', { command: 'flash' }));
  bindSimple('btnPhoneMic', 'click', () => {
    S.phoneMicEnabled = !S.phoneMicEnabled;
    updatePhoneMicButton();
    emit('camera:mic-toggle', { enabled: S.phoneMicEnabled });
  });
  bindSimple('btnTalkback', 'click', () => toggleTalkback());
  bindSimple('btnRotate', 'click', rotateViewer);
  bindSimple('btnFullscreen', 'click', toggleFullscreen);
  bindSimple('viewerSize', 'change', (event) => {
    applySize(event.target.value);
    localStorage.setItem(KEY_SIZE, event.target.value);
  });

  window.onkeydown = (event) => {
    const direction = mapKey(event.key);
    if (direction && !event.repeat) {
      event.preventDefault();
      startMove(direction);
    }
  };

  window.onkeyup = (event) => {
    const direction = mapKey(event.key);
    if (direction) {
      event.preventDefault();
      stopMove(direction);
    }
  };

  window.onblur = () => releaseAll();
}

function bindJoystick() {
  ['btnForward', 'btnBackward', 'btnLeft', 'btnRight', 'btnStop'].forEach((id) => {
    const oldNode = $(id);
    const node = oldNode.cloneNode(true);
    oldNode.parentNode.replaceChild(node, oldNode);
    const direction = node.dataset.direction;

    const start = (event) => {
      event.preventDefault();
      startMove(direction);
    };
    const stop = (event) => {
      event.preventDefault();
      stopMove(direction);
    };

    node.addEventListener('mousedown', start);
    node.addEventListener('mouseup', stop);
    node.addEventListener('mouseleave', stop);
    node.addEventListener('touchstart', start, { passive: false });
    node.addEventListener('touchend', stop, { passive: false });
    node.addEventListener('touchcancel', stop, { passive: false });
  });
}

function bindSimple(id, eventName, handler) {
  const oldNode = $(id);
  const node = oldNode.cloneNode(true);
  oldNode.parentNode.replaceChild(node, oldNode);
  node.addEventListener(eventName, handler);
  if (id === 'viewerSize') {
    const saved = localStorage.getItem(KEY_SIZE) || 'original';
    node.value = [...node.options].find((option) => option.value === saved) ? saved : 'original';
  }
}

function renderState(state) {
  dot('esp32Status', Boolean(state.esp32Online));
  dot('cameraStatus', Boolean(state.streamActive));
  $('speedSlider').value = String(state.speed ?? 128);
  $('speedValue').textContent = String(state.speed ?? 128);
  $('ledToggle').checked = Boolean(state.led);
  $('ledText').textContent = state.led ? 'encendido' : 'apagado';
  S.phoneMicEnabled = Boolean(state.microphoneActive);
  S.flashEnabled = Boolean(state.flashEnabled);
  updatePhoneMicButton();
  updateFlashButton();
  if (!state.streamActive) {
    noSignal();
  }
}

function restorePrefs() {
  const size = localStorage.getItem(KEY_SIZE) || 'original';
  $('viewerSize').value = [...$('viewerSize').options].find((option) => option.value === size) ? size : 'original';
  applySize(size);

  const rotation = parseInt(localStorage.getItem(KEY_ROT) || '0', 10);
  S.rotation = Number.isFinite(rotation) ? rotation : 0;
  applyRotation();
}

function applySize(value) {
  const surface = qs('.video-surface');
  surface.classList.toggle('video-surface-original', value === 'original');
  const scale = Number(value) || 1;
  surface.style.setProperty('--video-scale', String(scale));
}

function rotateViewer() {
  S.rotation = (S.rotation + 90) % 360;
  localStorage.setItem(KEY_ROT, String(S.rotation));
  applyRotation();
}

function applyRotation() {
  const surface = qs('.video-surface');
  surface.classList.remove('rotated-0', 'rotated-90', 'rotated-180', 'rotated-270');
  surface.classList.add(`rotated-${S.rotation}`);
  qs('.video-frame').style.transform = `translate(-50%, -50%) rotate(${S.rotation}deg)`;
  $('btnRotate').querySelector('.btn-label').textContent = `Rotar ${S.rotation}°`;
}

function toggleFullscreen() {
  setExpanded(!S.expanded);
}

function setExpanded(active) {
  S.expanded = Boolean(active);
  qs('.video-surface').classList.toggle('video-surface-expanded', S.expanded);
  $('btnFullscreen').querySelector('.btn-label').textContent = S.expanded
    ? 'Salir de pantalla completa'
    : 'Pantalla completa';
}

function updatePhoneMicButton() {
  const button = $('btnPhoneMic');
  if (!button) {
    return;
  }

  button.classList.toggle('is-active', S.phoneMicEnabled);
  button.querySelector('.btn-label').textContent = S.phoneMicEnabled
    ? 'Micrófono del teléfono encendido'
    : 'Micrófono del teléfono apagado';
}

function updateFlashButton() {
  const button = $('btnFlash');
  if (!button) {
    return;
  }

  button.classList.toggle('is-active', S.flashEnabled);
  button.querySelector('.btn-label').textContent = S.flashEnabled ? 'Flash encendido' : 'Flash apagado';
}

function updateTalkbackButton() {
  const button = $('btnTalkback');
  if (!button) {
    return;
  }

  button.classList.toggle('is-active', S.talkbackEnabled);
  button.querySelector('.btn-label').textContent = S.talkbackEnabled
    ? 'Intercomunicador encendido'
    : 'Intercomunicador apagado';
}

function stopTalkbackCapture() {
  S.talkbackStream?.getTracks().forEach((track) => track.stop());
  S.talkbackStream = null;
  S.talkbackTrack = null;
  S.talkbackEnabled = false;
  updateTalkbackButton();
}

async function ensureTalkbackTrack() {
  if (S.talkbackTrack && S.talkbackTrack.readyState === 'live') {
    return S.talkbackTrack;
  }

  S.talkbackStream?.getTracks().forEach((track) => track.stop());
  S.talkbackStream = await navigator.mediaDevices.getUserMedia({
    audio: {
      echoCancellation: true,
      noiseSuppression: true,
      autoGainControl: true
    },
    video: false
  });
  S.talkbackTrack = S.talkbackStream.getAudioTracks()[0] || null;
  return S.talkbackTrack;
}

async function toggleTalkback() {
  try {
    S.talkbackEnabled = !S.talkbackEnabled;

    if (S.talkbackEnabled) {
      const track = await ensureTalkbackTrack();
      if (!track) {
        throw new Error('No se pudo capturar el micrófono del panel');
      }

      S.phoneMicEnabled = true;
      updatePhoneMicButton();
      emit('camera:mic-toggle', { enabled: true });

      attachTalkbackSender();
      track.enabled = true;
      if (S.talkbackSender) {
        await S.talkbackSender.replaceTrack(track).catch(() => {});
      }
    } else {
      if (S.talkbackTrack) {
        S.talkbackTrack.enabled = false;
      }
      if (S.talkbackSender) {
        await S.talkbackSender.replaceTrack(null).catch(() => {});
      }
    }

    updateTalkbackButton();
  } catch (error) {
    S.talkbackEnabled = false;
    updateTalkbackButton();
    addLog(`Intercom: ${error.message}`);
  }
}

function attachTalkbackSender() {
  if (!S.pc) {
    return;
  }

  const sender = S.pc.getTransceivers()
    .find((transceiver) => transceiver.receiver?.track?.kind === 'audio')
    ?.sender;

  if (sender) {
    S.talkbackSender = sender;
  }
}

function startMove(direction) {
  if (S.activeDirs.has(direction)) {
    return;
  }

  S.activeDirs.add(direction);
  setPressed(direction, true);
  emit('command:move', { direction, speed: currentSpeed() });
  S.moveTimers[direction] = setInterval(() => {
    emit('command:move', { direction, speed: currentSpeed() });
  }, REPEAT);
}

function stopMove(direction) {
  S.activeDirs.delete(direction);
  setPressed(direction, false);
  clearInterval(S.moveTimers[direction]);
  delete S.moveTimers[direction];
  emit('command:move', { direction: 'stop', speed: currentSpeed() });
}

function releaseAll() {
  [...S.activeDirs].forEach((direction) => stopMove(direction));
  emit('command:move', { direction: 'stop', speed: currentSpeed() });
}

function setPressed(direction, active) {
  qs(`[data-direction="${direction}"]`)?.classList.toggle('pressed', active);
}

function currentSpeed() {
  return Number($('speedSlider')?.value || 128);
}

async function handleOffer(offer) {
  try {
    closePeer();

    const pc = new RTCPeerConnection({
      iceServers: S.iceServers,
      bundlePolicy: 'max-bundle',
      rtcpMuxPolicy: 'require'
    });
    S.pc = pc;
    S.pendingCandidates = [];
    S.remoteStream = new MediaStream();
    $('remoteVideo').srcObject = S.remoteStream;

    if (S.talkbackEnabled) {
      S.phoneMicEnabled = true;
      updatePhoneMicButton();
      emit('camera:mic-toggle', { enabled: true });
    }

    pc.ontrack = (event) => {
      if (!S.remoteStream.getTracks().find((track) => track.id === event.track.id)) {
        S.remoteStream.addTrack(event.track);
      }
      $('remoteVideo').play().catch(() => {});
      showVideo();
    };

    pc.onicecandidate = ({ candidate }) => {
      if (candidate) {
        emit('webrtc:ice-candidate', { candidate });
      }
    };

    pc.onconnectionstatechange = () => {
      if (['failed', 'disconnected', 'closed'].includes(pc.connectionState)) {
        noSignal();
      }
    };

    await pc.setRemoteDescription(new RTCSessionDescription(offer));
    attachTalkbackSender();

    if (S.talkbackEnabled && S.talkbackSender) {
      const track = await ensureTalkbackTrack();
      if (track) {
        track.enabled = true;
        await S.talkbackSender.replaceTrack(track).catch(() => {});
      }
    }

    const answer = await pc.createAnswer();
    await pc.setLocalDescription(answer);
    await flushPendingCandidates();
    emit('webrtc:answer', { answer: pc.localDescription });
  } catch (error) {
    addLog(`WebRTC: ${error.message}`);
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

function closePeer() {
  if (S.pc) {
    S.pc.close();
    S.pc = null;
  }
  S.pendingCandidates = [];
  S.talkbackSender = null;
  S.remoteStream = null;
  $('remoteVideo').srcObject = null;
}

function showVideo() {
  $('noSignal').classList.remove('signal-visible');
  $('liveBadge').classList.remove('hud-hidden');
  $('resolutionBadge').classList.remove('hud-hidden');
  updateResolutionBadge();
  dot('cameraStatus', true);
}

function noSignal() {
  $('noSignal').classList.add('signal-visible');
  $('liveBadge').classList.add('hud-hidden');
  $('resolutionBadge').classList.add('hud-hidden');
}

function updateResolutionBadge() {
  $('resolutionBadge').textContent = `${$('remoteVideo').videoWidth || 0}×${$('remoteVideo').videoHeight || 0}`;
}

function appendEntry(entry) {
  const row = document.createElement('div');
  row.className = 'log-entry';
  row.textContent = entry;
  $('systemLog').appendChild(row);
  while ($('systemLog').children.length > 200) {
    $('systemLog').removeChild($('systemLog').firstChild);
  }
  $('systemLog').scrollTop = $('systemLog').scrollHeight;
}

function addLog(message) {
  appendEntry(`[${new Date().toLocaleTimeString('es-EC', { hour12: false })}] ${message}`);
}

function dot(id, active) {
  $(id)?.classList.toggle('status-online', active);
  $(id)?.classList.toggle('status-offline', !active);
}

function mapKey(key) {
  return {
    ArrowUp: 'forward',
    ArrowDown: 'backward',
    ArrowLeft: 'left',
    ArrowRight: 'right',
    ' ': 'stop'
  }[key];
}

function showLoginError(message) {
  $('loginError').textContent = message;
}
