const MAX_LOG_ENTRIES = 200;
const ESP32_TIMEOUT_MS = 2000;

const robotState = {
  command: 'stop',
  speed: 128,
  led: false,
  esp32Online: false,
  lastPollTime: 0,
  adminConnected: false,
  cameraConnected: false,
  streamActive: false,
  microphoneActive: false,
  flashEnabled: false,
  lastOfferAt: 0,
  systemLog: []
};

function timestamp() {
  return new Date().toLocaleTimeString('es-EC', { hour12: false });
}

function addLog(message) {
  const entry = `[${timestamp()}] ${message}`;
  robotState.systemLog.push(entry);

  if (robotState.systemLog.length > MAX_LOG_ENTRIES) {
    robotState.systemLog.shift();
  }

  console.log(entry);
  return entry;
}

function getState() {
  return {
    ...robotState,
    systemLog: [...robotState.systemLog]
  };
}

function setState(key, value) {
  robotState[key] = value;
  return robotState[key];
}

function setCommand(command, speed = robotState.speed) {
  robotState.command = command;
  robotState.speed = Math.max(0, Math.min(255, Number(speed) || robotState.speed));
  return getState();
}

function setSpeed(speed) {
  robotState.speed = Math.max(0, Math.min(255, Number(speed) || robotState.speed));
  return robotState.speed;
}

function markEsp32Poll() {
  robotState.lastPollTime = Date.now();
  robotState.esp32Online = true;
  return getState();
}

function updateESP32Status() {
  const now = Date.now();
  const online = now - robotState.lastPollTime < ESP32_TIMEOUT_MS;
  robotState.esp32Online = online;
  return online;
}

function resetCommand(reason = 'Comando reiniciado a STOP (seguridad)') {
  robotState.command = 'stop';
  addLog(reason);
  return getState();
}

module.exports = {
  ESP32_TIMEOUT_MS,
  addLog,
  getState,
  setState,
  setCommand,
  setSpeed,
  markEsp32Poll,
  updateESP32Status,
  resetCommand
};
