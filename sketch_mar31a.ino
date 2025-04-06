#include <WiFi.h>
#include <WebServer.h>
#include <DHT.h>
#include <WiFiUdp.h>
#include <NTPClient.h>
#include <ArduinoJson.h>
#include <HTTPClient.h>

// ========== KONFIGURACJA ==========
#define DHTPIN 4
#define DHTTYPE DHT11
#define SOIL_MOISTURE_PIN 35
#define LIGHT_SENSOR_PIN 39
#define LED_PIN 2

const char* ssid = "Polibudzianie";
const char* password = "Bimber1000";

const unsigned long SENSOR_READ_INTERVAL = 300000;  // 5 minut
const unsigned long PUMP_DURATION = 7000;           // 7 sekund
const unsigned long PUMP_COOLDOWN = 3600000;        // 1 godzina
const int MAX_DATA_POINTS = 288;                    // 24h co 5 minut

// ========== DEKLARACJE STRUKTUR I ZMIENNYCH ==========
struct SensorData {
  float temp;
  float airHumidity;
  int soilMoisture;
  int light;
  String timestamp;
  String fullDate;
};

// ========== DEKLARACJE ZMIENNYCH GLOBALNYCH ==========
SensorData sensorData[MAX_DATA_POINTS];  // Tablica przechowująca dane sensorów
int dataCount = 0;                       // Licznik zapisanych danych
int desiredMoistureLevel = 2000;         // Pożądany poziom wilgotności gleby
bool autoWateringEnabled = false;        // Zmienna do śledzenia stanu opcji podlewania
unsigned long lastPumpTime = 0;          // Czas ostatniego podlewania
unsigned long nextSensorUpdate = 0;      // Czas następnej aktualizacji danych
unsigned long nextMoistureCheck = 0;     // Czas następnej kontroli wilgotności

const char* backgroundImage = "https://avatars.mds.yandex.net/i?id=877df61e12f9606abe97b47d153ed703_l-5230282-images-thumbs&ref=rim&n=13&w=3000&h=1688";

// ========== DEKLARACJE OBIEKTÓW ==========
WebServer server(80);
WiFiUDP ntpUDP;
NTPClient timeClient(ntpUDP, "pool.ntp.org");
DHT dht(DHTPIN, DHTTYPE);

// ========== FUNKCJE POMOCNICZE ==========
bool isDST(time_t date) {
  struct tm *timeinfo = gmtime(&date);
  int year = timeinfo->tm_year + 1900;
  
  struct tm marchLastSunday = {0};
  marchLastSunday.tm_year = timeinfo->tm_year;
  marchLastSunday.tm_mon = 2;
  marchLastSunday.tm_mday = 31;
  marchLastSunday.tm_hour = 1;
  time_t marchTime = mktime(&marchLastSunday);
  marchLastSunday = *gmtime(&marchTime);
  marchLastSunday.tm_mday -= marchLastSunday.tm_wday;
  marchTime = mktime(&marchLastSunday);
  
  struct tm octoberLastSunday = {0};
  octoberLastSunday.tm_year = timeinfo->tm_year;
  octoberLastSunday.tm_mon = 9;
  octoberLastSunday.tm_mday = 31;
  octoberLastSunday.tm_hour = 1;
  time_t octoberTime = mktime(&octoberLastSunday);
  octoberLastSunday = *gmtime(&octoberTime);
  octoberLastSunday.tm_mday -= octoberLastSunday.tm_wday;
  octoberTime = mktime(&octoberLastSunday);
  
  return (date >= marchTime && date < octoberTime);
}

String getFormattedDateTime() {
  timeClient.update();
  unsigned long epochTime = timeClient.getEpochTime();
  bool dst = isDST(epochTime);
  int timeOffset = dst ? 7200 : 3600;
  time_t localTime = epochTime + timeOffset;
  struct tm *timeinfo = gmtime(&localTime);

  char buffer[25];
  sprintf(buffer, "%04d-%02d-%02d %02d:%02d:%02d",
          timeinfo->tm_year + 1900, timeinfo->tm_mon + 1, timeinfo->tm_mday,
          timeinfo->tm_hour, timeinfo->tm_min, timeinfo->tm_sec);
          
  return String(buffer);
}

