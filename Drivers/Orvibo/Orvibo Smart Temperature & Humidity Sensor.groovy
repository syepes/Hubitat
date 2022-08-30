/**
 *  Copyright (C) Sebastian YEPES
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

import groovy.transform.Field

@Field String VERSION = "1.0.1"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

metadata {
  definition (name: "Orvibo Smart Temperature & Humidity Sensor", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Orvibo/Orvibo%20Smart%20Temperature%20%26%20Humidity%20Sensor.groovy") {
    capability "Actuator"
    capability "Sensor"
    capability "RelativeHumidityMeasurement"
    capability "TemperatureMeasurement"
    capability "Battery"
    capability "Initialize"

    command "clearState"

    fingerprint profileId: "0104", inClusters: "0000,0001,0019,0402,0405", model: "898ca74409a740b28d5841661e72268d" // ST30
  }

  preferences {
    input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
    input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: false, required: false

    //Temp and Humidity Offsets
    input name: "tempOffset", type: "decimal", title: "Temperature", description: "Offset", range:"*..*"
    input name: "humidityOffset", type: "decimal", title: "Humidity", description: "Offset", range: "*..*"
  }
}

// installed() runs just after a sensor is paired
def installed() {
  logger("debug", "installed(${VERSION})")

  if (state.driverInfo == null || state.driverInfo.isEmpty() || state.driverInfo.ver != VERSION) {
    state.driverInfo = [ver:VERSION]
  }

  if (state.deviceInfo == null) {
    state.deviceInfo = [:]
  }

  initialize()
}

def initialize() {
  logger("debug", "initialize()")

  if (getDataValue("model") && getDataValue("model") != "ST30") {
    if (device.data.model == "898ca74409a740b28d5841661e72268d") {
      updateDataValue("model", "ST30")
    }
  }
}

def updated() {
  logger("debug", "updated()")

  if (!state.driverInfo?.ver || state.driverInfo.isEmpty() || state.driverInfo.ver != VERSION) {
    installed()
  }

  unschedule()
}

def clearState() {
  logger("debug", "ClearState() - Clearing device states")
  state.clear()

  if (state?.driverInfo == null) {
    state.driverInfo = [:]
  } else {
    state.driverInfo.clear()
  }

  if (state?.deviceInfo == null) {
    state.deviceInfo = [:]
  } else {
    state.deviceInfo.clear()
  }

  installed()
}

def parse(String description) {
  logger("trace", "parse() - description: ${description?.inspect()}")

  if (description?.startsWith('cat')) {
    Map descMap = zigbee.parseDescriptionAsMap(description)
    logger("trace", "parse(catchall): ${descMap}")
  } else if (description?.startsWith('re')) {
    description = description - "read attr - "
    Map descMap = (description).split(",").inject([:]) {
      map, param ->
      def nameAndValue = param.split(":")
      map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
    }
    // Reverse payload byte order for little-endian data types - required for Hubitat firmware 2.0.5 or newer
    def intEncoding = Integer.parseInt(descMap.encoding, 16)
    if (descMap.value != null && intEncoding > 0x18 && intEncoding < 0x3e) {
      descMap.value = reverseHexString(descMap.value)
      logger("trace", "Little-endian payload data type; Hex value reversed to: ${descMap.value}")
    }

    // Send message data to appropriate parsing function based on the type of report
    switch (descMap.cluster) {
      case "0001": // Battery report
        if (descMap.attrId == "0021") {
          parseBattery(descMap.value)
        }
      break
      case "0402": // Temperature report
        parseTemperature(descMap.value)
      break
      case "0405": // Humidity report
        parseHumidity(descMap.value)
      break
      default:
        logger("warn", "Unknown read attribute message: ${descMap}")
    }
  }
  return [:]
}

// Reverses order of bytes in hex string
private def reverseHexString(hexString) {
  def reversed = ""
  for (int i = hexString.length(); i > 0; i -= 2) {
    reversed += hexString.substring(i - 2, i )
  }
  return reversed
}

// Calculate temperature with 0.01 precision in C or F unit as set by hub location settings
private parseTemperature(hexString) {
  logger("trace", "parseTemperature(${hexString})")

  Map map = [:]
  float temp = hexStrToSignedInt(hexString)/100
  def tempScale = location.temperatureScale
  def debugText = "Reported temperature: raw = ${temp}°C"
  if (temp < -50) {
    logger("warn", "Out-of-bounds temperature value received. Battery voltage may be too low")
  } else {
    if (tempScale == "F") {
      temp = ((temp * 1.8) + 32)
      debugText += ", converted = ${temp}°F"
    }
    if (tempOffset) {
      temp = (temp + tempOffset)
      debugText += ", offset = ${tempOffset}"
    }
    logger("debug", debugText)
    temp = temp.round(2)

    map.name = "temperature"
    map.value = temp
    map.unit = "°$tempScale"
    map.descriptionText = "Temperature is ${temp}°${tempScale}"
    map.displayed = true

    if (logDescText) {
      log.info "${device.displayName} ${map.descriptionText}"
    } else if(map?.descriptionText) {
      logger("info", "${map.descriptionText}")
    }

    sendEvent(map)
  }
}

// Calculate humidity with 0.1 precision
private parseHumidity(hexString) {
  logger("trace", "parseHumidity(${hexString})")

  Map map = [:]
  float humidity = Integer.parseInt(hexString,16)/100
  def debugText = "Reported humidity: raw = ${humidity}"
  if (humidity > 100) {
    logger("warn", "Out-of-bounds humidity value received. Battery voltage may be too low")
    return ""
  } else {
    if (humidityOffset) {
      debugText += ", offset = ${humidityOffset}"
      humidity = (humidity + humidityOffset)
    }
    logger("debug", debugText)
    humidity = humidity.round(1)

    map.name = "humidity"
    map.value = humidity
    map.unit = "%"
    map.descriptionText = "Humidity is ${map.value} ${map.unit}"
    map.displayed = true

    if (logDescText) {
      log.info "${device.displayName} ${map.descriptionText}"
    } else if(map?.descriptionText) {
      logger("info", "${map.descriptionText}")
    }

    sendEvent(map)
  }
}

private parseBattery(hexString) {
  logger("trace", "parseBattery(${hexString})")

  def rawValue = Integer.parseInt(hexString,16)
  if (0 <= rawValue && rawValue <= 200) {
    Map map = [:]
    def roundedPct = Math.round(rawValue / 2)
    map.name = "battery"
    map.value = roundedPct
    map.unit = "%"
    map.descriptionText = "Battery level is ${roundedPct}%"
    map.displayed = true

    if (logDescText) {
      log.info "${device.displayName} ${map.descriptionText}"
    } else if(map?.descriptionText) {
      logger("info", "${map.descriptionText}")
    }

    sendEvent(map)
  }
}

/**
 * @param level Level to log at, see LOG_LEVELS for options
 * @param msg Message to log
 */
private logger(level, msg) {
  if (level && msg) {
    Integer levelIdx = LOG_LEVELS.indexOf(level)
    Integer setLevelIdx = LOG_LEVELS.indexOf(logLevel)
    if (setLevelIdx < 0) {
      setLevelIdx = LOG_LEVELS.indexOf(DEFAULT_LOG_LEVEL)
    }
    if (levelIdx <= setLevelIdx) {
      log."${level}" "${device.displayName} ${msg}"
    }
  }
}
