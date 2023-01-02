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

@Field String VERSION = "1.0.4"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

@Field static Map<Integer,String> typeOperate = [
  0: "off",
  1: "on"
]
@Field static Map<Integer,String> typeOperationMode = [
  0: "Auto",
  1: "Dry",
  2: "Cool",
  3: "Heat",
  4: "Fan"
]
@Field static Map<Integer,String> typeFanSpeed = [
  0: "Auto",
  1: "Low",
  2: "LowMid",
  3: "Mid",
  4: "HighMid",
  5: "High"
]
@Field static Map<Integer,String> typeAirSwingAutoMode = [
  0: "Both",
  1: "Disabled",
  2: "AirSwingUD",
  3: "AirSwingLR"
]
@Field static Map<Integer,String> typeAirSwingUD = [
  (-1): "Auto",
  0: "Up",
  3: "UpMid",
  2: "Mid",
  4: "DownMid",
  1: "Down"
]
@Field static Map<Integer,String> typeAirSwingLR = [
  (-1): "Auto",
  1: "Left",
  5: "LeftMid",
  2: "Mid",
  4: "RightMid",
  0: "Right"
]
@Field static Map<Integer,String> typeEcoMode = [
  0: "Auto",
  1: "Powerful",
  2: "Quiet"
]
@Field static Map<Integer,String> typeNanoeMode = [
  0: "Unavailable",
  1: "off",
  2: "on",
  3: "ModeG",
  4: "All"
]
@Field static Map<Integer,String> typeAirQuality = [
  0: "Unavailable",
  1: "Clean",
  3: "Dirty"
]

metadata {
  definition (name: "Panasonic - Comfort Cloud - AC", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Netatmo/Netatmo%20-%20Velux%20-%20Window.groovy") {
    capability "Actuator"
    capability "Refresh"
    capability "Switch"
    capability "TemperatureMeasurement"

    command "clearState"
    command "setTemperature", [[name:"degrees",type:"NUMBER",description:"Temperature"]]
    command "setOperationMode", [[name:"operationMode",type:"ENUM", description:"Operation Mode", constraints: typeOperationMode.collect { it.value }]]
    command "setEcoMode", [[name:"ecoMode",type:"ENUM", description:"Eco Mode", constraints: typeEcoMode.collect { it.value }]]
    command "setFanSpeed", [[name:"fanSpeed",type:"ENUM", description:"Fan Speed", constraints: typeFanSpeed.collect { it.value }]]
    command "setAirSwingAutoMode", [[name:"fanAutoMode",type:"ENUM", description:"Fan Auto Mode", constraints: typeAirSwingAutoMode.collect { it.value }]]
    command "setAirSwingUD", [[name:"airSwingUD",type:"ENUM", description:"Fan Swing UD", constraints: typeAirSwingUD.collect { it.value }]]
    command "setAirSwingLR", [[name:"airSwingLR",type:"ENUM", description:"Fan Swing LR", constraints: typeAirSwingLR.collect { it.value }]]
    command "setNanoeMode", [[name:"nanoe",type:"ENUM", description:"Nanoe Mode", constraints: typeNanoeMode.collect { it.value }]]

    attribute "id", "string"
    attribute "type", "string"
    attribute "name", "string"
    attribute "moduleNumber", "string"
    attribute "groupName", "string"

    // Group / Status (parameters)
    attribute "operate", "string"
    attribute "operationMode", "string"
    attribute "temperatureSet", "number"
    attribute "insideTemperature", "number"
    attribute "outTemperature", "number"
    attribute "airQuality", "number"
    attribute "fanSpeed", "string"
    attribute "fanAutoMode", "string"
    attribute "airSwingLR", "string"
    attribute "airSwingUD", "string"
    attribute "ecoMode", "string"
    attribute "ecoNavi", "string"
    attribute "nanoe", "string"
    attribute "iAuto", "string"
    attribute "nanoeDevice", "string"
    attribute "actualNanoe", "string"
    attribute "airDirection", "string"
    attribute "ecoFunctionData", "string"
    attribute "lastSettingMode", "string"
    attribute "errorStatus", "string"
  }
  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: true, required: false
      input name: "tempChange", title: "Temperature Change", description: "Only log changes with differences of", type: "enum", options:[[0:"Any change"], [0.5:"0.5°"], [1:"1°"], [1.5:"1.5°"], [2:"2°"], [2.5:"2.5°"], [3:"3°"]], defaultValue: 1.5, required: true
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
  app.checkState(state.deviceInfo.deviceGuid)
}

def off() {
  logger("debug", "off()")
  def app = parent?.getParent()
  app.deviceControl(["deviceGuid": state.deviceInfo.deviceGuid, "parameters":["operate": 0]])
}

