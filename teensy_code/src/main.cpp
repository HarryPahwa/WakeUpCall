#include "main.h"
#include "Arduino.h"
#include "Adafruit_BLE.h"
#include "Adafruit_BluefruitLE_SPI.h"
#include "BluefruitConfig.h"

#define LEFT_TX 3
#define RIGHT_TX 4
#define LEFT_RX 23
#define RIGHT_RX 22

Adafruit_BluefruitLE_SPI ble(BLUEFRUIT_SPI_CS, BLUEFRUIT_SPI_IRQ, BLUEFRUIT_SPI_RST);

void setup(){
  pinMode(LED_BUILTIN,OUTPUT);
  digitalWrite(LED_BUILTIN,HIGH);
  Serial.begin(9600);
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
  pinMode(RIGHT_TX, OUTPUT);
  pinMode(LEFT_TX, OUTPUT);
  pinMode(A14,OUTPUT);
  pinMode(LEFT_RX, INPUT);
  pinMode(RIGHT_RX, INPUT);
  analogWriteFrequency(3,38000);
  analogWrite(LEFT_TX,128);
  analogWrite(RIGHT_TX,128);
  analogWrite(A14,110);

}

void loop(){
  /*Serial1.println(ble.read());
  ble.write(level);
  level;
  delay(1000);*/
  if(ble.available()){
    ble.read();
    ble.write(analogRead(23)/4);
    delay(100);
  }
  /*delay(100);
  while(analogRead(23)>800 && level < 255){
    analogWrite(A14,level);
    level++;
    delay(10);
  }
  analogWrite(A14,0);
  Serial.print("Final Value ");
  Serial.println(level);*/
  //Serial1.print("Analog value ");
  //Serial1.println(analogRead(23));*/
}