void shiftDataLeft() {
  for(int i = 0; i < MAX_DATA_POINTS - 1; i++) {
    sensorData[i] = sensorData[i+1];
  }
}

// ========== FUNKCJE SENSORÓW ==========
void updateSensorData() {
  float temp = dht.readTemperature();
  float hum = dht.readHumidity();
  int soil = analogRead(SOIL_MOISTURE_PIN);
  int light = analogRead(LIGHT_SENSOR_PIN);

  if (!isnan(temp) && !isnan(hum)) {
    timeClient.update();
    String time = timeClient.getFormattedTime();
    String fullDate = getFormattedDateTime();

    if(dataCount >= MAX_DATA_POINTS) {
      shiftDataLeft();
      dataCount = MAX_DATA_POINTS - 1;
    }

    sensorData[dataCount] = {temp, hum, soil, light, time, fullDate};
    dataCount++;
    
    Serial.println("Nowe dane: " + fullDate + " | Temp: " + String(temp) + 
                 "°C | Wilg: " + String(hum) + "% | Gleba: " + String(soil) + 
                 " | Światło: " + String(light));
  }
}

void checkSoilMoisture() {
  if (millis() >= nextMoistureCheck && autoWateringEnabled) { // Tylko gdy auto podlewanie włączone
    if (dataCount == 0) return;

    int currentMoisture = sensorData[dataCount-1].soilMoisture;
    
    if (currentMoisture > desiredMoistureLevel) {
      if (millis() - lastPumpTime > PUMP_COOLDOWN) {
        digitalWrite(LED_PIN, HIGH);
        delay(PUMP_DURATION);
        digitalWrite(LED_PIN, LOW);
        lastPumpTime = millis();
        
        String timestamp = getFormattedDateTime().substring(11);
        Serial.println("[" + timestamp + "] AUTO PODLEWANIE: Aktywowano pompę (" + 
                     String(currentMoisture) + " > " + String(desiredMoistureLevel) + ")");
      }
    }
    nextMoistureCheck = millis() + SENSOR_READ_INTERVAL;
  }
}

