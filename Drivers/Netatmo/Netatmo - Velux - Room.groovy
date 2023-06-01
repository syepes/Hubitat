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
  definition (name: "Netatmo - Velux - Room", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Netatmo/Netatmo%20-%20Velux%20-%20Room.groovy") {
    capability "Actuator"
    capability "Refresh"
    capability "Sensor"
    capability "Illuminance Measurement"
    capability "Temperature Measurement"
    capability "Relative Humidity Measurement"
    capability "Carbon Dioxide Measurement"
    capability "AirQuality"

    command "open", [[name:"velux_type", type: "ENUM", description: "mode", constraints: ["all","window","shutter","blind"]]]
    command "close", [[name:"velux_type", type: "ENUM", description: "mode", constraints: ["all","window","shutter","blind"]]]
    command "stop", [[name:"velux_type", type: "ENUM", description: "mode", constraints: ["all","window","shutter","blind"]]]
    command "setPosition", [[name:"velux_type", type: "ENUM", description: "mode", constraints: ["all","window","shutter","blind"]], [name:"position", type: "NUMBER", description: ""]]

    attribute "id", "string"
    attribute "velux_type", "string"
    attribute "homeName", "string"
    attribute "lux", "number"
    attribute "co2", "number"
    attribute "air_quality", "number"
    attribute "airQualityIndex-Level", "enum", ["Healthy","Fine","Fair","Poor","Unhealthy"]
    attribute "algo_status", "number"
    attribute "auto_close_ts", "number"

    attribute "max_comfort_co2", "number"
    attribute "max_comfort_humidity", "number"
    attribute "max_comfort_temperature", "number"
    attribute "min_comfort_humidity", "number"
    attribute "min_comfort_temperature", "number"
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
  def home = parent?.getParent()
  home.checkState(state.deviceInfo.homeID)
}

def parse(String description) {
  logger("trace", "parse() - description: ${description?.inspect()}")
  return []
}

def close(String velux_type="all") {
  getChildDevices()?.each {
    String type = it?.currentValue("velux_type")
    if (velux_type == "all" && type =~ /shutter|window|blind/) {
      it.close()
    } else if (velux_type == type) {
      it.close()
    }
  }
}

def open(String velux_type="all") {
  getChildDevices()?.each {
    String type = it?.currentValue("velux_type")
    if (velux_type == "all" && type =~ /shutter|window|blind/) {
      it.open()
    } else if (velux_type == type) {
      it.open()
    }
  }
}

def stop(String velux_type="all") {
  getChildDevices()?.each {
    String type = it?.currentValue("velux_type")
    if (velux_type == "all" && type =~ /shutter|window|blind/) {
      it.stop()
    } else if (velux_type == type) {
      it.stop()
    }
  }
}

def setPosition(String velux_type="all", BigDecimal position) {
  getChildDevices()?.each {
    String type = it?.currentValue("velux_type")
    if (velux_type == "all" && type =~ /shutter|window|blind/) {
      it.setPosition(position)
    } else if (velux_type == type) {
      it.setPosition(position)
    }
  }
}

def setDetails(Map detail) {
  logger("debug", "setDetails(${detail?.inspect()})")
  state.deviceInfo['velux_type'] = "room"
  state.deviceInfo['homeID'] = detail.homeID
  state.deviceInfo['roomID'] = detail.id
  sendEvent(name: "velux_type", value: "room")
  sendEvent(name: "homeName", value: detail.homeName)
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

    if (k == "lux") {
      sendEvent(name: "illuminance", value: v, displayed: true, isStateChange: isStateChange)
    }
    if (k ==~ /.*temperature/) {
      if (k == "temperature") {
        sendEvent(name: "temperature", value: (Float.parseFloat("${v}") / 10), displayed: true, isStateChange: isStateChange)
      } else {
        sendEvent(name: "${k}", value: (Float.parseFloat("${v}") / 10), displayed: true, isStateChange: isStateChange)
      }
      return
    }
    if (k == "co2") {
      sendEvent(name: "carbonDioxide", value: v, displayed: true, isStateChange: isStateChange)
    }
    if (k == "air_quality") {
      String air_quality_level = "Unknown"
      switch (v) {
        case "0":
          air_quality_level = "Healthy"
        break
        case "1":
          air_quality_level = "Fine"
        break
        case "2":
          air_quality_level = "Fair"
        break
        case "3":
          air_quality_level = "Poor"
        break
        case "4":
          air_quality_level = "Unhealthy"
        break
      }
      sendEvent(name: "airQualityIndex", value: v, displayed: true, isStateChange: isStateChange)
      sendEvent(name: "airQualityIndex-Level", value: air_quality_level, displayed: true, isStateChange: isStateChange)
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
      cd.setDetails(detail)
      return cd
    }
  } catch (e) {
    logger("error", "addModule() - Module creation Exception: ${e.inspect()}")
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
