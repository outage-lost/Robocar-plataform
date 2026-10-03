/**
 * RoboCar - ESP32 Bluetooth controller
 * Protocol: D:<throttle>:<steering>:<speed>, L:0/L:1 and S, each ending in \n.
 * throttle/steering are -100..100 and speed is 0..255.
 */
#include <Arduino.h>
#include "BluetoothSerial.h"
#include "esp_arduino_version.h"

// Pin definitions — kept from the previous project revision.
const int PIN_MOTOR_A = 12;
const int PIN_MOTOR_B = 13;
const int PIN_MOTOR_C = 14;
const int PIN_MOTOR_D = 15;
const int PIN_LED = 23;
const int PIN_PWM = 25;

const int PWM_FREQUENCY = 5000;
const int PWM_RESOLUTION = 8;
const int PWM_CHANNEL = 0;
const unsigned long COMMAND_TIMEOUT_MS = 500;

BluetoothSerial SerialBT;
String inputLine;
unsigned long lastCommandAt = 0;
int throttle = 0;
int steering = 0;
int maxSpeed = 180;
bool ledOn = false;

void writePwm(int value) {
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcWrite(PIN_PWM, value);
#else
  ledcWrite(PWM_CHANNEL, value);
#endif
}

void stopMotors() {
  digitalWrite(PIN_MOTOR_A, LOW); digitalWrite(PIN_MOTOR_B, LOW);
  digitalWrite(PIN_MOTOR_C, LOW); digitalWrite(PIN_MOTOR_D, LOW);
  writePwm(0);
  throttle = 0; steering = 0;
}

void setMotorDirection(int pinA, int pinB, int value) {
  if (value > 0) { digitalWrite(pinA, HIGH); digitalWrite(pinB, LOW); }
  else if (value < 0) { digitalWrite(pinA, LOW); digitalWrite(pinB, HIGH); }
  else { digitalWrite(pinA, LOW); digitalWrite(pinB, LOW); }
}

void applyDrive() {
  const int left = constrain(throttle + steering, -100, 100);
  const int right = constrain(throttle - steering, -100, 100);
  setMotorDirection(PIN_MOTOR_A, PIN_MOTOR_B, left);
  setMotorDirection(PIN_MOTOR_C, PIN_MOTOR_D, right);
  writePwm(map(max(abs(left), abs(right)), 0, 100, 0, maxSpeed));
}

void handleCommand(const String &line) {
  if (line == "S") { stopMotors(); lastCommandAt = millis(); return; }
  if (line.startsWith("L:")) {
    ledOn = line.substring(2).toInt() != 0;
    digitalWrite(PIN_LED, ledOn ? HIGH : LOW);
    return;
  }
  if (line.startsWith("D:")) {
    const int first = line.indexOf(':', 2);
    const int second = line.indexOf(':', first + 1);
    if (first < 0 || second < 0) return;
    throttle = constrain(line.substring(2, first).toInt(), -100, 100);
    steering = constrain(line.substring(first + 1, second).toInt(), -100, 100);
    maxSpeed = constrain(line.substring(second + 1).toInt(), 0, 255);
    applyDrive();
    lastCommandAt = millis();
  }
}

void readBluetooth() {
  while (SerialBT.available()) {
    const char c = static_cast<char>(SerialBT.read());
    if (c == '\n' || c == '\r') {
      if (inputLine.length()) { handleCommand(inputLine); inputLine = ""; }
    } else if (inputLine.length() < 48) inputLine += c;
    else inputLine = "";
  }
}

void setup() {
  Serial.begin(115200);
  pinMode(PIN_MOTOR_A, OUTPUT); pinMode(PIN_MOTOR_B, OUTPUT);
  pinMode(PIN_MOTOR_C, OUTPUT); pinMode(PIN_MOTOR_D, OUTPUT);
  pinMode(PIN_LED, OUTPUT);
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcAttach(PIN_PWM, PWM_FREQUENCY, PWM_RESOLUTION);
#else
  ledcSetup(PWM_CHANNEL, PWM_FREQUENCY, PWM_RESOLUTION);
  ledcAttachPin(PIN_PWM, PWM_CHANNEL);
#endif
  stopMotors(); digitalWrite(PIN_LED, LOW);
  SerialBT.begin("RoboCar-ESP32");
  lastCommandAt = millis();
  Serial.println("RoboCar Bluetooth listo: RoboCar-ESP32");
}

void loop() {
  readBluetooth();
  if (millis() - lastCommandAt > COMMAND_TIMEOUT_MS) stopMotors();
  delay(2);
}
