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

@Field String VERSION = "1.0.0"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

@Field static Map<String,String> typeRunMode = [
  'fixed': 'heat',
  'off': 'off',
  'frost': 'off',
  'anti_frost': 'off',
  'prog': 'auto',
  'schedule': 'auto',
  'override': 'emergency heat'
]

metadata {
  definition (name: "Warmup - Cloud - Room", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Warmup/Warmup%20-%20Cloud%20-%20Room.groovy") {
    capability "Actuator"
    capability "Refresh"
    capability "Switch"

    capability "TemperatureMeasurement"
    capability "ThermostatMode"
    capability "ThermostatOperatingState"
    capability "ThermostatHeatingSetpoint"

    command "clearState"
    command "setProgramme", [[name:"mode", type: "ENUM", description: "mode", constraints: ["prog","fixed"]], [name:"degrees", type: "NUMBER", description: "Temperature"]]
    command "setOverride", [[name:"degrees", type: "NUMBER", description: "Temperature"], [name:"until", type: "NUMBER", description: "Until minutes (10 >=< 240)"]]

    attribute "id", "string"
    attribute "type", "string"
    attribute "name", "string"
    attribute "moduleNumber", "string"
    attribute "groupName", "string"

    // Group / Status (parameters)
    attribute "locMode", "string"
    attribute "locName", "string"
    attribute "isOwner", "string"
    attribute "roomType", "string"
    attribute "roomMode", "string"
    attribute "runMode", "string"
    attribute "sleepActive", "string"
    attribute "floorType", "string"
    attribute "mainRoom", "string"
    attribute "sensorFault", "string"
    attribute "hasPolled", "string"
    attribute "lastPoll", "string"

    attribute "targetTemp", "number"
    attribute "overrideTemp", "number"
    attribute "overrideDur", "number"
    attribute "currentTemp", "number"
    attribute "airTemp", "number"
    attribute "floor1Temp", "number"
    attribute "floor2Temp", "number"
    attribute "fixedTemp", "number"
    attribute "heatingTarget", "number"
    attribute "setbackTemp", "number"
    attribute "comfortTemp", "number"
    attribute "sleepTemp", "number"
    attribute "minTemp", "number"
    attribute "maxTemp", "number"
    attribute "energy", "number"
    attribute "cost", "number"
  }
  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: true, required: false
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

def refresh() {
  logger("debug", "refresh() - state: ${state.inspect()}")
  def app = parent?.getParent()
  app.checkState()
}

def off() {
  logger("debug", "off()")
  setThermostatMode("off")
}

def on() {
  logger("debug", "on()")
  setThermostatMode("auto")
}

def cool() {
  logger("debug", "cool()")
  setThermostatMode("cool")
}

def auto() {
  logger("debug", "auto()")
  setThermostatMode("auto")
}

def heat() {
  logger("debug", "heat()")
  setThermostatMode("heat")
}

def emergencyHeat() {
  logger("debug", "emergencyHeat()")
  setThermostatMode("emergency heat")
}

def setThermostatMode(mode) {
  logger("debug", "setThermostatMode(${mode?.inspect()})")
  switch (mode) {
    case 'cool':
    break
    case 'off':
      parent?.setLocationModes("off")
    break
    case 'auto':
      setProgramme("prog")
    break
    case 'heat':
      setProgramme("fixed")
    break
    case 'emergency heat':
      setOverride(20,60)
    break
  }
}

def setHeatingSetpoint(Double degrees) {
  // heatingSetpoint
  logger("debug", "setHeatingSetpoint() - degrees: ${degrees}")

  if (logDescText) {
    log.info "${device.displayName} Set Heating Setpoint: ${degrees}"
  } else {
    logger("debug", "setHeatingSetpoint(${degrees}) - Set Heating Setpoint: ${degrees}")
  }
  setProgramme("fixed", degrees)
}

// Modes: [prog, fixed]
void setProgramme(String mode, Double degrees=null) {
  logger("debug", "setProgramme(mode=${mode}, degrees=${degrees})")

  String roomId = device.currentValue("id")
  String currentThermostatMode = device.currentValue('thermostatMode')
  String temperatureFormat = parent.currentValue("temperatureFormat")

  Map query = [request:[method: "setProgramme",
                        roomId: roomId,
                        roomMode: mode
                       ]
              ]

  if (degrees) {
    // Check limits
    if (degrees < 5) { degrees = 5 }
    if (degrees > 32) { degrees = 32 }
    query['request']['fixed'] = [fixedTemp: "${(degrees * 10) as Integer}"]
  }

  def app = parent?.getParent()
  app.apiPost(query) { resp ->
    logger("trace", "setProgramme(mode=${mode}, degrees=${degrees}) - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")
    if (resp && resp.getStatus() == 200 && resp?.getData()?.status?.result == "success") {
      if (logDescText) {
        log.info "${device.displayName} Set Room (${roomId}) Mode: ${mode} ${degrees ? '/ Temperature: '+ degrees +'' : ''}"
      } else {
        logger("debug", "setProgramme(mode=${mode}, degrees=${degrees}) - Set Room (${roomId}) Mode: ${mode} ${degrees ? '/ Temperature: '+ degrees +'' : ''}")
      }
    } else {
      logger("warn", "setProgramme(mode=${mode}, degrees=${degrees}) - Setting Room (${roomId}) Mode: ${mode} Failed")
    }
  }
}

void setOverride(BigDecimal degrees=null, BigDecimal until=0) {
  logger("debug", "setOverride(degrees=${degrees}, until=${until})")

  String roomId = device.currentValue("id")
  String currentThermostatMode = device.currentValue('thermostatMode')
  String temperatureFormat = parent.currentValue("temperatureFormat")

  // Check limits and Set end time
  if (degrees < 5) { degrees = 5 }
  if (degrees > 32) { degrees = 32 }

  if (until < 10) { until = 10 }
  if (until > 240) { until = 240 }
  String untilDateString = null
  use ( groovy.time.TimeCategory ) {
    def untilDate = new Date() + until?.toInteger().minutes
    untilDateString = untilDate.format("HH:mm")
  }

  Map query = [request:[method: "setOverride",
                        rooms: [roomId],
                        temp: "${(degrees * 10) as Integer}",
                        until: untilDateString,
                        type: 3
                       ]
              ]

  def app = parent?.getParent()
  app.apiPost(query) { resp ->
    logger("trace", "setOverride(degrees=${degrees}, until=${until}) - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")
    if (resp && resp.getStatus() == 200 && resp?.getData()?.status?.result == "success") {
      if (logDescText) {
        log.info "${device.displayName} Set Room (${roomId}) Mode: Override / Temperature: ${degrees} / Until: ${untilDateString}"
      } else {
        logger("debug", "setOverride(degrees=${degrees}, until=${until}) - Set Room (${roomId}) Mode: Override / Temperature: ${degrees} / Until: ${untilDateString}")
      }
    } else {
      logger("warn", "setOverride(degrees=${degrees}, until=${until}) - Setting Room (${roomId}) Mode: Override Failed")
    }
  }
}

def setDetails(Map detail) {
  logger("debug", "setDetails(${detail?.inspect()})")
  sendEvent(name: "id", value: detail.id)
  sendEvent(name: "name", value: detail.name)
  sendEvent(name: "locationName", value: detail.locName)

  detail?.each { k, v ->
    if (k =~ /^(parameters|id|name)$/) { return }
    updateDataValue("manufacturer", "Warmup")
    if (k =~ /^(deviceModuleNumber)$/) {
      // updateDataValue("model", v)
    }
    state.deviceInfo[k] = v
  }
}

def setStates(Map states) {
  logger("debug", "setStates(${states?.inspect()})")
  String temperatureFormat = parent.currentValue("temperatureFormat")

  if ((states?.targetTemp as BigDecimal) > (states?.currentTemp as BigDecimal)) {
    sendEvent(name: 'thermostatOperatingState', value: "heating", displayed: true)
  } else {
    sendEvent(name: 'thermostatOperatingState', value: "idle", displayed: true)
  }

  states?.each { k, v ->
    if (k =~ /^(schedule|roomId|roomName|locId)$/) { return }
    String cv = device.currentValue(k)
    boolean isStateChange = (cv?.toString() != v?.toString() ? true : false)
    if (isStateChange) {
      if (logDescText) {
        log.info "${device.displayName} Value change: ${k} = ${cv} != ${v}"
      } else {
        logger("debug", "setStates() - Value change: ${k} = ${cv} != ${v}")
      }
    }

    switch (k) {
      case 'runMode':
        sendEvent(name: "${k}", value: "${v}", displayed: true, isStateChange: isStateChange)

        String mode = typeRunMode.find { it.key == v }?.value
        cv = device.currentValue("thermostatMode")
        isStateChange = (cv?.toString() != mode ? true : false)
        if (isStateChange) {
          if (logDescText) {
            log.info "${device.displayName} Value change: thermostatMode = ${cv} != ${mode}"
          } else {
            logger("debug", "setStates() - Value change: thermostatMode = ${cv} != ${mode}")
          }
        }
        sendEvent(name: "thermostatMode", value: "${mode}", displayed: true, isStateChange: isStateChange)
      break
      case 'currentTemp':
        sendEvent(name: "${k}", value: "${v}", displayed: true, isStateChange: isStateChange, unit: "${temperatureFormat}",)
        sendEvent(name: 'temperature', value: "${v}", displayed: true, isStateChange: isStateChange, unit: "${temperatureFormat}", state: "heat")
      break
      case 'targetTemp':
        sendEvent(name: "${k}", value: "${v}", displayed: true, isStateChange: isStateChange, unit: "${temperatureFormat}",)
        sendEvent(name: 'heatingSetpoint', value: "${v}", displayed: true, isStateChange: isStateChange, unit: "${temperatureFormat}", state: "heat")
        sendEvent(name: 'thermostatSetpoint', value: "${v}", displayed: true, isStateChange: isStateChange, unit: "${temperatureFormat}", state: "heat")
      break
      case ~/.+Temp/:
        sendEvent(name: "${k}", value: "${v}", displayed: true, isStateChange: isStateChange, unit: "${temperatureFormat}")
      break
      default:
        sendEvent(name: "${k}", value: "${v}", displayed: true, isStateChange: isStateChange)
      break
    }

    if (k == "hasPolled" && v == "false") {
      logger("warn", "Device is not reachable")
    }
  }
}

def clearState() {
  logger("debug", "ClearStates() - Clearing device states")

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
