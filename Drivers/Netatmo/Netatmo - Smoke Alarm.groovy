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
import groovy.json.JsonSlurper

@Field String VERSION = "1.0.2"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[2]

metadata {
  definition (name: "Netatmo - Smoke Alarm", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Netatmo/Netatmo%20-%20Smoke%20Alarm.groovy") {
    capability "Actuator"
    capability "SmokeDetector"
    capability "Battery"
    capability "Refresh"

    attribute "homeName", "string"
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

  sendEvent(name: "smoke", value: "clear")
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

def refresh() {
  logger("debug", "refresh() - state: ${state.inspect()}")
}

def parse(String description) {
  logger("trace", "parse() - description: ${description?.inspect()}")
  return []
}

def setHome(String homeID, String homeName) {
  logger("debug", "setHome(${homeID?.inspect()}, ${homeName?.inspect()})")
  state.deviceInfo['homeID'] = homeID
  sendEvent(name: "homeName", value: homeName)
}

def smoke(String type) {
  logger("debug", "smoke(${type})")
  if (logDescText) {
    log.info "${device.displayName} Has ${type == 0 ? 'cleared' : 'detected'} Smoke Alarm"
  } else {
    logger("debug", "Has ${type == 0 ? 'cleared' : 'detected'} Smoke Alarm")
  }
  sendEvent(name: "smoke", value: type == 0 ? 'clear' : 'detected', displayed: true)
}

def battery(String type) {
  logger("debug", "battery(${type})")
  if (logDescText) {
    log.info "${device.displayName} Battery is ${type == 0 ? 'low' : 'very low'}"
  } else {
    logger("debug", "Battery is ${type == 0 ? 'low' : 'very low'}")
  }
  sendEvent(name: "battery", value: type == 0 ? '50' : '5', descriptionText: type == 0 ? 'low' : 'very low', displayed: true)
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
