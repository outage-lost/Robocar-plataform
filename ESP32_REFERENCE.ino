/**
 * RoboCar ESP32 Controller Reference Code
 * Polling API every 100ms for robot commands
 * 
 * This is a reference implementation for the ESP32 microcontroller
 * to integrate with the RoboCar platform.
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>

// ==================== CONFIGURATION ====================

const char* ssid = "YOUR_WIFI_SSID";
const char* password = "YOUR_WIFI_PASSWORD";
const char* serverURL = "http://YOUR_SERVER_IP:3000/api/commands";
const char* robotToken = "YOUR_ESP32_TOKEN";
const int pollInterval = 100; // milliseconds

// Pin definitions
const int PIN_MOTOR_A = 12;  // Motor A forward
const int PIN_MOTOR_B = 13;  // Motor A backward
const int PIN_MOTOR_C = 14;  // Motor B forward
const int PIN_MOTOR_D = 15;  // Motor B backward
const int PIN_LED = 23;       // LED (D13 equivalent)
const int PIN_PWM = 25;       // PWM for speed control

// PWM settings
const int PWM_FREQUENCY = 5000;
const int PWM_RESOLUTION = 8; // 8-bit (0-255)
const int PWM_CHANNEL = 0;

// ==================== GLOBAL STATE ====================

struct RobotState {
  String command;
  int speed;
  bool led;
  unsigned long lastUpdate;
};

RobotState robotState = {
  "stop",
  128,
  false,
  0
};

unsigned long lastPollTime = 0;

// ==================== SETUP ====================

void setup() {
  Serial.begin(115200);
  delay(1000);

  Serial.println("\n\n=== RoboCar ESP32 Initializing ===");

  // Setup pins
  pinMode(PIN_MOTOR_A, OUTPUT);
  pinMode(PIN_MOTOR_B, OUTPUT);
  pinMode(PIN_MOTOR_C, OUTPUT);
  pinMode(PIN_MOTOR_D, OUTPUT);
  pinMode(PIN_LED, OUTPUT);

  // Setup PWM
  ledcSetup(PWM_CHANNEL, PWM_FREQUENCY, PWM_RESOLUTION);
  ledcAttachPin(PIN_PWM, PWM_CHANNEL);

  // Initial state
  stopMotors();
  digitalWrite(PIN_LED, LOW);

  // Connect to WiFi
  connectToWiFi();

  Serial.println("Setup complete!");
}

// ==================== MAIN LOOP ====================

void loop() {
  if (WiFi.connected()) {
    // Poll server for commands every 100ms
    if (millis() - lastPollTime >= pollInterval) {
      pollCommandsFromServer();
      lastPollTime = millis();
    }

    // Apply current commands
    executeCommand();
  } else {
    // Reconnect if disconnected
    if (millis() % 5000 == 0) {
      Serial.println("WiFi disconnected. Reconnecting...");
      connectToWiFi();
    }
    stopMotors();
  }

  delay(10);
}

// ==================== WIFI CONNECTION ====================

void connectToWiFi() {
  Serial.print("Connecting to WiFi: ");
  Serial.println(ssid);

  WiFi.begin(ssid, password);

  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 20) {
    delay(500);
    Serial.print(".");
    attempts++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\nWiFi connected!");
    Serial.print("IP address: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("\nFailed to connect to WiFi");
  }
}

// ==================== API POLLING ====================

void pollCommandsFromServer() {
  HTTPClient http;

  Serial.print("[POLL] Requesting: ");
  Serial.println(serverURL);

  http.begin(serverURL);
  http.addHeader("X-Robot-Token", robotToken);

  int httpCode = http.GET();

  if (httpCode == 200) {
    String payload = http.getString();
    Serial.print("[RESPONSE] ");
    Serial.println(payload);

    // Parse JSON response
    StaticJsonDocument<200> doc;
    DeserializationError error = deserializeJson(doc, payload);

    if (!error) {
      robotState.command = (const char*)doc["command"];
      robotState.speed = doc["speed"];
      robotState.led = doc["led"];
      robotState.lastUpdate = millis();

      Serial.print("[STATE] Command: ");
      Serial.print(robotState.command);
      Serial.print(" | Speed: ");
      Serial.print(robotState.speed);
      Serial.print(" | LED: ");
      Serial.println(robotState.led);
    } else {
      Serial.print("[ERROR] JSON parsing failed: ");
      Serial.println(error.c_str());
    }
  } else {
    Serial.print("[ERROR] HTTP Code: ");
    Serial.println(httpCode);
  }

  http.end();
}

// ==================== MOTOR CONTROL ====================

void executeCommand() {
  // Update LED
  digitalWrite(PIN_LED, robotState.led ? HIGH : LOW);

  // Set PWM speed
  ledcWrite(PWM_CHANNEL, robotState.speed);

  // Execute movement command
  if (robotState.command == "stop") {
    stopMotors();
  } else if (robotState.command == "forward") {
    moveForward();
  } else if (robotState.command == "backward") {
    moveBackward();
  } else if (robotState.command == "left") {
    turnLeft();
  } else if (robotState.command == "right") {
    turnRight();
  }
}

void stopMotors() {
  digitalWrite(PIN_MOTOR_A, LOW);
  digitalWrite(PIN_MOTOR_B, LOW);
  digitalWrite(PIN_MOTOR_C, LOW);
  digitalWrite(PIN_MOTOR_D, LOW);
}

void moveForward() {
  digitalWrite(PIN_MOTOR_A, HIGH);
  digitalWrite(PIN_MOTOR_B, LOW);
  digitalWrite(PIN_MOTOR_C, HIGH);
  digitalWrite(PIN_MOTOR_D, LOW);
}

void moveBackward() {
  digitalWrite(PIN_MOTOR_A, LOW);
  digitalWrite(PIN_MOTOR_B, HIGH);
  digitalWrite(PIN_MOTOR_C, LOW);
  digitalWrite(PIN_MOTOR_D, HIGH);
}

void turnLeft() {
  digitalWrite(PIN_MOTOR_A, HIGH);
  digitalWrite(PIN_MOTOR_B, LOW);
  digitalWrite(PIN_MOTOR_C, LOW);
  digitalWrite(PIN_MOTOR_D, HIGH);
}

void turnRight() {
  digitalWrite(PIN_MOTOR_A, LOW);
  digitalWrite(PIN_MOTOR_B, HIGH);
  digitalWrite(PIN_MOTOR_C, HIGH);
  digitalWrite(PIN_MOTOR_D, LOW);
}

// ==================== MONITORING ====================

void monitorStatus() {
  // Check if still receiving commands
  unsigned long timeSinceLastUpdate = millis() - robotState.lastUpdate;

  if (timeSinceLastUpdate > 2000) {
    Serial.println("[WARNING] No commands received for 2+ seconds. Stopping motors.");
    stopMotors();
  }
}

/**
 * Expected JSON Response from Server:
 * {
 *   "command": "forward",
 *   "speed": 128,
 *   "led": false,
 *   "timestamp": 1234567890
 * }
 *
 * Commands:
 * - "stop"     : Stop all motors
 * - "forward"  : Move forward
 * - "backward" : Move backward
 * - "left"     : Turn left
 * - "right"    : Turn right
 *
 * Speed: 0-255 (PWM duty cycle)
 * LED: true/false (Digital GPIO)
 */