// ========== FUNKCJE SERWERA WWW ==========
void sendCurrentData() {
  if (dataCount == 0) {
    server.send(204, "text/plain", "");
    return;
  }

  SensorData data = sensorData[dataCount-1];
  String autoWateringStatus = autoWateringEnabled ? "WŁĄCZONE" : "WYŁĄCZONE";
  String autoWateringColor = autoWateringEnabled ? "#388E3C" : "#D32F2F";

  String html = R"=====(
  <!DOCTYPE html><html>
  <head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>AgroCzuwacz - Panel sterowania</title>
    <style>
      body {
        font-family: 'Segoe UI', Tahoma, sans-serif;
        background: url(')=====" + String(backgroundImage) + R"=====(') no-repeat center center fixed;
        background-size: cover;
        margin: 0;
        padding: 20px;
      }
      .container {
        background-color: rgba(255, 255, 255, 0.95);
        border-radius: 15px;
        padding: 25px;
        max-width: 1200px;
        margin: 20px auto;
        box-shadow: 0 8px 25px rgba(0,0,0,0.1);
      }
      h1 {
        color: #2E7D32;
        text-align: center;
        margin-bottom: 25px;
      }
      .card-container {
        display: flex;
        flex-wrap: wrap;
        justify-content: space-between;
        gap: 20px;
        margin-bottom: 25px;
      }
      .card {
        background-color: white;
        border-radius: 10px;
        padding: 20px;
        flex: 1 1 200px;
        min-width: 200px;
        box-shadow: 0 3px 10px rgba(0,0,0,0.1);
      }
      .card-title {
        color: #555;
        font-size: 16px;
        margin: 0 0 10px 0;
        text-align: center;
        display: flex;
        flex-direction: column;
        align-items: center;
      }
      .card-icon {
        font-size: 24px;
        margin-bottom: 5px;
      }
      .card-value {
        font-size: 28px;
        font-weight: bold;
        color: #2E7D32;
        margin: 10px 0;
        text-align: center;
      }
      .card-unit {
        font-size: 14px;
        color: #777;
        text-align: center;
      }
      .btn-container {
        display: flex;
        justify-content: center;
        margin-top: 20px;
      }
      .btn {
        background-color: #1976D2;
        color: white;
        text-align: center;
        padding: 12px 20px;
        border-radius: 8px;
        text-decoration: none;
        font-weight: bold;
      }
      .status-info {
        text-align: center;
        color: #555;
        font-size: 14px;
        margin-top: 20px;
      }
      .moisture-info {
        text-align: center;
        margin: 15px 0;
        font-size: 16px;
      }
      .moisture-value {
        font-weight: bold;
        color: #1976D2;
      }
      .auto-watering-status {
        text-align: center;
        margin: 15px 0;
        font-size: 16px;
      }
    </style>
  </head>
  <body>
    <div class="container">
      <h1>AgroCzuwacz - Panel sterowania</h1>
      
      <div class="moisture-info">
        Aktualny zadany poziom wilgotności: <span class="moisture-value" id="desired-moisture">)=====" + String(desiredMoistureLevel) + R"=====(</span>
      </div>
      
      <div class="auto-watering-status">
        Automatyczne podlewanie: <span id="auto-watering-value" style="font-weight:bold;color:)=====" + autoWateringColor + R"=====(">)=====" + autoWateringStatus + R"=====(</span>
      </div>
      
      <div class="card-container">
        <div class="card">
          <h3 class="card-title"><span class="card-icon">🌡️</span>Temperatura powietrza</h3>
          <div class="card-value" id="temp-value">)=====" + String(data.temp, 1) + R"=====(</div>
          <div class="card-unit">°C</div>
        </div>
        
        <div class="card">
          <h3 class="card-title"><span class="card-icon">💧</span>Wilgotność powietrza</h3>
          <div class="card-value" id="hum-value">)=====" + String(data.airHumidity, 1) + R"=====(</div>
          <div class="card-unit">%</div>
        </div>
        
        <div class="card">
          <h3 class="card-title"><span class="card-icon">🌱</span>Wilgotność gleby</h3>
          <div class="card-value" id="soil-value">)=====" + String(data.soilMoisture) + R"=====(</div>
          <div class="card-unit"></div>
        </div>
        
        <div class="card">
          <h3 class="card-title"><span class="card-icon">☀️</span>Poziom światła</h3>
          <div class="card-value" id="light-value">)=====" + String(data.light) + R"=====(</div>
          <div class="card-unit">lx</div>
        </div>
      </div>
      
      <div class="btn-container">
        <a href="/history" class="btn">Historia pomiarów</a>
      </div>
      
      <div class="status-info">
        Ostatnia aktualizacja: <span id="last-update">)=====" + data.fullDate + R"=====(</span>
      </div>
    </div>
    
    <script>
      function updateData() {
        fetch('/data')
          .then(response => response.json())
          .then(data => {
            document.getElementById('temp-value').textContent = data.temperature.toFixed(1);
            document.getElementById('hum-value').textContent = data.airHumidity.toFixed(1);
            document.getElementById('soil-value').textContent = data.soilMoisture;
            document.getElementById('light-value').textContent = data.lightLevel;
            document.getElementById('last-update').textContent = data.fullDate;
            
            const autoWateringEl = document.getElementById('auto-watering-value');
            autoWateringEl.textContent = data.autoWatering ? "WŁĄCZONE" : "WYŁĄCZONE";
            autoWateringEl.style.color = data.autoWatering ? "#388E3C" : "#D32F2F";
          });
          
        fetch('/getMoisture')
          .then(response => response.json())
          .then(data => {
            document.getElementById('desired-moisture').textContent = data.desiredMoisture;
          });
      }
      
      setInterval(updateData, 5000);
      updateData();
    </script>
  </body>
  </html>
  )=====";

  server.send(200, "text/html", html);
}

void sendSensorData() {
  if (dataCount == 0) {
    server.send(204, "application/json", "{}");
    return;
  }

  SensorData data = sensorData[dataCount-1];
  String json = "{\"temperature\":" + String(data.temp, 1) +
               ",\"airHumidity\":" + String(data.airHumidity, 1) +
               ",\"soilMoisture\":" + String(data.soilMoisture) +
               ",\"lightLevel\":" + String(data.light) +
               ",\"desiredMoisture\":" + String(desiredMoistureLevel) +
               ",\"autoWatering\":" + String(autoWateringEnabled ? "true" : "false") +
               ",\"fullDate\":\"" + data.fullDate + "\"}";

  server.send(200, "application/json", json);
}