def on() {
  logger("debug", "on()")
  def app = parent?.getParent()
  app.deviceControl(["deviceGuid": state.deviceInfo.deviceGuid, "parameters":["operate": 1]])
}

def setDetails(Map detail) {
  logger("debug", "setDetails(${detail?.inspect()})")
  sendEvent(name: "id", value: detail.id)
  sendEvent(name: "name", value: detail.name)
  sendEvent(name: "groupName", value: detail.groupName)
  detail?.each { k, v ->
    if (k =~ /^(parameters|id)$/) { return }
    if (k =~ /^(deviceModuleNumber)$/) {
      updateDataValue("model", v)
      updateDataValue("manufacturer", "Panasonic")
    }
    state.deviceInfo[k] = v
  }
}

def setStates(Map states) {
  logger("debug", "setStates(${states?.inspect()})")
  states?.each { k, v ->
    if (k == "parameters") {
      v?.each { pk, pv ->
        String cv = device.currentValue(pk)
        switch (pk) {
          case 'operate':
            String mode = typeOperate.find { it.key == pv }.value
            boolean isStateChange = (cv?.toString() != mode) ? true : false
            if (isStateChange) {
              if (logDescText) {
                log.info "${device.displayName} Value change: ${pk} = ${cv} != ${mode}"
              } else {
                logger("debug", "setStates() - Value change: ${pk} = ${cv} != ${mode}")
              }
            }

            parent?.sendEvent(name: "switch", value: "${mode}", displayed: true, descriptionText: "${device.displayName} (${state.deviceInfo['deviceGuid']})")
            sendEvent(name: "switch", value: "${mode}", displayed: true, isStateChange: isStateChange)
            sendEvent(name: "${pk}", value: "${mode}", displayed: true, isStateChange: isStateChange)
          break
          case 'operationMode':
            String mode = typeOperationMode.find { it.key == pv }.value
            boolean isStateChange = (cv?.toString() != mode) ? true : false
            if (isStateChange) {
              if (logDescText) {
                log.info "${device.displayName} Value change: ${pk} = ${cv} != ${mode}"
              } else {
                logger("debug", "setStates() - Value change: ${pk} = ${cv} != ${mode}")
              }
            }
            sendEvent(name: "${pk}", value: "${mode}", displayed: true, isStateChange: isStateChange)
          break
          case 'fanSpeed':
            String mode = typeFanSpeed.find { it.key == pv }.value
            boolean isStateChange = (cv?.toString() != mode) ? true : false
            if (isStateChange) {
              if (logDescText) {
                log.info "${device.displayName} Value change: ${pk} = ${cv} != ${mode}"
              } else {
                logger("debug", "setStates() - Value change: ${pk} = ${cv} != ${mode}")
              }
            }
            sendEvent(name: "${pk}", value: "${mode}", displayed: true, isStateChange: isStateChange)
          break
          case 'fanAutoMode':
            String mode = typeAirSwingAutoMode.find { it.key == pv }.value
            boolean isStateChange = (cv?.toString() != mode) ? true : false
            if (isStateChange) {
              if (logDescText) {
                log.info "${device.displayName} Value change: ${pk} = ${cv} != ${mode}"
              } else {
                logger("debug", "setStates() - Value change: ${pk} = ${cv} != ${mode}")
              }
            }
            sendEvent(name: "${pk}", value: "${mode}", displayed: true, isStateChange: isStateChange)
          break
          case 'airSwingUD':
            String mode = typeAirSwingUD.find { it.key == pv }.value
            boolean isStateChange = (cv?.toString() != mode) ? true : false
            if (isStateChange) {
              if (logDescText) {
                log.info "${device.displayName} Value change: ${pk} = ${cv} != ${mode}"
              } else {
                logger("debug", "setStates() - Value change: ${pk} = ${cv} != ${mode}")
              }
            }
            sendEvent(name: "${pk}", value: "${mode}", displayed: true, isStateChange: isStateChange)
          break
          case 'airSwingLR':
            String mode = typeAirSwingLR.find { it.key == pv }.value
            boolean isStateChange = (cv?.toString() != mode) ? true : false
            if (isStateChange) {
              if (logDescText) {
                log.info "${device.displayName} Value change: ${pk} = ${cv} != ${mode}"
              } else {
                logger("debug", "setStates() - Value change: ${pk} = ${cv} != ${mode}")
              }
            }
            sendEvent(name: "${pk}", value: "${mode}", displayed: true, isStateChange: isStateChange)
          break
          case 'ecoMode':
            String mode = typeEcoMode.find { it.key == pv }.value
            boolean isStateChange = (cv?.toString() != mode) ? true : false
            if (isStateChange) {
              if (logDescText) {
                log.info "${device.displayName} Value change: ${pk} = ${cv} != ${mode}"
              } else {
                logger("debug", "setStates() - Value change: ${pk} = ${cv} != ${mode}")
              }
            }
            sendEvent(name: "${pk}", value: "${mode}", displayed: true, isStateChange: isStateChange)
          break
          case 'nanoe':
            String mode = typeNanoeMode.find { it.key == pv }.value
            boolean isStateChange = (cv?.toString() != mode) ? true : false
            if (isStateChange) {
              if (logDescText) {
                log.info "${device.displayName} Value change: ${pk} = ${cv} != ${mode}"
              } else {
                logger("debug", "setStates() - Value change: ${pk} = ${cv} != ${mode}")
              }
            }
            sendEvent(name: "${pk}", value: "${mode}", displayed: true, isStateChange: isStateChange)
          break
          case 'actualNanoe':
            String mode = typeNanoeMode.find { it.key == pv }.value
            boolean isStateChange = (cv?.toString() != mode) ? true : false
            if (isStateChange) {
              if (logDescText) {
                log.info "${device.displayName} Value change: ${pk} = ${cv} != ${mode}"
              } else {
                logger("debug", "setStates() - Value change: ${pk} = ${cv} != ${mode}")
              }
            }
            sendEvent(name: "${pk}", value: "${mode}", displayed: true, isStateChange: isStateChange)
          break
          case ['insideTemperature', 'outTemperature']:
            String unit = (state?.deviceInfo?.temperatureUnit?.toInteger() == 0) ? "°C" : "°F"
            boolean isStateChange = (cv?.toString() != pv?.toString()) ? true : false
            def vDiff = Math.abs(cv?.toFloat() - pv?.toFloat())

            if (isStateChange && vDiff >= tempChange?.toFloat()) {
              if (logDescText) {
                if (pk == "insideTemperature") {
                  log.info "${device.displayName} Temperature (Inside) is ${pv} ${unit}"
                } else {
                  log.info "${device.displayName} Temperature (Outside) is ${pv} ${unit}"
                }
              } else {
                logger("debug", "setStates() - Value change: ${pk} = ${cv} ${unit} != ${pv} ${unit}")
              }
            }

            sendEvent(name: "${pk}", value: "${pv}", displayed: true, isStateChange: isStateChange, unit: unit)
            if (pk =~ /^(insideTemperature)$/) {
              sendEvent(name: "temperature", value: "${pv}", displayed: true, isStateChange: isStateChange, unit: unit)
            }
          break
          case ~/(?i).*temp.*/:
            String unit = (state?.deviceInfo?.temperatureUnit?.toInteger() == 0) ? "°C" : "°F"
            boolean isStateChange = (cv?.toString() != pv?.toString()) ? true : false
            if (isStateChange) {
              if (logDescText) {
                log.info "${device.displayName} Value change: ${pk} = ${cv} ${unit} != ${pv} ${unit}"
              } else {
                logger("debug", "setStates() - Value change: ${pk} = ${cv} ${unit} != ${pv} ${unit}")
              }
            }
            sendEvent(name: "${pk}", value: "${pv}", displayed: true, isStateChange: isStateChange, unit: unit)
          default:
            boolean isStateChange = (cv?.toString() != pv?.toString()) ? true : false
            if (isStateChange) {
              if (logDescText) {
                log.info "${device.displayName} Value change: ${pk} = ${cv} != ${pv}"
              } else {
                logger("debug", "setStates() - Value change: ${pk} = ${cv} != ${pv}")
              }
            }
            sendEvent(name: "${pk}", value: "${pv}", displayed: true, isStateChange: isStateChange)
          break
        }
      }
    } else {
        state.deviceInfo[k] = v
    }

    if (k == "reachable" && v == "false") {
      logger("warn", "Device is not reachable")
    }
  }
}

