/***************************************************
  Fingerprint sensor for Attendance System
  Modified to work with Java GUI
 ****************************************************/

#include <Adafruit_Fingerprint.h>

#if (defined(__AVR__) || defined(ESP8266)) && !defined(__AVR_ATmega2560__)
#include <SoftwareSerial.h>
SoftwareSerial mySerial(2, 3); 
#else
#define mySerial Serial1
#endif

Adafruit_Fingerprint finger(&mySerial);

void setup() {
  Serial.begin(9600);
  while (!Serial);
  delay(100);

  Serial.println("Adafruit Finger Detect Test");

  finger.begin(57600);
  delay(5);

  if (finger.verifyPassword()) {
    Serial.println("Found fingerprint sensor!");
  } else {
    Serial.println("Did not find fingerprint sensor :(");
    while (1) { delay(1); }
  }

  Serial.println(F("Reading sensor parameters"));
  finger.getParameters();
  Serial.print(F("Status: 0x")); Serial.println(finger.status_reg, HEX);
  Serial.print(F("Sys ID: 0x")); Serial.println(finger.system_id, HEX);
  Serial.print(F("Capacity: ")); Serial.println(finger.capacity);
  Serial.print(F("Security level: ")); Serial.println(finger.security_level);
  Serial.print(F("Device address: ")); Serial.println(finger.device_addr, HEX);
  Serial.print(F("Packet len: ")); Serial.println(finger.packet_len);
  Serial.print(F("Baud rate: ")); Serial.println(finger.baud_rate);

  finger.getTemplateCount();
  if (finger.templateCount == 0) {
    Serial.println("Sensor has no fingerprint data. Run 'enroll' first.");
  } else {
    Serial.print("Sensor contains ");
    Serial.print(finger.templateCount);
    Serial.println(" templates.");
    Serial.println("Waiting for a valid finger...");
  }
}

void loop() {
  checkFingerprint();
  delay(50);
}

void checkFingerprint() {
  uint8_t p = finger.getImage();

  if (p == FINGERPRINT_NOFINGER) return; 

  switch (p) {
    case FINGERPRINT_OK: Serial.println("Image taken"); break;
    case FINGERPRINT_PACKETRECIEVEERR: Serial.println("Communication error"); return;
    case FINGERPRINT_IMAGEFAIL: Serial.println("Imaging error"); return;
    default: Serial.println("Unknown error"); return;
  }

  p = finger.image2Tz();
  if (p != FINGERPRINT_OK) {
    Serial.println("Error converting image to template");
    return;
  }

  p = finger.fingerFastSearch();
  if (p == FINGERPRINT_OK) {
    Serial.print("Found ID #"); Serial.print(finger.fingerID);
    Serial.print(" with confidence "); Serial.println(finger.confidence);

    Serial.print("NewID:"); Serial.println(finger.fingerID);
  } else if (p == FINGERPRINT_NOTFOUND) {
    Serial.println("Fingerprint not found in database");

    Serial.println("NewID:-1");
  } else {
    Serial.println("Communication error during search");
    return;
  }

  delay(2000);
}
