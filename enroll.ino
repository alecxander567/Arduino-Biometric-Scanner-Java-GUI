/***************************************************
  Fingerprint sensor for Attendance System
  Full integration: Enrollment + Scanning
  Works with Java GUI
 ****************************************************/

#include <Adafruit_Fingerprint.h>

#if (defined(__AVR__) || defined(ESP8266)) && !defined(__AVR_ATmega2560__)
  SoftwareSerial mySerial(2, 3); 
#else
  #define mySerial Serial1
#endif

Adafruit_Fingerprint finger = Adafruit_Fingerprint(&mySerial);
uint8_t id;

void setup() {
  Serial.begin(9600);
  while (!Serial);  
  delay(100);

  Serial.println("Fingerprint sensor system starting...");

  finger.begin(57600);
  delay(5);

  if (finger.verifyPassword()) {
    Serial.println("Found fingerprint sensor!");
  } else {
    Serial.println("Did not find fingerprint sensor :(");
    while (1) { delay(1); }
  }

  finger.getParameters();
  Serial.print("Sensor templates: ");
  finger.getTemplateCount();
  Serial.println(finger.templateCount);

  Serial.println("System ready. Send 'ENROLL' or 'SCAN' from GUI.");
}

void loop() {
  if (Serial.available()) {
    String command = Serial.readStringUntil('\n');
    command.trim(); 

    if (command == "ENROLL") {
      enrollFingerprint();
    } else if (command == "SCAN") {
      scanFingerprint();
    }
  }
}

// =========================
// Enrollment function
// =========================
void enrollFingerprint() {
  Serial.println("Enrollment mode: Please type ID (1-127) and press Enter...");
  
  while (!Serial.available());
  id = Serial.parseInt();
  Serial.read(); 
  if (id == 0 || id > 127) {
    Serial.println("Invalid ID. Enrollment cancelled.");
    return;
  }

  Serial.print("Starting enrollment for ID #"); Serial.println(id);

  if (!getFingerprintEnroll()) {
    Serial.println("Enrollment failed. Try again.");
  } else {
    Serial.print("Enrollment successful! ID: "); Serial.println(id);
    Serial.print("EnrolledID:");
    Serial.println(id); 
  }
}

// =========================
// Fingerprint scanning
// =========================
void scanFingerprint() {
  Serial.println("Scan mode: Place your finger...");

  int p = finger.getImage();
  if (p != FINGERPRINT_OK) {
    if (p == FINGERPRINT_NOFINGER) return;
    Serial.println("Error capturing image.");
    return;
  }

  p = finger.image2Tz();
  if (p != FINGERPRINT_OK) {
    Serial.println("Error converting image.");
    return;
  }

  p = finger.fingerFastSearch();
  if (p == FINGERPRINT_OK) {
    Serial.print("Found ID #"); 
    Serial.print(finger.fingerID);
    Serial.print(" Confidence: "); 
    Serial.println(finger.confidence);

    Serial.print("ScanID:");
    Serial.println(finger.fingerID); 
  } else if (p == FINGERPRINT_NOTFOUND) {
    Serial.println("No match found.");
    Serial.println("ScanID:-1"); 
  } else {
    Serial.println("Error searching fingerprint.");
  }

  delay(1000); 
}

// =========================
// Enrollment helper
// =========================
uint8_t getFingerprintEnroll() {
  int p = -1;

  Serial.println("Place finger for first scan...");
  while (p != FINGERPRINT_OK) {
    p = finger.getImage();
  }

  p = finger.image2Tz(1);
  if (p != FINGERPRINT_OK) return p;

  Serial.println("Remove finger...");
  delay(2000);
  while (finger.getImage() != FINGERPRINT_NOFINGER);

  Serial.println("Place same finger again...");
  p = -1;
  while (p != FINGERPRINT_OK) {
    p = finger.getImage();
  }

  p = finger.image2Tz(2);
  if (p != FINGERPRINT_OK) return p;

  p = finger.createModel();
  if (p != FINGERPRINT_OK) return p;

  p = finger.storeModel(id);
  if (p != FINGERPRINT_OK) return p;

  return true;
}
