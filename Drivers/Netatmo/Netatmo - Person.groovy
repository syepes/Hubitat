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
  definition (name: "Netatmo - Person", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Netatmo/Netatmo%20-%20Person.groovy") {
    capability "Actuator"
    capability "Sensor"
    capability "Presence Sensor"
    capability "ContactSensor"
    capability "Refresh"

    command "seen"
    command "away"
    command "setAway"
    attribute "homeName", "string"
    attribute "image_tag", "string"
    attribute "last_seen", "string"
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

  sendEvent(name: "contact", value: "open")
  sendEvent(name: "presence", value: "not present")
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

def setHome(homeID,homeName) {
  logger("debug", "setHome(${homeID?.inspect()}, ${homeName?.inspect()})")
  state.deviceInfo['homeID'] = homeID
  sendEvent(name: "homeName", value: homeName)
}

def setAway() {
  logger("debug", "setAway()")
  parent.setAway(state.deviceInfo['homeID'], device.name)
}

def seen(String snapshot_url = null) {
  logger("debug", "seen(${snapshot_url})")
  sendEvent(name: "presence", value: "present", displayed: true)
  if (snapshot_url != null) {
    sendEvent(name: "image_tag", value: '<img src="'+ snapshot_url +'" width="240" height="190">', isStateChange: true, displayed: true)
  }

}

private contactClose(String person){
  sendEvent(name: "contact", value: "closed", displayed: true, isStateChange: true, descriptionText: "Activated by ${person}")
  runIn(180, "contactOpen")
}

private contactOpen(){
    sendEvent(name: "contact", value: "open", displayed: false, isStateChange: true)
}

def away() {
  logger("debug", "away()")
  sendEvent(name: "presence", value: "not present")
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
