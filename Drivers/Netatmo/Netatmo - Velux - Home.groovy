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

@Field String VERSION = "1.0.1"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

metadata {
  definition (name: "Netatmo - Velux - Home", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Netatmo/Netatmo%20-%20Velux%20-%20Home.groovy") {
    capability "Actuator"
    capability "Refresh"

    command "open", [[name:"velux_type", type: "ENUM", description: "mode", constraints: ["all","window","shutter"]]]
    command "close", [[name:"velux_type", type: "ENUM", description: "mode", constraints: ["all","window","shutter"]]]
    command "stop", [[name:"velux_type", type: "ENUM", description: "mode", constraints: ["all","window","shutter"]]]
    command "setPosition", [[name:"velux_type", type: "ENUM", description: "mode", constraints: ["all","window","shutter"]], [name:"position", type: "NUMBER", description: ""]]
    command "setScenario", [[name:"scenario_type", type: "ENUM", description: "scenario", constraints: ["wake_up","bedtime","away", "home", "away+bedtime", "home+wake_up"]]]

    attribute "city", "string"
    attribute "place_improved", "enum", ["false","true"]
    attribute "trust_location", "enum", ["false","true"]
    attribute "therm_absence_notification", "enum", ["false","true"]
    attribute "therm_absence_autoaway", "enum", ["false","true"]
  }
  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: false, required: false
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
  parent.checkState(state.deviceInfo.homeID)
}

def parse(String description) {
  logger("trace", "parse() - description: ${description?.inspect()}")
  return []
}

def open(String velux_type="all") {
  logger("debug", "open(${velux_type?.inspect()})")
  getChildDevices()?.each {
    String type = it?.currentValue("velux_type")
    if (type =~ /room/) {
      it.open(velux_type)
    }
  }
}

def close(String velux_type="all") {
  logger("debug", "close(${velux_type?.inspect()})")
  getChildDevices()?.each {
    String type = it?.currentValue("velux_type")
    if (type =~ /room/) {
      it.close(velux_type)
    }
  }
}

def stop(String velux_type="all") {
  logger("debug", "stop(${velux_type?.inspect()})")
  getChildDevices()?.each {
    String type = it?.currentValue("velux_type")
    if (type =~ /room/) {
      it.stop(velux_type)
    }
  }
}

def setPosition(String velux_type="all", BigDecimal position) {
  logger("debug", "osetPositionpen(${velux_type?.inspect()},${position?.inspect()})")
  getChildDevices()?.each {
    String type = it?.currentValue("velux_type")
    if (type =~ /room/) {
      it.setPosition(velux_type, position)
    }
  }
}

def setScenario(String scenario_type="away+bedtime") {
  logger("debug", "setScenario(${scenario_type?.inspect()})")
  switch (scenario_type) {
    case 'away+bedtime':
      getChildDevices()?.each {
        String type = it?.currentValue("velux_type")
        if (type =~ /gateway/) {
          it.setScenario('away')
          pauseExecution(2000)
          it.setScenario('bedtime')
        }
      }
    break
    case 'home':
      getChildDevices()?.each {
        String type = it?.currentValue("velux_type")
        if (type =~ /gateway/) {
          it.setScenarioWithPin(scenario_type)
        }
      }
    break
    case 'home+wake_up':
      getChildDevices()?.each {
        String type = it?.currentValue("velux_type")
        if (type =~ /gateway/) {
          it.setScenarioWithPin('home')
          pauseExecution(2000)
          it.setScenario('wake_up')
        }
      }
    break
    default:
      getChildDevices()?.each {
        String type = it?.currentValue("velux_type")
        if (type =~ /gateway/) {
          it.setScenario(scenario_type)
        }
      }
    break
  }
}

def setDetails(Map detail) {
  logger("debug", "setDetails(${detail?.inspect()})")
  state.deviceInfo['homeID'] = detail.id
  sendEvent(name: "city", value: detail.city)
  sendEvent(name: "place_improved", value: detail.place_improved)
  sendEvent(name: "trust_location", value: detail.trust_location)
  sendEvent(name: "therm_absence_notification", value: detail.therm_absence_notification)
  sendEvent(name: "therm_absence_autoaway", value: detail.therm_absence_autoaway)
}

def setStates(Map states) {
  logger("debug", "setStates(${states?.inspect()})")
  states?.each { k, v ->
    String cv = device.currentValue(k)
    boolean isStateChange = (cv?.toString() != v?.toString()) ? true : false
    if (isStateChange) {
      if (logDescText) {
        log.info "${device.displayName} Value change: ${k} = ${cv} != ${v}"
      } else {
        logger("debug", "setStates() - Value change: ${k} = ${cv} != ${v}")
      }
    }
    sendEvent(name: "${k}", value: "${v}", displayed: true, isStateChange: isStateChange)
  }
}

ChildDeviceWrapper addModule(Map detail) {
  logger("debug", "addModule(${detail?.inspect()})")

  try {
    ChildDeviceWrapper cd = getChildDevice("${device.deviceNetworkId}-${detail?.id}")
    if(!cd) {
      logger("debug", "addModule() - Creating Module (${detail.inspect()}")
      ChildDeviceWrapper cdm = addChildDevice("syepes", "Netatmo - Velux - ${detail?.typeName}", "${device.deviceNetworkId}-${detail?.id}", [name: "${detail?.name} (${detail?.typeName})", label: "${detail?.name} (${detail?.typeName})", isComponent: true])
      cdm.setDetails(detail)
      return cdm
    } else {
      logger("debug", "addModule() - Module: (${device.deviceNetworkId}-${detail?.id}) already exists")
      return cd
    }
  } catch (e) {
    logger("error", "addModule() - Module creation Exception: ${e.inspect()}")
    return null
  }
}

ChildDeviceWrapper addRoom(Map detail) {
  logger("debug", "addRoom(${detail?.inspect()})")

  try {
    ChildDeviceWrapper cd = getChildDevice("${device.deviceNetworkId}-${detail?.id}")
    if(!cd) {
      logger("debug", "addRoom() - Creating Room (${detail.inspect()}")
      ChildDeviceWrapper cdm = addChildDevice("syepes", "Netatmo - Velux - Room", "${device.deviceNetworkId}-${detail?.id}", [name: "${detail?.name} (${detail?.type})", label: "${detail?.name} (${detail?.type})", isComponent: true])
      cdm.setDetails(detail)
      return cdm
    } else {
      logger("debug", "addRoom() - Room: ${device.name} (${device.deviceNetworkId}-${detail?.id}) already exists")
      cd.setDetails(detail)
      return cd
    }
  } catch (e) {
    logger("error", "addRoom() - Room creation Exception: ${e.inspect()}")
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