void sendHistoryPage() {
  String html = R"=====(
  <!DOCTYPE html><html>
  <head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Historia pomiarów - AgroCzuwacz</title>
    <script src="https://cdn.plot.ly/plotly-latest.min.js"></script>
    <style>
      body {
        font-family: 'Segoe UI', Tahoma, sans-serif;
        background: url(')=====" + String(backgroundImage) + R"=====(') no-repeat center center fixed;
        background-size: cover;
        margin: 0;
        padding: 20px;
      }
      .container {
        background-color: rgba(255, 255, 255, 0.95);
        border-radius: 15px;
        padding: 25px;
        max-width: 1200px;
        margin: 20px auto;
        box-shadow: 0 8px 25px rgba(0,0,0,0.1);
      }
      h1 {
        color: #2E7D32;
        text-align: center;
        margin-bottom: 25px;
      }
      .chart-container {
        background-color: white;
        border-radius: 10px;
        padding: 15px;
        margin-bottom: 25px;
        box-shadow: 0 3px 10px rgba(0,0,0,0.05);
      }
      .chart {
        height: 400px;
        width: 100%;
      }
      .btn {
        display: block;
        background-color: #388E3C;
        color: white;
        text-align: center;
        padding: 12px;
        margin: 20px auto 0;
        border-radius: 8px;
        text-decoration: none;
        width: 200px;
        font-weight: bold;
      }
    </style>
  </head>
  <body>
    <div class="container">
      <h1>Historia pomiarów (24h)</h1>
      
      <div class="chart-container">
        <div id="temperatureChart" class="chart"></div>
      </div>
      
      <div class="chart-container">
        <div id="humidityChart" class="chart"></div>
      </div>
      
      <div class="chart-container">
        <div id="soilChart" class="chart"></div>
      </div>
      
      <div class="chart-container">
        <div id="lightChart" class="chart"></div>
      </div>

      <a href="/" class="btn">Powrót do panelu</a>
    </div>

    <script>
      const timeData = [];
      const tempData = [];
      const humData = [];
      const soilData = [];
      const lightData = [];
      const fullDateData = [];
  )=====";

  for(int i = 0; i < dataCount; i++) {
    html += "timeData.push('" + String(i) + "');";
    html += "tempData.push(" + String(sensorData[i].temp) + ");";
    html += "humData.push(" + String(sensorData[i].airHumidity) + ");";
    html += "soilData.push(" + String(sensorData[i].soilMoisture) + ");";
    html += "lightData.push(" + String(sensorData[i].light) + ");";
    html += "fullDateData.push('" + sensorData[i].fullDate + "');";
  }

  html += R"=====(
      // Konfiguracja wspólna dla wszystkich wykresów
      const layoutCommon = {
        xaxis: {
          title: 'Czas',
          showticklabels: false
        },
        hovermode: 'closest',
        showlegend: false
      };

      // Konfiguracja markerów
      const markerSettings = {
        size: 6,
        color: '#ffffff',
        line: {
          width: 2
        }
      };

      // Wykres temperatury
      Plotly.newPlot('temperatureChart', [{
        x: timeData,
        y: tempData,
        mode: 'lines+markers',
        line: {color: '#D32F2F', width: 2},
        marker: Object.assign({}, markerSettings, {line: {color: '#D32F2F', width: 2}}),
        name: 'Temperatura',
        text: timeData.map((t, i) => 'Data: ' + fullDateData[i] + '<br>Wartość: ' + tempData[i].toFixed(1) + '°C'),
        hoverinfo: 'text'
      }], {
        ...layoutCommon,
        title: 'Temperatura powietrza',
        yaxis: {title: 'Temperatura (°C)'}
      });

      // Wykres wilgotności powietrza
      Plotly.newPlot('humidityChart', [{
        x: timeData,
        y: humData,
        mode: 'lines+markers',
        line: {color: '#1976D2', width: 2},
        marker: Object.assign({}, markerSettings, {line: {color: '#1976D2', width: 2}}),
        name: 'Wilgotność',
        text: timeData.map((t, i) => 'Data: ' + fullDateData[i] + '<br>Wartość: ' + humData[i].toFixed(1) + '%'),
        hoverinfo: 'text'
      }], {
        ...layoutCommon,
        title: 'Wilgotność powietrza',
        yaxis: {title: 'Wilgotność (%)'}
      });

      // Wykres wilgotności gleby
      Plotly.newPlot('soilChart', [{
        x: timeData,
        y: soilData,
        mode: 'lines+markers',
        line: {color: '#388E3C', width: 2},
        marker: Object.assign({}, markerSettings, {line: {color: '#388E3C', width: 2}}),
        name: 'Wilgotność gleby',
        text: timeData.map((t, i) => 'Data: ' + fullDateData[i] + '<br>Wartość: ' + soilData[i]),
        hoverinfo: 'text'
      }], {
        ...layoutCommon,
        title: 'Wilgotność gleby',
        yaxis: {title: 'Wilgotność gleby'}
      });

      // Wykres światła
      Plotly.newPlot('lightChart', [{
        x: timeData,
        y: lightData,
        mode: 'lines+markers',
        line: {color: '#FFA000', width: 2},
        marker: Object.assign({}, markerSettings, {line: {color: '#FFA000', width: 2}}),
        name: 'Nasłonecznienie',
        text: timeData.map((t, i) => 'Data: ' + fullDateData[i] + '<br>Wartość: ' + lightData[i]),
        hoverinfo: 'text'
      }], {
        ...layoutCommon,
        title: 'Poziom nasłonecznienia',
        yaxis: {title: 'Nasłonecznienie'}
      });
    </script>
  </body>
  </html>
  )=====";

  server.send(200, "text/html", html);
}