def setTemperature(Double degrees) {
  logger("debug", "setTemperature(${degrees?.inspect()})")

  if (degrees == null) {
    logger("error", "Temperature Set (${degrees}) is incorrect")
  } else {
    if (logDescText) {
      log.info "${device.displayName} Temperature Set (${degrees})"
    } else {
      logger("info", "Temperature Set (${degrees})")
    }
    def app = parent?.getParent()
    app.deviceControl(["deviceGuid": state.deviceInfo.deviceGuid, "parameters":["temperatureSet": degrees]])
  }
}

def setHeatingSetpoint(Double degrees) {
  // heatingSetpoint
  logger("debug", "setHeatingSetpoint() - degrees: ${degrees}")
}

def setCoolingSetpoint(Double degrees) {
  // coolingSetpoint
  // temperatureSet
  logger("debug", "setCoolingSetpoint() - degrees: ${degrees}")
}

def setOperationMode(mode="Auto") {
  logger("debug", "setOperationMode(${mode?.inspect()})")
  // Validate modes
  Integer mode_value = typeOperationMode.find { it.value == mode }.key

  if (mode_value == null) {
    logger("error", "Operation Mode (${mode}) is incorrect")
  } else {
    if (logDescText) {
      log.info "${device.displayName} Operation Mode (${mode}) value = ${mode_value}"
    } else {
      logger("info", "Operation Mode (${mode}) value = ${mode_value}")
    }
    def app = parent?.getParent()
    app.deviceControl(["deviceGuid": state.deviceInfo.deviceGuid, "parameters":["operationMode": mode_value]])
  }
}

