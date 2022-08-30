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
import com.hubitat.app.ChildDeviceWrapper

@Field String VERSION = "1.0.0"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

metadata {
  definition (name: "Panasonic - Comfort Cloud - Group", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Netatmo/Netatmo%20-%20Velux%20-%20Home.groovy") {
    capability "Actuator"
    capability "Switch"
    capability "Refresh"
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

def refresh() {
  logger("debug", "refresh() - state: ${state.inspect()}")
  parent.checkState()
}

def parse(String description) {
  logger("trace", "parse() - description: ${description?.inspect()}")
  return []
}

def off() {
  logger("debug", "off()")
  List<ChildDeviceWrapper> devices = getChildDevices()
  devices.each { device ->
    def deviceId = device.currentState("id")?.value
    logger("debug", "off() - Device: ${device} (${deviceId})")
    if (deviceId) {
      parent.deviceControl(["deviceGuid": deviceId, "parameters":["operate": 0]])
    }
  }
}

def on() {
  logger("debug", "on()")
  List<ChildDeviceWrapper> devices = getChildDevices()
  devices.each { device ->
    def deviceId = device.currentState("id")?.value
    logger("debug", "on() - Device: ${device} (${deviceId})")
    if (deviceId) {
      parent.deviceControl(["deviceGuid": deviceId, "parameters":["operate": 1]])
    }
  }
}

def setDetails(Map detail) {
  logger("debug", "setDetails(${detail?.inspect()})")
  state.deviceInfo['groupId'] = detail.groupId
  state.deviceInfo['groupName'] = detail.groupName
}

ChildDeviceWrapper addDevice(Map detail) {
  if (detail && detail.deviceType ==~ /2|3/) {
    addAC(detail)
  } else {
    logger("error", "addDevice() - Unknown Device type: ${detail.inspect()}")
  }
}

ChildDeviceWrapper addAC(Map detail) {
  logger("info", "Creating Device: ${detail.deviceName} (${detail.deviceModuleNumber})")
  logger("debug", "addAC(${detail?.inspect()})")

  try {
    ChildDeviceWrapper cd = getChildDevice("${device.deviceNetworkId}-${detail?.id}")
    if(!cd) {
      logger("debug", "addAC() - Creating AC (${detail.inspect()}")
      ChildDeviceWrapper cdm = addChildDevice("syepes", "Panasonic - Comfort Cloud - AC", "${device.deviceNetworkId}-${detail?.id}", [name: "${detail?.name}", label: "${detail?.name}", isComponent: true])
      cdm.setDetails(detail)
      return cdm
    } else {
      logger("debug", "addAC() - AC: (${device.deviceNetworkId}-${detail?.id}) already exists")
      return cd
    }
  } catch (e) {
    logger("error", "addAC() - AC creation Exception: ${e.inspect()}")
    return null
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
