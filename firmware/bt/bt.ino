const int LED_PIN = 3;
int ledValue = 0;
unsigned long lastToggleTime = 0;
int counter = 1;

String command = "";
char commandStart = '<';
char commandEnd = '>';

//#define SERIAL Serial
#define SERIAL Serial1

void setup() {
  Serial1.begin(9600);
  pinMode(LED_PIN, OUTPUT);
}

void loop() {
  unsigned long currentTime = millis();
  
  if (currentTime - lastToggleTime > 500) {
    //ledValue = ledValue == 0 ? 1 : 0;
    //digitalWrite(LED_PIN, ledValue);
  
    SERIAL.print("Hello ");
    SERIAL.println(counter);
    
    lastToggleTime = currentTime;
    
    counter++;
  }
  
  while (SERIAL.available() > 0) {
    int input = SERIAL.read();

    if (input == commandStart) {
      command = "";
    } else if (input == commandEnd) {
      handle(command);

      command = "";
    } else {
      command += (char)input;
    }
  }
}

void handle(String command) {
  if (command == "on") {
    setLed(1);
  } else if (command == "off") {
    setLed(0);
  }
  
  Serial1.println("Received: '" + command + "'");
}

void setLed(int value) {
  ledValue = value;
  digitalWrite(LED_PIN, ledValue);
}
