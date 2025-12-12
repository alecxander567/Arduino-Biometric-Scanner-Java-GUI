#include <Adafruit_Fingerprint.h>

#if (defined(__AVR__) || defined(ESP8266)) && !defined(__AVR_ATmega2560__)
#include <SoftwareSerial.h>
SoftwareSerial mySerial(2, 3); 
#else
#define mySerial Serial1
#endif

Adafruit_Fingerprint finger(&mySerial);
uint8_t nextID = 1;

void setup() {
  Serial.begin(9600);
  while (!Serial);
  delay(100);

  Serial.println("Fingerprint Attendance System");
  finger.begin(57600);
  delay(5);

  if (finger.verifyPassword()) {
    Serial.println("Found fingerprint sensor!");
  } else {
    Serial.println("Did not find fingerprint sensor :(");
    while (1) { delay(1); }
  }

  finger.getParameters();
  finger.getTemplateCount();

  nextID = finger.templateCount + 1;

  Serial.print("Sensor contains ");
  Serial.print(finger.templateCount);
  Serial.println(" templates.");
  Serial.println("Waiting for valid finger...");
}

void loop() {
  checkFingerprint();

  if (Serial.available()) {
    String command = Serial.readStringUntil('\n');
    command.trim();

    if (command.startsWith("ENROLL:")) {
      uint8_t enrollID = command.substring(7).toInt();
      if (enrollID <= 0 || enrollID > 127) {
        Serial.println("Invalid ID. Must be 1-127.");
        Serial.println("NewID:-1");
      } else {
        enrollFingerprint(enrollID);
      }
    } else if (command == "SCAN") {
      checkFingerprint();
    } else if (command == "CLEARFP") {
      clearAllFingerprints();
    }
  }
}

void checkFingerprint() {
  uint8_t p = finger.getImage();
  if (p == FINGERPRINT_NOFINGER) return;
  if (p != FINGERPRINT_OK) {
    Serial.println("Error capturing image");
    return;
  }

  Serial.println("Image taken");

  p = finger.image2Tz();
  if (p != FINGERPRINT_OK) {
    Serial.println("Error converting image");
    return;
  }

  p = finger.fingerFastSearch();
  if (p == FINGERPRINT_OK) {
    Serial.print("Found ID #"); 
    Serial.println(finger.fingerID);
    Serial.print("NewID:"); 
    Serial.println(finger.fingerID);
  } else if (p == FINGERPRINT_NOTFOUND) {
    Serial.println("Fingerprint not found - enrolling new fingerprint...");
    if (enrollNewFingerprint()) {
      Serial.print("NewID:");
      Serial.println(nextID - 1); 
    } else {
      Serial.println("Enrollment failed");
      Serial.println("NewID:-1");
    }
  } else {
    Serial.println("Communication error");
  }
  delay(500);
}

bool enrollNewFingerprint() {
  uint8_t id = nextID++;

  Serial.print("Enrolling ID #");
  Serial.println(id);

  int p = finger.image2Tz(1);
  if (p != FINGERPRINT_OK) return false;

  Serial.println("Remove finger");
  delay(2000);
  while (finger.getImage() != FINGERPRINT_NOFINGER);

  Serial.println("Place same finger again");
  p = -1;
  while (p != FINGERPRINT_OK) {
    p = finger.getImage();
    if (p == FINGERPRINT_NOFINGER) continue;
    if (p != FINGERPRINT_OK) return false;
  }

  p = finger.image2Tz(2);
  if (p != FINGERPRINT_OK) return false;

  p = finger.createModel();
  if (p != FINGERPRINT_OK) return false;

  p = finger.storeModel(id);
  if (p != FINGERPRINT_OK) return false;

  Serial.println("Enrollment successful!");
  return true;
}

void enrollFingerprint(uint8_t id) {
  Serial.println("Starting enrollment for ID: " + String(id));
  int result = getFingerprintEnroll(id);
  if (result == FINGERPRINT_OK) {
    Serial.println("Enrollment successful!");
    Serial.print("NewID:");
    Serial.println(id);
  } else {
    Serial.println("Enrollment failed");
    Serial.println("NewID:-1");
  }
}

uint8_t getFingerprintEnroll(uint8_t id) {
  uint8_t p = FINGERPRINT_NOFINGER;

  Serial.println("Place finger for first scan...");
  while (p != FINGERPRINT_OK) p = finger.getImage();

  p = finger.image2Tz(1);
  if (p != FINGERPRINT_OK) return p;

  Serial.println("Remove finger...");
  delay(2000);
  while (finger.getImage() != FINGERPRINT_NOFINGER);

  Serial.println("Place same finger again...");
  p = FINGERPRINT_NOFINGER;
  while (p != FINGERPRINT_OK) p = finger.getImage();

  p = finger.image2Tz(2);
  if (p != FINGERPRINT_OK) return p;

  p = finger.createModel();
  if (p != FINGERPRINT_OK) return p;

  p = finger.storeModel(id);
  return p;
}

void clearAllFingerprints() {
  Serial.println("Clearing sensor fingerprint database...");
  uint8_t p = finger.emptyDatabase();
  if (p == FINGERPRINT_OK) {
    Serial.println("All fingerprints deleted!");
    Serial.println("ClearFP:OK");
  } else {
    Serial.println("Failed to clear fingerprints.");
    Serial.println("ClearFP:FAIL");
  }
}