def setEcoMode(mode="Auto") {
  logger("debug", "setEcoMode(${mode?.inspect()})")
  // Validate modes
  Integer mode_value = typeEcoMode.find { it.value == mode }.key

  if (mode_value == null) {
    logger("error", "Eco Mode (${mode}) is incorrect")
  } else {
    if (logDescText) {
      log.info "${device.displayName} Eco Mode (${mode}) value = ${mode_value}"
    } else {
      logger("info", "Eco Mode (${mode}) value = ${mode_value}")
    }
    def app = parent?.getParent()
    app.deviceControl(["deviceGuid": state.deviceInfo.deviceGuid, "parameters":["ecoMode": mode_value]])
  }
}

def setFanSpeed(mode="Auto") {
  logger("debug", "setFanSpeed(${mode?.inspect()})")
  // Validate modes
  Integer mode_value = typeFanSpeed.find { it.value == mode }.key

  if (mode_value == null) {
    logger("error", "FanSpeed (${mode}) is incorrect")
  } else {
    if (logDescText) {
      log.info "${device.displayName} FanSpeed (${mode}) value = ${mode_value}"
    } else {
      logger("info", "FanSpeed (${mode}) value = ${mode_value}")
    }
    def app = parent?.getParent()
    app.deviceControl(["deviceGuid": state.deviceInfo.deviceGuid, "parameters":["fanSpeed": mode_value]])
  }
}

def setAirSwingAutoMode(mode="Auto") {
  logger("debug", "setAirSwingAutoMode(${mode?.inspect()})")
  // Validate modes
  Integer mode_value = typeAirSwingAutoMode.find { it.value == mode }.key

  if (mode_value == null) {
    logger("error", "AirSwing Auto Mode (${mode}) is incorrect")
  } else {
    if (logDescText) {
      log.info "${device.displayName} AirSwing Auto Mode (${mode}) value = ${mode_value}"
    } else {
      logger("info", "AirSwing Auto Mode (${mode}) value = ${mode_value}")
    }
    def app = parent?.getParent()
    app.deviceControl(["deviceGuid": state.deviceInfo.deviceGuid, "parameters":["fanAutoMode": mode_value]])
  }
}

def setAirSwingUD(mode="Auto") {
  logger("debug", "setAirSwingUD(${mode?.inspect()})")
  // Validate modes
  Integer mode_value = typeAirSwingUD.find { it.value == mode }.key

  if (mode_value == null) {
    logger("error", "AirSwingUD (${mode}) is incorrect")
  } else {
    if (logDescText) {
      log.info "${device.displayName} AirSwingUD (${mode}) value = ${mode_value}"
    } else {
      logger("info", "AirSwingUD (${mode}) value = ${mode_value}")
    }
    def app = parent?.getParent()
    app.deviceControl(["deviceGuid": state.deviceInfo.deviceGuid, "parameters":["airSwingUD": mode_value]])
  }
}

def setAirSwingLR(mode="Auto") {
  logger("debug", "setAirSwingLR(${mode?.inspect()})")
  // Validate modes
  Integer mode_value = typeAirSwingLR.find { it.value == mode }.key

  if (mode_value == null) {
    logger("error", "AirSwingLR (${mode}) is incorrect")
  } else {
    if (logDescText) {
      log.info "${device.displayName} AirSwingLR (${mode}) value = ${mode_value}"
    } else {
      logger("info", "AirSwingLR (${mode}) value = ${mode_value}")
    }
    def app = parent?.getParent()
    app.deviceControl(["deviceGuid": state.deviceInfo.deviceGuid, "parameters":["airSwingLR": mode_value]])
  }
}

def setNanoeMode(mode="Unavailable") {
  logger("debug", "setNanoeMode(${mode?.inspect()})")
  // Validate modes
  Integer mode_value = typeNanoeMode.find { it.value == mode }.key

  if (mode_value == null) {
    logger("error", "Nanoe Mode (${mode}) is incorrect")
  } else {
    if (logDescText) {
      log.info "${device.displayName} Nanoe Mode (${mode}) value = ${mode_value}"
    } else {
      logger("info", "Nanoe Mode (${mode}) value = ${mode_value}")
    }
    def app = parent?.getParent()
    app.deviceControl(["deviceGuid": state.deviceInfo.deviceGuid, "parameters":["nanoe": mode_value]])
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
