#include "main.h"
#include "Arduino.h"
#include "Adafruit_BLE.h"
#include "Adafruit_BluefruitLE_SPI.h"
#include "BluefruitConfig.h"

Adafruit_BluefruitLE_SPI ble(BLUEFRUIT_SPI_CS, BLUEFRUIT_SPI_IRQ, BLUEFRUIT_SPI_RST);

char buf[200];

void setup(){
  pinMode(LED_BUILTIN,OUTPUT);
  digitalWrite(LED_BUILTIN,HIGH);
  Serial1.begin(9600);

  if ( !ble.begin(VERBOSE_MODE) )
  {
    Serial1.println("Couldn't find Bluefruit, make sure it's in CoMmanD mode & check wiring?");
  }
  Serial1.println("OK!");
  Serial1.println("Performing a factory reset: ");
  if ( ! ble.factoryReset() ){
    Serial1.println("Couldn't factory reset");
  }
  Serial1.print("Is in data mode?");
  Serial1.println(ble.setMode(BLUEFRUIT_MODE_DATA));

}

void loop(){
  while(1){
    Serial1.println(ble.read());
    delay(1000);
  }
}
