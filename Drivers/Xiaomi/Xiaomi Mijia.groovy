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

import groovy.json.JsonSlurper
import groovy.transform.Field

@Field String VERSION = "1.0.2"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[2]

metadata {
  definition (name: "Xiaomi Mijia", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Xiaomi/Xiaomi%20Mijia.groovy") {
    capability "Actuator"
    capability "Refresh"
    capability "Initialize"
    capability "Configuration"

    command "checkState"
    command "cleanChild"
    attribute "status", "string"
  }

  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "stateCheckInterval", title: "State Check", description: "Device last seen check interval", type: "enum", options:[[0:"Disabled"], [5:"5min"], [10:"10min"], [15:"15min"], [20:"20min"], [30:"30min"], [60:"1h"], [120:"2h"]], defaultValue: 20, required: true
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

def initialize() {
  logger("debug", "initialize()")
}

def updated() {
  logger("debug", "updated()")

  if (!state.driverInfo?.ver || state.driverInfo.isEmpty() || state.driverInfo.ver != VERSION) {
    installed()
  }

  if (!state.deviceInfo) {
    refresh()
  }

  unschedule()
  configure()
}

def refresh() {
  logger("debug", "refresh() - state: ${state.inspect()}")
}

def configure() {
  logger("debug", "configure()")

  if (stateCheckInterval.toInteger()) {
    schedule("0 */5 * ? * *", checkState)
  }
}

def cleanChild() {
  logger("debug", "cleanChild() - childDevices: ${childDevices?.size()}")
  childDevices?.each{ deleteChildDevice(it.deviceNetworkId) }
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
}

def parse(String description) {
  logger("trace", "parse() - description: ${description?.inspect()}")
  def result = []

  def descMap = parseDescriptionAsMap(description)
  if (!descMap?.isEmpty()) {
    def body = parseJson(descMap?.body)
    if(body) {
      def cd = findOrCreateChild(body.type,body.mac, body.label, body.location)
      if (cd) {
        cd.parse(body)
      }
    }
  }

  logger("debug", "parse() - descMap: ${descMap?.inspect()} with result: ${result?.inspect()}")
  result
}

// Finds / Creates the child device
private def findOrCreateChild(String type, String id, String label, String location) {
  logger("debug", "findOrCreateChild(${type},${id},${label},${location})")
  try {
    String thisId = device.id
    def cd = getChildDevice("${thisId}-${id}")
    if (!cd) {
      switch (type) {
        case "Sensor Temperature Humidity":
          cd = addChildDevice("Xiaomi Mijia - ${type} Child Device", "${thisId}-${id}", [name: "${type} - ${location}", label: "${label}", isComponent: true])
          cd.sendEvent([name: "status", value: "online", displayed: true])
        break
        default :
          logger("error", "findOrCreateChild(${type},${id},${label},${location}) - Device type not found")
        break
      }
    }
    return cd
  } catch (e) {
    logger("error", "findOrCreateChild(${type},${id},${label},${location}) - e: ${e}")
  }
}

private parseDescriptionAsMap(description) {
  logger("trace", "parseDescriptionAsMap() - description: ${description.inspect()}")
  try {
    def descMap = description.split(",").inject([:]) { map, param ->
      def nameAndValue = param.split(":")
      if (nameAndValue.length == 2){
        map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
      } else {
        map += [(nameAndValue[0].trim()):""]
      }
    }

    def headers = new String(descMap["headers"]?.decodeBase64())
    def status_code = headers?.tokenize('\r\n')[0]
    headers = headers?.tokenize('\r\n')?.toList()[1..-1]?.collectEntries{
      it.split(":",2).with{ [ (it[0]): (it.size()<2) ? null : it[1] ?: null ] }
    }

    def body = new String(descMap["body"]?.decodeBase64())
    def body_json
    logger("trace", "parseDescriptionAsMap() - headers: ${headers.inspect()}, body: ${body.inspect()}")

    if (body && body != "") {
      if(body.startsWith("\"{") || body.startsWith("{") || body.startsWith("\"[") || body.startsWith("[")) {
        JsonSlurper slurper = new JsonSlurper()
        body_json = slurper.parseText(body)
        logger("trace", "parseDescriptionAsMap() - body_json: ${body_json}")
      }
    }

    return [desc: descMap.subMap(['mac','ip','port']), status_code: status_code, headers:headers, body:body_json]
  } catch (e) {
    logger("error", "parseDescriptionAsMap() - ${e.inspect()}")
    return [:]
  }
}

def checkState() {
  logger("debug", "checkState()")
  def now = new Date()
  def cd_devices = getChildDevices()

  cd_devices.each { child ->
    def lastActivity = child.getLastActivity()
    def prev = Date.parse("yyyy-MM-dd HH:mm:ss","${lastActivity}".replace("+00:00","+0000"))
    long unxNow = (now.getTime()/1000) as long
    long unxPrev = (prev.getTime()/1000) as long
    long lastSeen = Math.abs((unxNow-unxPrev)/60)
    logger("debug", "checkState() - ${child} - lastActivity: ${lastActivity}, lastSeen: ${lastSeen}min")

    if (lastSeen > 0) {
      if (lastSeen > stateCheckInterval?.toInteger()) {
        logger("warn", "${child} - Is offline (lastSeen: ${lastSeen}min)")
        child.sendEvent([name: "status", value: "offline", displayed: true])
      } else {
        child.sendEvent([name: "status", value: "online", displayed: true])
      }
    }
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