void handleGetMoisture() {
  if (dataCount == 0) {
    server.send(204, "application/json", "{}");
    return;
  }

  SensorData data = sensorData[dataCount-1];
  String json = "{\"currentMoisture\":" + String(data.soilMoisture) + 
               ",\"desiredMoisture\":" + String(desiredMoistureLevel) + "}";
  
  server.send(200, "application/json", json);
}

void handleSetMoisture() {
  if (server.method() != HTTP_POST) {
    server.send(405, "application/json", "{\"status\":\"error\",\"message\":\"Method not allowed\"}");
    return;
  }

  // Obsługa zarówno form-urlencoded jak i raw JSON
  int oldMoisture = desiredMoistureLevel;
  int moistureValue;
  
  if (server.hasArg("desiredMoisture")) {
    // Form-urlencoded
    moistureValue = server.arg("desiredMoisture").toInt();
  } else {
    // Raw JSON
    String body = server.arg("plain");
    if(body.length() == 0) {
      server.send(400, "application/json", "{\"status\":\"error\",\"message\":\"Empty request body\"}");
      return;
    }

    DynamicJsonDocument doc(256);
    DeserializationError error = deserializeJson(doc, body);
    
    if(error || !doc.containsKey("desiredMoisture")) {
      server.send(400, "application/json", "{\"status\":\"error\",\"message\":\"Invalid request\"}");
      return;
    }
    
    moistureValue = doc["desiredMoisture"].as<int>();
  }

  desiredMoistureLevel = moistureValue;
  
  // Logowanie zmiany do Serial Monitora
  Serial.println("Zmieniono zadany poziom wilgotności z " + String(oldMoisture) + 
                " na " + String(desiredMoistureLevel));
  
  String response = "{\"status\":\"success\",\"desiredMoisture\":" + String(desiredMoistureLevel) + "}";
  server.send(200, "application/json", response);
}

void handleWaterPlant() {
  // Walidacja metody HTTP
  if (server.method() != HTTP_POST) {
    server.send(405, "application/json", "{\"status\":\"error\",\"message\":\"Method not allowed\"}");
    return;
  }

  // Sprawdzenie cooldownu pompy
  unsigned long remainingCooldown = PUMP_COOLDOWN - (millis() - lastPumpTime);
  if (remainingCooldown > 0) {
    String error = "{\"status\":\"error\",\"message\":\"Pump cooldown active\",\"remaining_ms\":" + String(remainingCooldown) + "}";
    server.send(429, "application/json", error);
    return;
  }

  // Aktywacja pompy
  digitalWrite(LED_PIN, HIGH);
  delay(PUMP_DURATION);
  digitalWrite(LED_PIN, LOW);
  lastPumpTime = millis();

  // Logowanie
  Serial.println("[PUMP] Manual watering activated for " + String(PUMP_DURATION) + "ms");

  // Response
  String response = "{\"status\":\"success\",\"message\":\"Watering completed\",\"duration_ms\":" + String(PUMP_DURATION) + "}";
  server.send(200, "application/json", response);
}

