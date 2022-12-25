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
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[2]

metadata {
  definition (name: "Xiaomi Mijia - Sensor Temperature Humidity Child Device", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Xiaomi/Xiaomi%20Mijia%20-%20Sensor%20Temperature%20Humidity%20Child%20Device.groovy") {
    capability "Actuator"
    capability "Sensor"
    capability "Temperature Measurement"
    capability "Relative Humidity Measurement"
    capability "Battery"

    attribute "name", "string"
    attribute "location", "string"
    attribute "status", "string"
  }
  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
    }
  }
}

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

def uninstalled() {
  logger("debug", "uninstalled()")
  unschedule()
}

def updated() {
  logger("debug", "updated()")

  if (!state.driverInfo?.ver || state.driverInfo.isEmpty() || state.driverInfo.ver != VERSION) {
    installed()
  }
  unschedule()
  initialize()
}

def initialize() {
  logger("debug", "initialize()")
}

def parse(value) {
  logger("debug", "parse() - value: ${value?.inspect()}")
  def result = []
  try {
    if (value) {
      value?.each { k, v ->
        switch (k) {
          case 'battery':
            Map map = [ name: "battery", value: v, unit: "%", displayed: true, descriptionText: "Battery is ${v} %"]
            if(v.toInteger() < 5) {
              logger("warn", "Has a low battery")
            } else {
              if(logDescText && map?.descriptionText) {
                log.info "${device.displayName} ${map.descriptionText}"
              } else if(map?.descriptionText) {
                logger("info", "${map.descriptionText}")
              }
            }
            sendEvent(map)
          break
          case 'location':
            Map map = [ name: "location", value: v, displayed: true]
            sendEvent(map)
          break
          case 'temperature':
            Map map = [ name: "temperature", value: v, unit: "°C", displayed: true, descriptionText: "Temperature is ${v} °C"]
            if(logDescText && map?.descriptionText) {
              log.info "${device.displayName} ${map.descriptionText}"
            } else if(map?.descriptionText) {
              logger("info", "${map.descriptionText}")
            }
            sendEvent(map)
          break
          case 'humidity':
            Map map = [ name: "humidity", value: v, unit: "%", displayed: true, descriptionText: "Humidity is ${v} %"]
            if(logDescText && map?.descriptionText) {
              log.info "${device.displayName} ${map.descriptionText}"
            } else if(map?.descriptionText) {
              logger("info", "${map.descriptionText}")
            }
            sendEvent(map)
          break
          case 'name':
            state.deviceInfo.name = v
          break
          case 'mac':
            state.deviceInfo.mac = v
          break
          case 'firmware':
            state.deviceInfo.firmware = v
          break
          case 'label':
          case 'type':
          break
          default:
            logger("warn", "parse() - type: ${k} - Unhandled")
          break
        }
      }

      state.deviceInfo.lastevent = (new Date().getTime()/1000) as long
    }
  } catch (e) {
    logger("error", "parse() - ${e}, value: ${value?.inspect()}")
  }
  return result
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
