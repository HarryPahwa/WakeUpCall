#ifndef EYEOPENER_H

#include <Arduino.h>

#define PWM_FREQ 38000

typedef struct thresh{
  int openThresh;
  int closeThresh;
} EyeThresh;

class EyeOpener{
  public:
    EyeOpener(int lLedPin, int lRecieverPin);
    void begin();
    bool getState();
    void freeRun();
  private:
    int lLedPin;
    int lRecieverPin;
    EyeThresh left;
    EyeThresh right;
};

#endif
