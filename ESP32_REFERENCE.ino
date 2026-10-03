/**
 * RoboCar - ESP32 Bluetooth + L298N
 *
 * Protocol, one line per command:
 *   D:<throttle>:<steering>:<speed>   throttle/steering -100..100, speed 0..255
 *   L:0 or L:1                       LED off/on
 *   S                                  emergency stop
 *
 * The mobile app sends a combined throttle/steering command. The ESP32 mixes
 * it into independent left/right motor PWM values and stops on signal loss.
 */
#include <Arduino.h>
#include "BluetoothSerial.h"
#include "esp_arduino_version.h"

// Exact physical wiring for the L298N and the status LED.
const int PIN_LED = 13;  // D13: LED only
const int PIN_ENA = 12;  // D12: motor A speed/PWM only
const int PIN_IN1 = 14;  // D14: motor A direction
const int PIN_IN2 = 27;  // D27: motor A direction
const int PIN_IN3 = 26;  // D26: motor B direction
const int PIN_IN4 = 25;  // D25: motor B direction
const int PIN_ENB = 33;  // D33: motor B speed/PWM only

const int PWM_FREQUENCY = 5000;
const int PWM_RESOLUTION = 8;
const int PWM_CHANNEL_A = 0;
const int PWM_CHANNEL_B = 1;
const unsigned long COMMAND_TIMEOUT_MS = 500;

BluetoothSerial SerialBT;
String inputLine;
unsigned long lastCommandAt = 0;
int throttle = 0;
int steering = 0;
int maxSpeed = 200;
bool ledOn = false;

void writeMotorPwm(int pin, int channel, int value) {
  value = constrain(value, 0, 255);
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcWrite(pin, value);
#else
  ledcWrite(channel, value);
#endif
}

void stopMotors() {
  digitalWrite(PIN_IN1, LOW); digitalWrite(PIN_IN2, LOW);
  digitalWrite(PIN_IN3, LOW); digitalWrite(PIN_IN4, LOW);
  writeMotorPwm(PIN_ENA, PWM_CHANNEL_A, 0);
  writeMotorPwm(PIN_ENB, PWM_CHANNEL_B, 0);
  throttle = 0;
  steering = 0;
}

void setMotorDirection(int pinForward, int pinBackward, int value) {
  if (value > 0) {
    digitalWrite(pinForward, HIGH); digitalWrite(pinBackward, LOW);
  } else if (value < 0) {
    digitalWrite(pinForward, LOW); digitalWrite(pinBackward, HIGH);
  } else {
    digitalWrite(pinForward, LOW); digitalWrite(pinBackward, LOW);
  }
}

void applyDrive() {
  const int left = constrain(throttle + steering, -100, 100);
  const int right = constrain(throttle - steering, -100, 100);
  setMotorDirection(PIN_IN1, PIN_IN2, left);
  setMotorDirection(PIN_IN3, PIN_IN4, right);
  writeMotorPwm(PIN_ENA, PWM_CHANNEL_A, map(abs(left), 0, 100, 0, maxSpeed));
  writeMotorPwm(PIN_ENB, PWM_CHANNEL_B, map(abs(right), 0, 100, 0, maxSpeed));
}

void handleCommand(const String &line) {
  if (line == "S") {
    stopMotors();
    lastCommandAt = millis();
    return;
  }
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
    const char character = static_cast<char>(SerialBT.read());
    if (character == '\n' || character == '\r') {
      if (inputLine.length() > 0) { handleCommand(inputLine); inputLine = ""; }
    } else if (inputLine.length() < 48) {
      inputLine += character;
    } else {
      inputLine = "";
    }
  }
}

void setup() {
  Serial.begin(115200);
  pinMode(PIN_LED, OUTPUT);
  pinMode(PIN_ENA, OUTPUT); pinMode(PIN_ENB, OUTPUT);
  pinMode(PIN_IN1, OUTPUT); pinMode(PIN_IN2, OUTPUT);
  pinMode(PIN_IN3, OUTPUT); pinMode(PIN_IN4, OUTPUT);
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcAttach(PIN_ENA, PWM_FREQUENCY, PWM_RESOLUTION);
  ledcAttach(PIN_ENB, PWM_FREQUENCY, PWM_RESOLUTION);
#else
  ledcSetup(PWM_CHANNEL_A, PWM_FREQUENCY, PWM_RESOLUTION);
  ledcSetup(PWM_CHANNEL_B, PWM_FREQUENCY, PWM_RESOLUTION);
  ledcAttachPin(PIN_ENA, PWM_CHANNEL_A);
  ledcAttachPin(PIN_ENB, PWM_CHANNEL_B);
#endif
  stopMotors();
  digitalWrite(PIN_LED, LOW);
  SerialBT.begin("RoboCar-ESP32");
  lastCommandAt = millis();
  Serial.println("RoboCar listo - L298N ENA D12 / ENB D33 / LED D13");
}

void loop() {
  readBluetooth();
  if (millis() - lastCommandAt > COMMAND_TIMEOUT_MS) stopMotors();
  delay(2);
}
