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

@Field String VERSION = "1.0.3"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

metadata {
  definition (name: "Sonoff RF Bridge - Shade Child Device", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Sonoff/Sonoff%20RF%20Bridge%20-%20Shade%20Child%20Device.groovy") {
    capability "Actuator"
    capability "WindowShade"
    command "stop"
    attribute "switch", "string"
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
  if (value) {
    sendEvent(value)
  }
}

def close() {
  logger("debug", "close()")
  sendEvent([name: "windowShade", value: "closing", displayed: true])
  parent.childClose(device.deviceNetworkId)
}

def off() {
  logger("debug", "off()")
  close()
}

def open() {
  logger("debug", "open()")
  sendEvent(name: "windowShade", value: "opening", displayed: true)
  parent.childOpen(device.deviceNetworkId)
}

def on() {
  logger("debug", "on()")
  open()
}

def startPositionChange(value) {
  logger("debug", "startPositionChange(${value})")

  switch (value) {
    case "close":
      close()
      return
    case "open":
      open()
      return
    default:
      logger("error", "startPositionChange(${value}) - Unsupported state")
  }
}

def setPosition(BigDecimal value) {
  logger("debug", "setPosition(${value})")
  sendEvent(name: "windowShade", value: "partially open", displayed: true)
  parent.childPosition(device.deviceNetworkId, value)
}

def setLevel(BigDecimal value) {
  logger("debug", "setLevel(${value})")
  setPosition(value)
}

def stop() {
  logger("debug", "stop()")
  sendEvent(name: "windowShade", value: "partially open", displayed: true)
  parent.childStop(device.deviceNetworkId)
}

def stopPositionChange() {
  logger("debug", "stopPositionChange()")
  stop()
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
