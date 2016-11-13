#include "main.h"
#include "Arduino.h"
#include "Adafruit_BLE.h"
#include "Adafruit_BluefruitLE_SPI.h"
#include "BluefruitConfig.h"

Adafruit_BluefruitLE_SPI ble(BLUEFRUIT_SPI_CS, BLUEFRUIT_SPI_IRQ, BLUEFRUIT_SPI_RST);

int level=65;
char buf[200];

void setup(){
  pinMode(LED_BUILTIN,OUTPUT);
  digitalWrite(LED_BUILTIN,HIGH);
  Serial.begin(9600);
  while(!Serial);
  if ( !ble.begin(VERBOSE_MODE) )
  {
    Serial.println("Couldn't find Bluefruit, make sure it's in CoMmanD mode & check wiring?");
  }
  Serial.println("OK!");
  Serial.println("Performing a factory reset: ");
  if ( ! ble.factoryReset() ){
    Serial.println("Couldn't factory reset");
  }
  Serial.print("Is in data mode?");
  Serial.println(ble.setMode(BLUEFRUIT_MODE_DATA));
  pinMode(3, OUTPUT);
  pinMode(A14,OUTPUT);
  pinMode(23, INPUT);
  analogWriteFrequency(3,38000);
  analogWrite(3,128);
  analogWrite(A14,5);

}

void loop(){
  /*Serial1.println(ble.read());
  ble.write(level);
  level;
  delay(1000);*/
  while(1){

    Serial.println(analogRead(23));
    Serial.print("ADC val ");
    delay(1000);
  }
  /*delay(100);
  while(analogRead(23)>200 && level < 255){
    analogWrite(A14,level);
    level++;
    delay(10);
  }
  analogWrite(3,0);
  Serial1.print("Final Value ");
  Serial1.println(level);
  //Serial1.print("Analog value ");
  //Serial1.println(analogRead(23));*/
}
