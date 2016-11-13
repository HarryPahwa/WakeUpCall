#include "eyeOpener.h"

EyeOpener::EyeOpener(int lLedPin, int lRecieverPin){
  this->lLedPin=lLedPin;
  this->lRecieverPin=lRecieverPin;
  pinMode(lLedPin, OUTPUT);
  pinMode(lRecieverPin, INPUT);
  analogWriteFrequency(1,PWM_FREQ);
  analogWrite(this->lLedPin,0);
}

EyeOpener::freeRun(){

}
