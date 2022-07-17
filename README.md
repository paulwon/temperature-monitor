# Temperature monitor
## Features
- Monitor the temperature of an Aerohive AP by periodically running the CLI command "show system temperature" via SSH. 
- When the temperature is same or higher than the threshold ("temperatureThreshold" defined in "config.properties"), an alert email will be sent via Gmail. 
- When the temperature is same or below the recovery threshold (temperatureThreshold - temperatureThresholdDiff), the recovery email will be sent.
- Only one alert email will be sent until the recovery email is sent.

## How to run it
- You can run the jar file directly like below
    ```
    java -jar temperature-monitor.jar
    ```
- You can also build your docker image by using the file "Dockerfile" and run it as a container

## config.properties
The file "config.properties" needs to be placed under the same directory where the jar file runs. The same file is also needed when you create a docker image.

The example of the file "config.properties":
```
username=admin
password=xxx
host=1.2.3.4
port=22
command=show sys temp\n
emailUsername=xxx@gmail.com
emailPassword=xxx
emailTo=xxx@domain.com
temperatureThreshold=55
temperatureThresholdDiff=4
```