void handleDebugPump() {
  // Aktywacja pompy
  digitalWrite(LED_PIN, HIGH);
  delay(PUMP_DURATION);
  digitalWrite(LED_PIN, LOW);

  // Logowanie
  Serial.println("[DEBUG-PUMP] Force activated for " + String(PUMP_DURATION) + "ms");

  // Prosta odpowiedź JSON
  String response = "{\"status\":\"success\",\"message\":\"Debug pump activated\",\"duration_ms\":" + String(PUMP_DURATION) + "}";
  server.send(200, "application/json", response);
}

void handleSetAutoWatering() {
  if (server.method() != HTTP_POST) {
    server.send(405, "application/json", "{\"status\":\"error\",\"message\":\"Method not allowed\"}");
    return;
  }

  // Obsługa zarówno form-urlencoded jak i raw JSON
  bool oldState = autoWateringEnabled;
  bool newState;
  
  if (server.hasArg("enabled")) {
    // Form-urlencoded
    newState = server.arg("enabled") == "true";
  } else {
    // Raw JSON
    String body = server.arg("plain");
    if(body.length() == 0) {
      server.send(400, "application/json", "{\"status\":\"error\",\"message\":\"Empty request body\"}");
      return;
    }

    DynamicJsonDocument doc(256);
    DeserializationError error = deserializeJson(doc, body);
    
    if(error || !doc.containsKey("enabled")) {
      server.send(400, "application/json", "{\"status\":\"error\",\"message\":\"Invalid request\"}");
      return;
    }
    
    newState = doc["enabled"].as<bool>();
  }

  autoWateringEnabled = newState;
  
  // Logowanie zmiany do Serial Monitora
  Serial.println("Zmieniono tryb automatycznego podlewania z " + String(oldState ? "WŁĄCZONE" : "WYŁĄCZONE") + 
                " na " + String(autoWateringEnabled ? "WŁĄCZONE" : "WYŁĄCZONE"));
  
  String response = "{\"status\":\"success\",\"autoWatering\":" + String(autoWateringEnabled ? "true" : "false") + "}";
  server.send(200, "application/json", response);
}

// ========== SETUP I LOOP ==========
void setup() {
  Serial.begin(115200);
  dht.begin();
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);
  
  WiFi.begin(ssid, password);
  Serial.print("Łączenie z WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nPołączono z WiFi");
  Serial.println("IP: " + WiFi.localIP().toString());

  timeClient.begin();
  timeClient.setTimeOffset(0);
  while(!timeClient.update()) {
    timeClient.forceUpdate();
    delay(500);
  }

  // Konfiguracja endpointów
  server.on("/", HTTP_GET, sendCurrentData);
  server.on("/data", HTTP_GET, sendSensorData);
  server.on("/history", HTTP_GET, sendHistoryPage);
  server.on("/getMoisture", HTTP_GET, handleGetMoisture);
  server.on("/setMoisture", HTTP_POST, handleSetMoisture);
  server.on("/waterPlant", HTTP_POST, handleWaterPlant);
  server.on("/debugPump", HTTP_GET, handleDebugPump);
  server.on("/setAutoWatering", HTTP_POST, handleSetAutoWatering);

  server.enableCORS(true); // Włącz CORS
  server.begin();
  Serial.println("Serwer HTTP uruchomiony");

  updateSensorData();
  nextSensorUpdate = millis() + SENSOR_READ_INTERVAL;
}

void loop() {
  server.handleClient();
  
  if (millis() >= nextSensorUpdate) {
    updateSensorData();
    nextSensorUpdate = millis() + SENSOR_READ_INTERVAL;
  }
  
  checkSoilMoisture();
}