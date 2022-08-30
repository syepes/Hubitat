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

@Field static Map<Integer,String> typeGeoMode = [
  0: "off",
  1: "on and visible to others",
  2: "on and invisible",
]
@Field static Map<Integer,String> typeCurrency = [
  0: "£",
  1: "€",
  2: "\$",
  3: "¥",
  4: "Pln",
  5: "Kr",
  6: "Kn"
]
@Field static Map<Boolean,String> typeTempFormat = [
  false: "°C",
  true: "°F"
]

metadata {
  definition (name: "Warmup - Cloud - Location", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Warmup/Warmup%20-%20Cloud%20-%20Location.groovy") {
    capability "Actuator"
    capability "Switch"
    capability "Refresh"

    command "setLocationModes", [[name:"mode", type: "ENUM", description: "Mode", constraints: ["off", "frost"]]]

    attribute "id", "string"
    attribute "name", "string"
    attribute "locMode", "string"
    attribute "smartGeo", "string"
    attribute "smartGeoMode", "string"
    attribute "currency", "string"
    attribute "temperatureFormat", "string"
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
  setLocationModes("off")
}

def on() {
  logger("debug", "on()")
  setLocationModes("frost")
}

// Modes: [off, frost]
void setLocationModes(String mode) {
  logger("debug", "setLocationModes(mode=${mode})")

  String locId = device.currentValue("id")
  String locName = device.currentValue('name')

  Map query = [request:[method: "setModes",
                        values: [holEnd: "-",
                                 fixedTemp: "",
                                 holStart: "-",
                                 geoMode: "0",
                                 holTemp: "-",
                                 locId: locId,
                                 locMode: mode
                        ]
                       ]
              ]

  def app = parent
  app.apiPost(query) { resp ->
    logger("trace", "setLocationModes(mode=${mode}) - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")
    if (resp && resp.getStatus() == 200 && resp?.getData()?.status?.result == "success") {
      if (logDescText) {
        log.info "${device.displayName} Set Location (${locName}/${locId}) Mode: ${mode}"
      } else {
        logger("debug", "setLocationModes(mode=${mode}) - Set Location (${locName}/${locId}) Mode: ${mode}")
      }
    } else {
      logger("warn", "setLocationModes(mode=${mode}) - Setting Location (${locName}/${locId}) Mode: ${mode} Failed")
    }
  }
}

def setDetails(Map detail) {
  logger("debug", "setDetails(${detail?.inspect()})")
  sendEvent(name: "id", value: detail.id)
  sendEvent(name: "name", value: detail.name)

  state.deviceInfo['locId'] = detail.id
  state.deviceInfo['locName'] = detail.name

  detail?.each { k, v ->
    if (k =~ /^(fenceArray|id|now|name)$/) { return }
    state.deviceInfo[k] = v

    if (k == "currency") {
      String cur = typeCurrency.find { it.key == v }?.value
      sendEvent(name: "${k}", value: "${cur}", displayed: true)
    }
    if (k == "tempFormat") {
      String temp = typeTempFormat.find { it.key?.toString() == v?.toString() }?.value
      sendEvent(name: "temperatureFormat", value: "${temp}", displayed: true)
    }
    if (k =~ /^(smartGeo|locMode)$/) {
      sendEvent(name: "${k}", value: "${v}", displayed: true)
    }
    if (k == "geoMode") {
      String mode = typeGeoMode.find { it.key == v }?.value
      sendEvent(name: "smartGeoMode", value: "${mode}", displayed: true)
    }
  }
}

ChildDeviceWrapper addRoom(Map detail) {
  logger("info", "Creating Device: ${detail.roomName} (${detail.locName})")
  logger("debug", "addRoom(${detail?.inspect()})")

  try {
    ChildDeviceWrapper cd = getChildDevice("${device.deviceNetworkId}-${detail?.id}")
    if(!cd) {
      logger("debug", "addRoom() - Creating Room (${detail.inspect()}")
      ChildDeviceWrapper cdm = addChildDevice("syepes", "Warmup - Cloud - Room", "${device.deviceNetworkId}-${detail?.id}", [name: "${detail?.name}", label: "${detail?.name}", isComponent: true])
      cdm.setDetails(detail)
      return cdm
    } else {
      logger("debug", "addRoom() - Room: ${cd.name} (${detail.locName}) (${device.deviceNetworkId}-${detail?.id}) already exists")
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
