
Hubitat Elevation® - Apps/Drivers & Tools
================
---


## Drivers / Apps

Vendor        | Device | Model(Status)
---           | ---    | ---
Aeotec        | [Heavy Duty Smart Switch Gen5](https://aeotec.com/outdoor-z-wave-switch/) | ZW078(Working)
Aeotec        | [Range Extender 6+7](https://aeotec.com/z-wave-repeater/) | ZW117(Working)
Aeotec        | [MultiSensor 6](https://aeotec.com/z-wave-sensor/) | ZW100(Working FW: +1.13)
Aeotec        | [Water Sensor 6](https://aeotec.com/z-wave-water-sensor/) | ZW122(Working)
Eurotronic    | [Air Quality Sensor](https://eurotronic.org/produkte/sensoren/luftguetesensor/) | 700088(Working)
Fibaro        | [Smoke Sensor](https://manuals.fibaro.com/smoke-sensor/) | FGSD-002(Working)
Popp          | [Electric Strike Lock Control](https://www.popp.eu/products/actuators/strike-lock-control/) | 012501(Working)
Popp          | [Z-Rain](https://www.popp.eu/z-rain/) | 700168(Working)
Heatit        | [Z-Temp2](https://www.heatit.com/z-wave/heatit-z-temp-2-2//) | FW 1.01 (Working)
Heltun        | [Touch Panel Switch](https://www.heltun.com/z-wave-touch-panel-switch) | TPS01-05 FW 2.02(Working)
LG            | [WebOS TV](http://webostv.developer.lge.com/) | (Working)
Netatmo       | [Security - Doorbell](https://www.netatmo.com/en-us/security/doorbell) | Working with [limitations](https://forum.netatmo.com/viewtopic.php?f=5&t=18880)
Netatmo       | [Security - Smart Indoor Camera](https://www.netatmo.com/en-us/security/cam-indoor) | Working
Netatmo       | [Security - Smart Outdoor Camera](https://www.netatmo.com/en-us/security/cam-outdoor) | Working
Netatmo       | [Security - Smart Door and Window Sensor](https://www.netatmo.com/en-eu/security/cam-indoor/tag) | Working
Netatmo       | [Security - Smart Smoke Detector](https://www.netatmo.com/en-us/security/cam-outdoor) | WIP (But don't own this device)
Netatmo       | [Weather - Smart Home Weather Station](https://www.netatmo.com/en-us/security/cam-outdoor) | WIP (But don't own this device)
Netatmo       | [Weather - Smart Rain Gauge](https://www.netatmo.com/en-us/security/cam-outdoor) | WIP (But don't own this device)
Netatmo       | [Weather - Smart Anemometer](https://www.netatmo.com/en-us/security/cam-outdoor) | WIP (But don't own this device)
Orvibo        | [Smart Temperature & Humidity Sensor](https://www.orvibo.com/en/product/temp_hum_sensor.html) | ST30 (Working)
Qubino        | [Flush Pilot Wire](https://cdn.shopify.com/s/files/1/0066/8149/3559/files/qubino-flush-pilot-wire-plus-user-manual-v1-1-eng.pdf) | ZMNHJD1(Working)
Qubino        | [Flush Shutter](https://qubino.com/products/flush-shutter/) | ZMNHCD1(Working)<br/>Custom built for CMV / VMC usages: [1](https://www.domo-blog.fr/domotiser-vmc-avec-module-fibaro-fgr-222-223-jeedom/), [2](https://forum.jeedom.com/viewtopic.php?t=46694)
Schwaiger     | [Thermostat - Temperature Sensor](http://www.schwaiger.de/en/temperature-sensor.html) | ZHD01(Working)
Sonoff        | [RF Bridge 433.9MHz](https://sonoff.tech/product/accessories/433-rf-bridge) | R2 V1.0 Tasmota + Portisch (Working)
Xiaomi Mijia  | BLE Temperature and Humidity Sensor | [v1](https://www.amazon.com/FOONEE-Hygrometer-Thermometer-Temperature-Screen-Remote/dp/B07HQJGF53) & [v2](https://www.amazon.com/gooplayer-Bluetooth-Thermometer-Wireless-Hygrometer/dp/B08619Y2QR)  (Working with [external dependency](https://github.com/syepes/Hubitat/tree/master/Drivers/Xiaomi/Xiaomi%20Mijia%20DataCollector/))
Zipato        | [Mini RFID Keypad](https://www.zipato.com/product/mini-keypad-rfid) | ZHD01(Working)


## Tools

Name              | Description                | Status
---               | ---                        | ---
MetricLogger      | Forwards all the state changes and metrics from devices to [VictoriaMetrics](https://victoriametrics.com/) | Working
LokiLogLogger     | Forwards all the Hub logs generated by the Hub to [Loki](https://grafana.com/oss/loki/)                    | Working
LokiZigbeeLogger  | Forwards all the Zigbee logs to [Loki](https://grafana.com/oss/loki/)                                      | Working
LokiZWaveLogger   | Forwards all the ZWave logs to [Loki](https://grafana.com/oss/loki/)                                       | Working


## Development and Contributions
All Apps/Drivers are provided 'as is', I won't be responsible for any damages, bugs or liabilities whatsoever...
If you have any idea for an improvement or find a bug do not hesitate in opening an issue.

**Note:** All drivers and apps will automatically check for a new release "every 7 days at noon", you will find this information in the *State Variable:* driverInfo

**Donations to support current or new device development are accepted via Paypal**: https://paypal.me/syepesf
If your device is missing a driver I can develop it for you, if you send me a sample device ***:-)***


## License
All content is distributed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0)
Copyright &copy; 2020, [Sebastian YEPES](mailto:syepes@gmail.com)
